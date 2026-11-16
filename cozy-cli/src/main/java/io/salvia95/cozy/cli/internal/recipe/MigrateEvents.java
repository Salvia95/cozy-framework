package io.salvia95.cozy.cli.internal.recipe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.sun.source.tree.Tree;

import io.salvia95.cozy.cli.internal.AppProject;
import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.JavaSources;
import io.salvia95.cozy.rules.Layer;

/**
 * {@code cozy migrate events}: 기능 모듈의 옛 이벤트 자리({@code {모듈}/spi/event/})에 있는 이벤트를 이벤트 계약 모듈
 * ({@code events/spi/{모듈}/})로 옮긴다.
 *
 * <ol>
 * <li>이벤트 파일(테스트 폴더의 같은 패키지 포함)을 옮기고 {@code package} 선언을 고친다. 옛 {@code package-info.java}는 지우고,
 * 이벤트 계약 모듈과 주인 폴더의 {@code package-info.java}를 만든다.</li>
 * <li>앱 전체({@code src/main/java}, {@code src/test/java})에서 옛 패키지를 가리키는 이름(import, static import, 전체 이름)을 고친다.</li>
 * <li>이벤트를 내거나 받는 모듈에 {@code "events :: spi"}를 선언한다. 받는 모듈의 {@code "{주인} :: spi"}는 그 모듈이 주인의 다른
 * spi(Java, 템플릿, Mapper XML)를 쓰지 않을 때만 지운다.</li>
 * <li>저장된 클래스 이름을 새 이름으로 바꾸는 앱 마이그레이션 {@code V{시각}__cozy_move_event_contracts.sql}을 만든다. 업데이트한 PC에
 * 남아 있던 미완료 발행 기록이 새 클래스로 읽히고, 이미 처리한 이벤트는 인박스가 계속 건너뛴다.</li>
 * </ol>
 * 옛 자리에 이벤트가 아닌 타입이나 하위 폴더가 있으면 아무것도 바꾸지 않고 실패한다. 자원 파일에 옛 이름이 남아 있으면 손으로 할 일로
 * 알린다(이미 적용된 마이그레이션은 보지 않는다). 옮길 것이 없으면 바뀐 것이 없다.
 */
final class MigrateEvents {

	static final String USAGE = "cozy migrate events [--dry-run] [--json]";

	/** 앱 마이그레이션 이름의 뒷부분 */
	static final String MIGRATION_SUFFIX = "cozy_move_event_contracts";

	private static final List<String> SOURCE_ROOTS = List.of("src/main/java", "src/test/java");

	private static final List<String> RESOURCE_ROOTS = List.of("src/main/resources", "src/test/resources");

	private static final Set<String> TEXT_FILES = Set.of("xml", "yml", "yaml", "properties", "json", "html", "sql", "txt",
			"js", "css", "md");

	private MigrateEvents() {
	}

	/** 옮기는 이벤트 하나: 옛 이름 → 새 이름 */
	record Move(String owner, String from, String to) {
	}

