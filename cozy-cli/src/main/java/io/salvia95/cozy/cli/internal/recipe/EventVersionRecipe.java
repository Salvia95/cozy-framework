package io.salvia95.cozy.cli.internal.recipe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;

import javax.lang.model.element.Modifier;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.JavaSources;
import io.salvia95.cozy.rules.Layer;
import io.salvia95.cozy.rules.Role;

/**
 * {@code cozy add event-version} (ADR-0033 §5.4). 이벤트에 필드를 더하고 schemaVersion을 올리고 새 버전 샘플을 남긴다. 스키마 진화 규칙은
 * docs/conventions/events.md다.
 *
 * <ul>
 * <li>필드는 record 끝에 박싱 타입으로 더한다. 옛 샘플을 읽으면 null이 된다.</li>
 * <li>지금의 {@code of()}({@code new Ev(UUID.randomUUID(), N, …)}를 돌려주는 것)는 새 필드를 null로 넘기는 위임으로 바꾸고, 새 필드를 받는
 * {@code of()}를 더한다. 그래서 발행하는 곳의 코드가 그대로 컴파일된다.</li>
 * <li>가장 높은 {@code v{n}.json}에 새 필드의 샘플 값을 더해 {@code v{n+1}.json}을 만든다.</li>
 * </ul>
 * 이 모양이 아니면 추측하지 않고 실패한다.
 */
final class EventVersionRecipe {

	private static final Pattern SAMPLE = Pattern.compile("v(\\d+)\\.json");

	private EventVersionRecipe() {
	}

	/** 이벤트 필드의 박싱 타입 */
	static String boxed(Field field) {
		return switch (field.kind()) {
			case TEXT, LONGTEXT -> "String";
			case INT -> "Integer";
			case LONG -> "Long";
			case BOOL -> "Boolean";
			case DATE -> "LocalDate";
			case INSTANT -> "Instant";
		};
	}

	/** 지금의 of(): 파라미터와 schemaVersion */
	private record Current(MethodTree method, int version, List<String> names) {
	}