	/** @return 다음 할 일 */
	static String run(Context ctx, List<String> rest) {
		if (rest.size() != 1 || !rest.getFirst().equals("events")) {
			throw new CliException("cozy migrate events 뒤에는 아무것도 적지 않습니다 (" + USAGE + ").");
		}
		AppProject project = ctx.project();
		Plan plan = ctx.plan();
		// 옛 패키지 → 새 패키지 (주인 모듈마다 하나)
		Map<String, String> packages = new LinkedHashMap<>();
		Map<String, String> owners = new LinkedHashMap<>();
		List<Move> moves = new ArrayList<>();
		for (AppProject.Module module : new TreeMap<>(project.modules()).values()) {
			if (module.name().equals(AppProject.EVENTS)) {
				continue;
			}
			String from = ctx.rolePackage(module.name(), Layer.SPI_EVENT);
			if (files(project.dir(), from).isEmpty()) {
				continue;
			}
			packages.put(from, ctx.eventPackage(module.name()));
			owners.put(from, module.name());
			moves.addAll(events(ctx, module.name(), from));
		}
		if (packages.isEmpty()) {
			return "옛 이벤트 자리(spi/event)에 이벤트가 없어 옮길 것이 없습니다. cozy check, 그다음 mvn verify.";
		}
		ctx.requireStarter("cozy-starter-events", "cozy migrate events");

		Set<String> receivers = new LinkedHashSet<>();
		Map<String, Set<String>> received = new LinkedHashMap<>();
		for (String root : SOURCE_ROOTS) {
			for (Path path : walk(project.dir().resolve(root), ".java")) {
				String file = relative(project, path);
				String text = plan.current(file);
				String pkgPath = file.substring(root.length() + 1, Math.max(file.lastIndexOf('/'), root.length() + 1));
				String pkg = pkgPath.replace('/', '.');
				String target = packages.get(pkg);
				if (target != null) {
					move(ctx, root, file, text, pkg, target, packages);
					continue;
				}
				String next = rewrite(text, packages);
				if (next.equals(text)) {
					continue;
				}
				plan.edit(file, next, "이벤트 이름을 이벤트 계약 모듈(" + AppProject.EVENTS + ".spi)로");
				if (root.equals("src/main/java")) {
					project.moduleOf(pkg).filter((m) -> !m.name().equals(AppProject.EVENTS)).ifPresent((m) -> {
						receivers.add(m.name());
						packages.forEach((from, to) -> {
							if (refersTo(text, from)) {
								received.computeIfAbsent(m.name(), (k) -> new LinkedHashSet<>()).add(owners.get(from));
							}
						});
					});
				}
			}
		}
		for (String owner : owners.values()) {
			ctx.ensureEventsModule(owner);
			ctx.declareEvents(owner);
		}
		for (String receiver : receivers) {
			ctx.declareEvents(receiver);
		}
		received.forEach((receiver, from) -> from.stream()
			.filter((owner) -> !owner.equals(receiver))
			.forEach((owner) -> dropOwnerDependency(ctx, receiver, owner)));
		leftInResources(ctx, packages);
		String migration = "src/main/resources/db/migration/V" + ctx.nextMigrationTime() + "__" + MIGRATION_SUFFIX + ".sql";
		plan.create(migration, migrationSql(moves));
		return "mvn clean verify로 확인합니다. 옮기기 전의 클래스가 target/에 남아 있으면 이름이 겹치므로 clean이 필요합니다. 배포한 앱은 "
				+ "기동할 때 마이그레이션 " + migration.substring(migration.lastIndexOf('/') + 1) + "로 저장된 발행 기록의 클래스 이름을 바꿉니다.";
	}

	/** 옛 자리의 이벤트. 이벤트(CozyEvent record)가 아닌 타입이 있으면 실패한다 */
	private static List<Move> events(Context ctx, String owner, String from) {
		List<Move> moves = new ArrayList<>();
		for (JavaSources.SourceType type : ctx.project().sources().types().values()) {
			if (!type.pkg().equals(from) && !type.pkg().startsWith(from + ".")) {
				continue;
			}
			String file = type.unit().relative();
			if (!type.pkg().equals(from)) {
				throw new CliException(file + ": 옛 이벤트 자리(" + from + ")의 하위 폴더는 옮기지 않습니다. 이벤트를 " + from
						+ " 바로 아래로 옮긴 뒤 다시 실행하세요.");
			}
			if (!type.name().equals(type.topLevel())) {
				continue;
			}
			boolean event = type.kind() == Tree.Kind.RECORD && type.tree()
				.getImplementsClause()
				.stream()
				.anyMatch((i) -> i.toString().equals("CozyEvent") || i.toString().endsWith(".CozyEvent"));
			if (!event) {
				throw new CliException(file + ": " + type.simpleName() + "는 이벤트(CozyEvent record)가 아니라 옮기지 않습니다. 이벤트 "
						+ "계약 모듈에는 이벤트만 둡니다. 이 타입을 " + ctx.rolePackage(owner, Layer.SPI_DTO) + "로 옮긴 뒤 다시 실행하세요.");
			}
			moves.add(new Move(owner, type.name(), ctx.eventPackage(owner) + "." + type.simpleName()));
		}
		moves.sort((a, b) -> a.from().compareTo(b.from()));
		return moves;
	}

	/** 옛 자리의 파일 하나: package-info는 지우고(새 것은 따로 만든다), 나머지는 새 자리에 만들고 지운다 */
	private static void move(Context ctx, String root, String file, String text, String from, String to,
			Map<String, String> packages) {
		Plan plan = ctx.plan();
		String name = file.substring(file.lastIndexOf('/') + 1);
		if (name.equals("package-info.java") && root.equals("src/main/java")) {
			plan.delete(file, "옛 이벤트 자리 → " + to);
			return;
		}
		String moved = Pattern.compile("(?m)^(\\s*package\\s+)" + Pattern.quote(from) + "(\\s*;)")
			.matcher(text)
			.replaceFirst("$1" + Matcher.quoteReplacement(to) + "$2");
		String target = root + "/" + to.replace('.', '/') + "/" + name;
		plan.create(target, rewrite(moved, packages));
		plan.delete(file, "→ " + target);
	}

	/** 옛 패키지를 가리키는 이름(타입 이름이나 {@code *}가 뒤따르는 것)을 새 패키지로 */
	static String rewrite(String text, Map<String, String> packages) {
		String next = text;
		for (Map.Entry<String, String> entry : packages.entrySet()) {
			next = reference(entry.getKey()).matcher(next).replaceAll(Matcher.quoteReplacement(entry.getValue()));
		}
		return next;
	}

	private static boolean refersTo(String text, String pkg) {
		return reference(pkg).matcher(text).find();
	}

	private static Pattern reference(String pkg) {
		return Pattern.compile("(?<![\\w.])" + Pattern.quote(pkg) + "(?=\\s*\\.\\s*(?:[A-Z]|\\*))");
	}

	/**
	 * 받는 모듈이 주인의 이벤트만 받았다면 {@code "{주인} :: spi"}를 지운다. 주인의 다른 spi(Java, 템플릿, Mapper XML)를 쓰면 그대로
	 * 둔다. 주인을 다른 형식으로 적었으면 판단하지 않고 손으로 할 일로 알린다.
	 */
	private static void dropOwnerDependency(Context ctx, String receiver, String owner) {
		String file = ctx.packageInfo(ctx.modulePackage(receiver));
		String current = ctx.plan().current(file);
		if (current == null || !current.contains("@ApplicationModule")) {
			return;
		}
		List<String> declared = PackageInfos.dependencies(file, current);
		List<String> naming = declared.stream()
			.filter((d) -> d.replaceAll("\\s*::.*", "").strip().equals(owner))
			.toList();
		if (naming.isEmpty() || usesOwner(ctx, receiver, owner)) {
			return;
		}
		for (String dependency : naming) {
			if (dependency.replaceAll("\\s+", "").equals(owner + "::spi")) {
				ctx.plan().edit(file, PackageInfos.removeDependency(file, ctx.plan().current(file), dependency),
						"allowedDependencies -= \"" + dependency + "\" (이벤트만 받으므로 " + AppProject.EVENTS + " :: spi로 충분)");
			}
			else {
				ctx.manual(file, receiver + "는 " + owner + "의 이벤트만 받는 것으로 보입니다. " + owner + "의 다른 것을 쓰지 않으면 "
						+ "allowedDependencies의 \"" + dependency + "\"를 지우세요. 형식이 달라 판단하지 않고 그대로 두었습니다.", dependency);
			}
		}
	}