	static String run(Context ctx, Args args) {
		args.only("event-version", 2, "field");
		String module = Names.module(args.positional(0, "모듈 이름"));
		String event = Names.pascal(args.positional(1, "이벤트 이름"), "이벤트");
		ctx.requireStarter("cozy-starter-events", "이벤트");
		ctx.module(module);
		List<Field> fields = Field.parseAll(args.all("field"));
		if (fields.isEmpty()) {
			throw new CliException("--field가 하나 이상 필요합니다 (예: --field pinned:bool).");
		}
		String pkg = ctx.rolePackage(module, Layer.SPI_EVENT);
		String file = ctx.roleFile(module, Role.EVENT, event);
		String source = ctx.plan().current(file);
		if (source == null) {
			throw new CliException(module + "에 이벤트 " + event + "가 없습니다(" + pkg + "." + event + "). 먼저 cozy add event " + module + " "
					+ event + "로 만드세요.");
		}
		JavaEdits.Parsed parsed = JavaEdits.parse(file, source);
		ClassTree type = parsed.type(event);
		if (type.getKind() != Tree.Kind.RECORD) {
			throw new CliException(file + "의 " + event + "가 record가 아닙니다. 이 이벤트는 손으로 고치세요.");
		}
		Map<String, String> components = new LinkedHashMap<>();
		VariableTree last = null;
		for (Tree member : type.getMembers()) {
			if (member instanceof VariableTree v && !v.getModifiers().getFlags().contains(Modifier.STATIC)) {
				components.put(v.getName().toString(), JavaEdits.normalize(v.getType().toString()));
				last = v;
			}
		}
		Current current = current(file, event, type, components);

		// 다시 실행: 모두 있으면 바뀐 것 없음, 일부만 있거나 타입이 다르면 실패 (ADR-0033 §3)
		List<String> present = new ArrayList<>();
		List<String> absent = new ArrayList<>();
		for (Field field : fields) {
			String existing = components.get(field.name());
			if (existing == null) {
				absent.add(field.name());
			}
			else if (!existing.equals(boxed(field))) {
				throw new CliException(event + "에 " + field.name() + "가 이미 " + existing + "로 있습니다(원하는 것: " + boxed(field)
						+ "). 필드의 타입은 바꾸지 않습니다. 다른 이름을 쓰세요.");
			}
			else {
				present.add(field.name());
			}
		}
		String dir = "src/test/resources/events/" + event;
		Map<Integer, String> samples = samples(ctx, dir);
		if (samples.isEmpty()) {
			throw new CliException(dir + "/v1.json이 없습니다. 버전별 샘플이 있어야 버전을 올립니다(docs/conventions/events.md).");
		}
		int highest = samples.keySet().stream().max(Integer::compare).orElseThrow();
		if (!present.isEmpty() && !absent.isEmpty()) {
			throw new CliException(event + "에 일부 필드만 있습니다. 있는 것: " + String.join(", ", present) + ". 없는 것: " + String.join(", ", absent)
					+ ". 있는 필드는 빼고 다시 실행하세요.");
		}
		if (absent.isEmpty()) {
			boolean inOf = current.names().containsAll(present);
			boolean inSample = sample(ctx, dir, highest).keySet().containsAll(present);
			if (!inOf || !inSample || current.version() != highest) {
				throw new CliException(event + "에 필드는 있지만 of()나 샘플 v" + highest + ".json이 맞지 않습니다. of()가 새 필드를 받고 "
						+ "schemaVersion이 가장 높은 샘플 번호와 같도록 손으로 맞추세요.");
			}
			ctx.plan().edit(file, source, "event-version " + String.join(", ", present));
			ctx.plan().edit(dir + "/v" + highest + ".json", ctx.plan().current(dir + "/v" + highest + ".json"), "샘플 v" + highest);
			return next(ctx, module, event, highest);
		}
		if (current.version() != highest) {
			throw new CliException(event + ".of()의 schemaVersion(" + current.version() + ")이 가장 높은 샘플 v" + highest
					+ ".json과 다릅니다. 둘을 먼저 맞추세요.");
		}
		int version = highest + 1;

		// 1. record 끝에 구성 요소
		String added = String.join("", fields.stream().map((f) -> ", " + boxed(f) + " " + f.name()).toList());
		int at = parsed.end(last);
		String next = source.substring(0, at) + added + source.substring(at);

		// 2. 지금의 of()를 위임으로 바꾸고 새 of()를 더한다
		JavaEdits.Parsed again = JavaEdits.parse(file, next);
		ClassTree type2 = again.type(event);
		Current current2 = current(file, event, type2, components);
		MethodTree of = current2.method();
		String params = String.join(", ", of.getParameters().stream().map((p) -> next.substring(again.start(p), again.end(p))).toList());
		List<String> oldNames = current2.names();
		List<String> nulls = fields.stream().map((f) -> "null").toList();
		List<String> delegate = new ArrayList<>(oldNames);
		delegate.addAll(nulls);
		List<String> all = new ArrayList<>(oldNames);
		fields.forEach((f) -> all.add(f.name()));
		String newParams = (params.isEmpty() ? "" : params + ", ")
				+ String.join(", ", fields.stream().map((f) -> boxed(f) + " " + f.name()).toList());
		String text = "public static " + event + " of(" + params + ") {\n\t\treturn of(" + String.join(", ", delegate) + ");\n\t}\n\n"
				+ "\t/** schemaVersion " + version + ": " + String.join(", ", fields.stream().map(Field::name).toList())
				+ " 추가 (cozy add event-version). 옛 of()는 새 필드를 null로 넘긴다. */\n" + "\tpublic static " + event + " of(" + newParams
				+ ") {\n\t\treturn new " + event + "(UUID.randomUUID(), " + version + (all.isEmpty() ? "" : ", " + String.join(", ", all))
				+ ");\n\t}";
		String edited = next.substring(0, again.start(of)) + text + next.substring(again.end(of));

		// 3. import
		Set<String> imports = new TreeSet<>();
		fields.stream().map(Field::importName).filter((i) -> i != null).forEach(imports::add);
		edited = JavaEdits.addImports(file, edited, imports);
		ctx.plan()
			.edit(file, edited, "+= " + String.join(", ", fields.stream().map((f) -> boxed(f) + " " + f.name()).toList())
					+ ", schemaVersion " + highest + " → " + version);

		// 4. 새 버전 샘플
		Map<String, Object> sample = sample(ctx, dir, highest);
		Map<String, String> json = new TreeMap<>();
		sample.forEach((k, v) -> json.put(k, json(v)));
		json.put("schemaVersion", String.valueOf(version));
		fields.forEach((f) -> json.put(f.name(), f.sample()));
		ctx.plan()
			.create(dir + "/v" + version + ".json", "{" + String.join(",", json.entrySet().stream().map((e) -> "\"" + e.getKey() + "\":"
					+ e.getValue()).toList()) + "}\n");
		return next(ctx, module, event, version);
	}