	/** 받는 모듈이 (옮긴 뒤에도) 주인 모듈의 spi를 쓰는가 */
	private static boolean usesOwner(Context ctx, String receiver, String owner) {
		AppProject project = ctx.project();
		Pattern java = Pattern.compile("(?<![\\w.])" + Pattern.quote(ctx.rolePackage(owner, Layer.SPI)) + "(?![\\w])");
		Path main = project.dir().resolve("src/main/java").resolve(ctx.modulePackage(receiver).replace('.', '/'));
		for (Path path : walk(main, ".java")) {
			if (java.matcher(ctx.plan().current(relative(project, path))).find()) {
				return true;
			}
		}
		Pattern template = Pattern.compile("(?<![\\w/])" + Pattern.quote(owner) + "/spi/");
		for (Path path : walk(project.dir().resolve("src/main/resources/templates/" + receiver), ".html")) {
			if (template.matcher(ctx.plan().current(relative(project, path))).find()) {
				return true;
			}
		}
		for (Path path : walk(project.dir().resolve("src/main/resources/mapper/" + receiver), ".xml")) {
			if (java.matcher(ctx.plan().current(relative(project, path))).find()) {
				return true;
			}
		}
		return false;
	}

	/** 자원 파일에 남은 옛 이름. 이미 적용된 마이그레이션은 고치면 안 되므로 보지 않는다 */
	private static void leftInResources(Context ctx, Map<String, String> packages) {
		AppProject project = ctx.project();
		for (String root : RESOURCE_ROOTS) {
			for (Path path : walk(project.dir().resolve(root), "")) {
				String file = relative(project, path);
				String extension = file.substring(file.lastIndexOf('.') + 1);
				if (!TEXT_FILES.contains(extension) || file.startsWith("src/main/resources/db/migration/")) {
					continue;
				}
				String text = ctx.plan().current(file);
				packages.forEach((from, to) -> {
					if (text.contains(from + ".")) {
						ctx.manual(file, "옛 이벤트 이름(" + from + ".…)이 남아 있습니다. " + to + ".…로 바꾸세요.", from + " → " + to);
					}
				});
			}
		}
	}

	/** 저장된 발행 기록과 인박스의 클래스 이름을 바꾸는 SQL. 리스너 id는 {@code …(이벤트 FQN)}로 끝난다 */
	static String migrationSql(List<Move> moves) {
		StringBuilder sql = new StringBuilder();
		sql.append("-- 이벤트 클래스를 이벤트 계약 모듈(events)로 옮겨 클래스 이름이 바뀌었다 (cozy migrate events).\n")
			.append("-- 저장된 발행 기록과 인박스의 클래스 이름을 새 이름으로 바꾼다. 미완료 발행 기록은 다음 기동 때 새 클래스로 다시 발행되고,\n")
			.append("-- 이미 처리한 이벤트는 인박스가 계속 건너뛴다.\n");
		for (Move move : moves) {
			String from = "(" + move.from() + ")";
			String to = "(" + move.to() + ")";
			sql.append("\n-- ").append(move.from()).append(" → ").append(move.to()).append('\n')
				.append("UPDATE cozy_event_publication SET event_type = '").append(move.to())
				.append("' WHERE event_type = '").append(move.from()).append("';\n")
				.append("UPDATE cozy_event_publication SET listener_id = replace(listener_id, '").append(from).append("', '")
				.append(to).append("') WHERE instr(listener_id, '").append(from).append("') > 0;\n")
				.append("UPDATE cozy_inbox SET listener_id = replace(listener_id, '").append(from).append("', '").append(to)
				.append("') WHERE instr(listener_id, '").append(from).append("') > 0;\n");
		}
		return sql.toString();
	}

	/** 옛 자리의 Java 파일 (main과 test) */
	private static List<Path> files(Path project, String pkg) {
		List<Path> files = new ArrayList<>();
		for (String root : SOURCE_ROOTS) {
			files.addAll(walk(project.resolve(root).resolve(pkg.replace('.', '/')), ".java"));
		}
		return files;
	}

	private static List<Path> walk(Path dir, String suffix) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			return walk.filter((p) -> Files.isRegularFile(p) && p.toString().endsWith(suffix)).sorted().toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static String relative(AppProject project, Path path) {
		return project.dir().relativize(path).toString().replace('\\', '/');
	}
}