	/** {@code new Ev(UUID.randomUUID(), N, a, b)}를 돌려주는 static of() 하나 */
	private static Current current(String file, String event, ClassTree type, Map<String, String> components) {
		List<Current> found = new ArrayList<>();
		for (Tree member : type.getMembers()) {
			if (!(member instanceof MethodTree method) || !method.getName().contentEquals("of")
					|| !method.getModifiers().getFlags().contains(Modifier.STATIC) || method.getBody() == null
					|| method.getBody().getStatements().size() != 1
					|| !(method.getBody().getStatements().getFirst() instanceof ReturnTree ret)
					|| !(ret.getExpression() instanceof NewClassTree create)
					|| !create.getIdentifier().toString().equals(event)) {
				continue;
			}
			List<? extends ExpressionTree> arguments = create.getArguments();
			List<String> names = method.getParameters().stream().map((p) -> p.getName().toString()).toList();
			if (arguments.size() < 2 || !arguments.get(0).toString().replaceAll("\\s+", "").equals("UUID.randomUUID()")
					|| !(arguments.get(1) instanceof LiteralTree literal) || !(literal.getValue() instanceof Integer version)) {
				continue;
			}
			List<String> rest = arguments.subList(2, arguments.size()).stream().map(Object::toString).toList();
			if (!rest.equals(names)) {
				continue;
			}
			found.add(new Current(method, version, names));
		}
		if (found.size() != 1) {
			throw new CliException(file + "에서 return new " + event + "(UUID.randomUUID(), <버전>, <파라미터>…); 한 줄인 of()를 하나로 찾지 "
					+ "못했습니다(" + found.size() + "개). 알아볼 수 없는 of()는 고치지 않습니다. 필드 추가는 손으로 하고 cozy check로 확인하세요.");
		}
		Current current = found.getFirst();
		List<String> businessComponents = new ArrayList<>(components.keySet());
		businessComponents.removeAll(List.of("eventId", "schemaVersion"));
		if (!current.names().equals(businessComponents.subList(0, Math.min(businessComponents.size(), current.names().size())))
				|| current.names().size() > businessComponents.size()) {
			throw new CliException(file + "의 of() 파라미터(" + String.join(", ", current.names()) + ")가 record 구성 요소 순서와 다릅니다. "
					+ "알아볼 수 없는 of()는 고치지 않습니다.");
		}
		return current;
	}

	private static Map<Integer, String> samples(Context ctx, String dir) {
		Map<Integer, String> versions = new TreeMap<>();
		Path path = ctx.project().dir().resolve(dir);
		if (Files.isDirectory(path)) {
			try (Stream<Path> files = Files.list(path)) {
				files.map((f) -> f.getFileName().toString()).forEach((name) -> {
					Matcher m = SAMPLE.matcher(name);
					if (m.matches()) {
						versions.put(Integer.parseInt(m.group(1)), dir + "/" + name);
					}
				});
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}
		return versions;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> sample(Context ctx, String dir, int version) {
		String file = dir + "/v" + version + ".json";
		Object loaded;
		try {
			loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(ctx.plan().current(file));
		}
		catch (RuntimeException ex) {
			throw new CliException(file + "를 읽을 수 없습니다(JSON 오류). 샘플을 먼저 고치세요.");
		}
		if (!(loaded instanceof Map<?, ?> map)) {
			throw new CliException(file + "가 JSON 객체가 아닙니다.");
		}
		for (Object value : map.values()) {
			if (value instanceof Map || value instanceof List) {
				throw new CliException(file + "에 중첩된 값이 있습니다. 이 샘플은 손으로 이어 쓰세요(v" + (version + 1) + ".json).");
			}
		}
		Object schema = map.get("schemaVersion");
		if (!(schema instanceof Integer i) || i != version) {
			throw new CliException(file + "의 schemaVersion이 " + version + "가 아닙니다. 샘플을 먼저 고치세요.");
		}
		return (Map<String, Object>) map;
	}

	private static String json(Object value) {
		if (value == null) {
			return "null";
		}
		if (value instanceof String s) {
			return Field.quote(s).replace("\n", "\\n").replace("\t", "\\t");
		}
		if (value instanceof Number || value instanceof Boolean) {
			return value.toString();
		}
		throw new CliException("샘플 값 " + value + "를 다시 쓸 수 없습니다. 이 샘플은 손으로 이어 쓰세요.");
	}

	private static String next(Context ctx, String module, String event, int version) {
		List<String> listeners = new ArrayList<>();
		for (JavaSources.SourceType type : ctx.project().sources().types().values()) {
			for (Tree member : type.tree().getMembers()) {
				if (member instanceof MethodTree method
						&& method.getModifiers().getAnnotations().stream().anyMatch((a) -> a.getAnnotationType().toString().endsWith("ApplicationModuleListener"))
						&& method.getParameters().size() == 1) {
					String param = method.getParameters().getFirst().getType().toString();
					if (param.equals(event) || param.endsWith("." + event)) {
						listeners.add(type.simpleName() + "." + method.getName());
					}
				}
			}
		}
		return "다음: " + event + "는 이제 schemaVersion " + version + "입니다. 발행하는 곳은 그대로 컴파일되고 새 필드는 null로 갑니다. 새 값을 보내려면 "
				+ "새 of()를 부르세요. " + (listeners.isEmpty() ? "받는 쪽은 아직 없습니다"
						: "받는 쪽(" + String.join(", ", listeners) + ")은 옛 이벤트의 새 필드가 null일 때도 처리해야 합니다");
	}
}
