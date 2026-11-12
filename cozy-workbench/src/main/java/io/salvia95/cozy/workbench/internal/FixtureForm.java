package io.salvia95.cozy.workbench.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 상태의 샘플 데이터를 화면 명세의 모델 타입에 맞춰 묶는다. 객체는 속성 표, 객체의 목록은 그리드가 되고, 값마다 타입에 맞는 입력(체크박스, 숫자,
 * 날짜, 선택)을 쓴다.
 *
 * <ul>
 * <li>값의 자리와 고칠 수 있는지는 {@link FixtureEditor}가 알려 준다. 이 클래스는 묶고 고르기만 하고 YAML을 쓰지 않는다.</li>
 * <li>타입은 실행 중인 앱에서 읽는다. record는 구성 요소, 폼 클래스는 setter가 있는 필드(선언 순서)가 속성이다.</li>
 * <li>타입을 알 수 없으면(찾을 수 없는 클래스, {@code Map}, {@code Object}, 검증 오류 {@code _errors}) YAML의 모양과 값의 종류대로 묶는다.</li>
 * <li>타입에는 있는데 명세에 없는 값은 "없음"으로 보인다. 키를 더하는 일은 하지 않는다.</li>
 * </ul>
 */
final class FixtureForm {

	private FixtureForm() {
	}

	/**
	 * 입력 하나.
	 *
	 * @param id 화면 안에서 유일한 id
	 * @param path 상태 안의 자리 ({@code result/rows/0/title})
	 * @param value 보일 값. 적다가 저장하지 못한 값이 있으면 그 값이다. {@code null}은 빈 글이다
	 * @param kind {@code text}, {@code int}, {@code number}, {@code bool}, {@code date}, {@code datetime},
	 * {@code time}, {@code enum}, {@code null}
	 * @param control {@code text}, {@code number}, {@code checkbox}, {@code select}, {@code date},
	 * {@code datetime-local}, {@code time}, {@code fixed}(읽기 전용), {@code absent}(명세에 없음)
	 * @param options {@code select}의 선택지
	 * @param editable 고칠 수 있는가
	 * @param present 명세에 값이 있는가
	 * @param placeholder 빈 칸에 보일 글
	 */
	public record Input(String id, String path, String value, String kind, String control, List<String> options, boolean editable,
			boolean present, String placeholder) {

		/** 폼의 이름 */
		public String name() {
			return "v." + this.path;
		}

		public boolean checked() {
			return "true".equals(this.value);
		}

		/** 숫자 입력의 간격 */
		public String step() {
			return this.kind.equals("int") ? "1" : "any";
		}
	}

	/**
	 * 속성 표의 행 하나.
	 *
	 * @param name 속성 이름 (식별자)
	 * @param title 사람이 읽는 이름 (DTO Javadoc의 짧은 이름). 없으면 빈 글
	 * @param description Javadoc의 설명 전체. 없으면 빈 글
	 * @param kind 종류의 이름 ({@code 정수}, enum이면 타입 이름)
	 */
	public record Prop(String name, String title, String description, String kind, Input input) {

		/** 화면 낭독기가 읽을 이름 */
		public String spoken() {
			return this.title.isEmpty() ? this.name : this.title;
		}
	}

	/** 그리드의 열 하나. 구성은 {@link Prop}과 같다 */
	public record Column(String name, String title, String description, String kind) {

		public String spoken() {
			return this.title.isEmpty() ? this.name : this.title;
		}
	}

	/** 그리드의 행 하나. {@code number}는 1부터 */
	public record Row(int number, List<Input> cells) {
	}

	/**
	 * 속성 표나 그리드 하나.
	 *
	 * @param path 상태 안의 자리 ({@code result/rows})
	 * @param title 모델 안의 경로 ({@code page}, {@code memo/bodyLines}). 모델 자체면 빈 글
	 * @param type 타입의 짧은 이름 ({@code CozyPage}, {@code List<Row>}). 모르면 빈 글
	 * @param newItem 그리드에 더할 행의 YAML ({@code { id: 0, title: "" }}). 원소 타입을 모르면 빈 글
	 */
	public record Block(String path, String title, String type, boolean grid, List<Prop> props, List<Column> columns, List<Row> rows,
			String newItem) {

		/** 화면 안의 위치 ({@code #fixture-result-rows}) */
		public String anchor() {
			return anchor(this.path);
		}

		/** 행을 더할 수 있는가: 원소 타입을 알거나, 복제할 행이 있다 */
		public boolean addable() {
			return !this.newItem.isEmpty() || !this.rows.isEmpty();
		}

		static String anchor(String path) {
			return "fixture-" + path.replaceAll("[^A-Za-z0-9_-]", "-");
		}
	}

	/**
	 * 모델 하나 (상태의 최상위 키 하나).
	 *
	 * @param name 모델 이름
	 * @param label 사람이 읽는 이름. 모델이면 빈 글
	 * @param type 타입의 짧은 이름
	 * @param typeName 화면 명세에 적힌 타입
	 * @param note 타입을 읽지 못했을 때의 이유. 없으면 빈 글
	 */
	public record Section(String name, String label, String type, String typeName, String note, List<Block> blocks) {
	}

	/** 값의 모양 */
	private sealed interface Shape {
	}

	/** 스칼라. {@code label}은 종류의 이름 (enum이면 타입 이름) */
	private record Scalar(String kind, List<String> options, String label) implements Shape {

		Scalar(String kind) {
			this(kind, List.of(), WorkbenchTerms.KINDS.getOrDefault(kind, kind));
		}
	}

	private record ListOf(Type element) implements Shape {
	}

	private record ObjectOf(Class<?> type) implements Shape {
	}

	private record Untyped() implements Shape {
	}

	private record Property(String name, Type type) {
	}

	private static final Shape UNTYPED = new Untyped();

	private static final Pattern INT = Pattern.compile("-?\\d+");

	private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?");

	private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	private static final Pattern DATETIME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?");

	private static final Pattern TIME = Pattern.compile("\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?");

	/**
	 * @param yaml 화면 명세
	 * @param state 상태 이름
	 * @param model 모델 이름 → 타입 (화면 명세의 {@code model})
	 * @param draft 자리 → 적다가 저장하지 못한 값
	 */
	static List<Section> of(String yaml, String state, Map<String, String> model, Map<String, String> draft) {
		return of(yaml, state, model, draft, (type) -> Map.of());
	}

	/**
	 * @param docs 타입 → 속성 이름 → 설명 ({@link SourceDocs}). 없으면 빈 맵
	 */
	static List<Section> of(String yaml, String state, Map<String, String> model, Map<String, String> draft,
			java.util.function.Function<Class<?>, Map<String, String>> docs) {
		Map<String, FixtureEditor.Field> fields = new LinkedHashMap<>();
		FixtureEditor.fields(yaml, state).forEach((f) -> fields.put(f.path(), f));
		Builder builder = new Builder(fields, draft, docs);
		List<Section> sections = new ArrayList<>();
		FixtureEditor.values(yaml, state).forEach((name, value) -> {
			if (name.equals(io.salvia95.cozy.ui.catalog.ScreenSpec.ERRORS_KEY)) {
				sections.add(new Section(name, "검증 오류", "", "", "", builder.root(name, UNTYPED, value)));
				return;
			}
			String typeName = model.getOrDefault(name, "");
			Optional<Class<?>> type = load(typeName);
			Shape shape = type.<Shape>map(FixtureForm::shape).orElse(UNTYPED);
			String note = type.isPresent() ? "" : "모델 타입을 찾을 수 없습니다: " + (typeName.isEmpty() ? "(model에 없음)" : typeName)
					+ ". 화면 명세에 적힌 모양대로 표시합니다.";
			sections.add(new Section(name, "", type.map(FixtureForm::simpleName).orElse(""), typeName, note, builder.root(name, shape, value)));
		});
		return sections;
	}

	/** 자리가 {@code path}인 그리드 */
	static Optional<Block> grid(List<Section> sections, String path) {
		return sections.stream().flatMap((s) -> s.blocks().stream()).filter((b) -> b.grid() && b.path().equals(path)).findFirst();
	}

	/** 모든 입력 (화면 순서) */
	static List<Input> inputs(List<Section> sections) {
		List<Input> inputs = new ArrayList<>();
		for (Section section : sections) {
			for (Block block : section.blocks()) {
				block.props().forEach((p) -> inputs.add(p.input()));
				block.rows().forEach((r) -> inputs.addAll(r.cells()));
			}
		}
		return inputs;
	}

	/** 보낸 값 가운데 보인 값과 다른 것. 날짜와 시각은 같은 때를 가리키면 같다 */
	static Map<String, String> changes(List<Input> inputs, Map<String, String> values) {
		Map<String, Input> byPath = new LinkedHashMap<>();
		inputs.forEach((i) -> byPath.put(i.path(), i));
		Map<String, String> changed = new LinkedHashMap<>();
		values.forEach((path, value) -> {
			Input input = byPath.get(path);
			if (input == null || !same(input, value)) {
				changed.put(path, value);
			}
		});
		return changed;
	}

	private static boolean same(Input input, String value) {
		if (input.value().equals(value)) {
			return true;
		}
		try {
			return switch (input.kind()) {
				case "datetime" -> LocalDateTime.parse(input.value()).equals(LocalDateTime.parse(value));
				case "time" -> LocalTime.parse(input.value()).equals(LocalTime.parse(value));
				default -> false;
			};
		}
		catch (RuntimeException ex) {
			return false;
		}
	}

	/** 바뀐 값이 종류에 맞지 않으면 그 자리와 이유. 없으면 {@code null} */
	static String problem(List<Input> inputs, Map<String, String> values) {
		Map<String, String> changed = changes(inputs, values);
		for (Input input : inputs) {
			String value = changed.get(input.path());
			if (value == null) {
				continue;
			}
			String expected = switch (input.kind()) {
				case "bool" -> (value.equals("true") || value.equals("false")) ? null : "true나 false만 씁니다";
				case "int" -> INT.matcher(value).matches() ? null : "정수만 씁니다";
				case "number" -> NUMBER.matcher(value).matches() ? null : "숫자만 씁니다";
				case "date" -> DATE.matcher(value).matches() ? null : "날짜(2026-11-30)만 씁니다";
				case "datetime" -> DATETIME.matcher(value).matches() ? null : "날짜와 시각(2026-11-30T09:00)만 씁니다";
				case "time" -> TIME.matcher(value).matches() ? null : "시각(09:00)만 씁니다";
				case "enum" -> input.options().contains(value) ? null : "다음 가운데 하나만 씁니다: " + String.join(", ", input.options());
				default -> null;
			};
			if (expected != null) {
				return input.path() + ": " + expected + " (적은 값: " + value + ")";
			}
		}
		return null;
	}

	// ── 타입 ─────────────────────────────────────────────────────────────

	/** FQN. 중첩 클래스는 {@code Outer.Inner}와 {@code Outer$Inner} 모두 된다 */
	private static Optional<Class<?>> load(String typeName) {
		if (typeName.isBlank()) {
			return Optional.empty();
		}
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		String candidate = typeName.strip();
		while (true) {
			try {
				return Optional.of(Class.forName(candidate, false, classLoader));
			}
			catch (ClassNotFoundException | LinkageError ex) {
				int dot = candidate.lastIndexOf('.');
				if (dot < 0) {
					return Optional.empty();
				}
				candidate = candidate.substring(0, dot) + "$" + candidate.substring(dot + 1);
			}
		}
	}

	private static Shape shape(Type type) {
		if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
			Type[] args = parameterized.getActualTypeArguments();
			if (Collection.class.isAssignableFrom(raw)) {
				return new ListOf(args[0]);
			}
			if (raw == Optional.class) {
				return shape(args[0]);
			}
			return Map.class.isAssignableFrom(raw) ? UNTYPED : shape(raw);
		}
		if (!(type instanceof Class<?> c)) {
			return UNTYPED;
		}
		if (c == boolean.class || c == Boolean.class) {
			return new Scalar("bool");
		}
		if (c == int.class || c == long.class || c == short.class || c == byte.class || c == Integer.class || c == Long.class
				|| c == Short.class || c == Byte.class || c == BigInteger.class) {
			return new Scalar("int");
		}
		if (c == double.class || c == float.class || c == BigDecimal.class || Number.class.isAssignableFrom(c)) {
			return new Scalar("number");
		}
		if (c == LocalDate.class) {
			return new Scalar("date");
		}
		if (c == LocalDateTime.class) {
			return new Scalar("datetime");
		}
		if (c == LocalTime.class) {
			return new Scalar("time");
		}
		if (c.isEnum()) {
			return new Scalar("enum", Arrays.stream(c.getEnumConstants()).map((e) -> ((Enum<?>) e).name()).toList(), simpleName(c));
		}
		if (c.isArray()) {
			return new ListOf(c.getComponentType());
		}
		if (Collection.class.isAssignableFrom(c)) {
			return new ListOf(Object.class);
		}
		if (c == Object.class || Map.class.isAssignableFrom(c)) {
			return UNTYPED;
		}
		if (c.isPrimitive() || c == String.class || c == char.class || c == Character.class || CharSequence.class.isAssignableFrom(c)
				|| c.getName().startsWith("java.")) {
			return new Scalar("text");
		}
		return new ObjectOf(c);
	}

	/** record는 구성 요소, 그 밖의 클래스는 setter가 있거나 public인 필드 (상위 클래스부터, 선언 순서) */
	private static List<Property> properties(Class<?> type) {
		if (type.isRecord()) {
			return Arrays.stream(type.getRecordComponents()).map((RecordComponent c) -> new Property(c.getName(), c.getGenericType())).toList();
		}
		List<Class<?>> chain = new ArrayList<>();
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			chain.addFirst(c);
		}
		Set<String> setters = new LinkedHashSet<>();
		Arrays.stream(type.getMethods())
			.filter((m) -> m.getName().startsWith("set") && m.getName().length() > 3 && m.getParameterCount() == 1)
			.forEach((m) -> setters.add(Character.toLowerCase(m.getName().charAt(3)) + m.getName().substring(4)));
		List<Property> properties = new ArrayList<>();
		for (Class<?> c : chain) {
			for (Field field : c.getDeclaredFields()) {
				int mod = field.getModifiers();
				if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || field.isSynthetic()) {
					continue;
				}
				if (Modifier.isPublic(mod) || setters.contains(field.getName())) {
					properties.add(new Property(field.getName(), field.getGenericType()));
				}
			}
		}
		return properties;
	}

	private static String simpleName(Class<?> type) {
		return type.getSimpleName();
	}

	/** {@code List<Row>}. 원소의 타입을 모르면 {@code List<?>} */
	private static String typeLabel(ListOf list) {
		String element = switch (shape(list.element())) {
			case ObjectOf o -> simpleName(o.type());
			case Scalar s when list.element() instanceof Class<?> c -> simpleName(c);
			default -> "?";
		};
		return "List<" + element + ">";
	}

	// ── 새 행 ─────────────────────────────────────────────────────────────

	/** 원소 타입으로 만든 새 행의 YAML (흐름 표기 한 줄). 모르는 타입이면 빈 글 */
	static String newItem(Type element) {
		String item = defaultValue(shape(element), 0);
		return (item == null) ? "" : item;
	}

	/** 타입의 기본값. 글은 빈 글, 숫자는 0, 참·거짓은 false, 날짜는 오늘, enum은 첫 상수, 목록은 빈 목록. 모르면 {@code null} */
	private static String defaultValue(Shape shape, int depth) {
		return switch (shape) {
			case Scalar s -> switch (s.kind()) {
				case "bool" -> "false";
				case "int", "number" -> "0";
				case "date" -> "\"" + LocalDate.now() + "\"";
				case "datetime" -> "\"" + LocalDate.now() + "T09:00\"";
				case "time" -> "\"09:00\"";
				case "enum" -> s.options().isEmpty() ? "null" : plainOrQuoted(s.options().getFirst());
				default -> "\"\"";
			};
			case ListOf l -> "[]";
			case ObjectOf o when depth < 3 -> {
				List<String> parts = new ArrayList<>();
				for (Property property : properties(o.type())) {
					String value = defaultValue(shape(property.type()), depth + 1);
					parts.add(property.name() + ": " + ((value == null) ? "null" : value));
				}
				yield parts.isEmpty() ? "{}" : "{ " + String.join(", ", parts) + " }";
			}
			default -> null;
		};
	}

	/** enum 상수는 따옴표 없이. YAML이 참·거짓이나 null로 읽을 이름(YES, OFF, NULL 등)만 따옴표로 */
	private static String plainOrQuoted(String constant) {
		return constant.matches("(?i)y|yes|n|no|true|false|on|off|null|~") ? "\"" + constant + "\"" : constant;
	}

	// ── 묶기 ─────────────────────────────────────────────────────────────

	private static final class Builder {

		private final Map<String, FixtureEditor.Field> fields;

		private final Map<String, String> draft;

		private final java.util.function.Function<Class<?>, Map<String, String>> docs;

		private final Map<Class<?>, Map<String, String>> docCache = new java.util.HashMap<>();

		private int next;

		Builder(Map<String, FixtureEditor.Field> fields, Map<String, String> draft,
				java.util.function.Function<Class<?>, Map<String, String>> docs) {
			this.fields = fields;
			this.draft = draft;
			this.docs = docs;
		}

		/** 타입의 속성 설명. 타입을 모르면 빈 맵 */
		private Map<String, String> docs(Class<?> type) {
			if (type == null) {
				return Map.of();
			}
			return this.docCache.computeIfAbsent(type, (t) -> {
				try {
					return this.docs.apply(t);
				}
				catch (RuntimeException ex) {
					return Map.of();
				}
			});
		}

		private Prop prop(Class<?> owner, String name, String kind, Input input) {
			String description = docs(owner).getOrDefault(name, "");
			return new Prop(name, description.isEmpty() ? "" : SourceDocs.shortName(description), description, kind, input);
		}

		private Column column(Class<?> owner, String name, String kind) {
			String description = docs(owner).getOrDefault(name, "");
			return new Column(name, description.isEmpty() ? "" : SourceDocs.shortName(description), description, kind);
		}

		/** 모델 하나의 블록들 */
		List<Block> root(String name, Shape shape, Object value) {
			List<Block> out = new ArrayList<>();
			Shape fitted = fit(shape, value);
			switch (fitted) {
				case ObjectOf o -> object(name, "", o.type(), (Map<?, ?>) value, out);
				case ListOf l -> grid(name, "", l.element(), (List<?>) value, out);
				case Untyped u when value instanceof Map<?, ?> map -> untypedObject(name, "", map, out);
				case Untyped u when value instanceof List<?> list -> grid(name, "", Object.class, list, out);
				default -> {
					Input input = input(name, fitted);
					out.add(new Block(name, "", "", false, List.of(prop(null, name, label(fitted, input), input)), List.of(), List.of(), ""));
				}
			}
			return out;
		}

		/** 값이 타입의 모양과 다르면(객체 자리에 null 등) YAML의 모양을 따른다 */
		private static Shape fit(Shape shape, Object value) {
			return switch (shape) {
				case ObjectOf o when !(value instanceof Map) -> UNTYPED;
				case ListOf l when !(value instanceof List) -> UNTYPED;
				case Scalar s when value instanceof Map || value instanceof List -> UNTYPED;
				default -> shape;
			};
		}

		private void object(String path, String title, Class<?> type, Map<?, ?> map, List<Block> out) {
			List<Prop> props = new ArrayList<>();
			List<Runnable> nested = new ArrayList<>();
			Set<String> known = new LinkedHashSet<>();
			for (Property property : properties(type)) {
				known.add(property.name());
				member(type, path, title, property.name(), shape(property.type()), map, props, nested, out);
			}
			for (Object key : map.keySet()) {
				if (!known.contains(String.valueOf(key))) {
					member(type, path, title, String.valueOf(key), UNTYPED, map, props, nested, out);
				}
			}
			if (!props.isEmpty() || nested.isEmpty()) {
				out.add(new Block(path, title, title.isEmpty() ? "" : simpleName(type), false, props, List.of(), List.of(), ""));
			}
			nested.forEach(Runnable::run);
		}

		private void untypedObject(String path, String title, Map<?, ?> map, List<Block> out) {
			List<Prop> props = new ArrayList<>();
			List<Runnable> nested = new ArrayList<>();
			for (Object key : map.keySet()) {
				member(null, path, title, String.valueOf(key), UNTYPED, map, props, nested, out);
			}
			if (!props.isEmpty() || nested.isEmpty()) {
				out.add(new Block(path, title, "", false, props, List.of(), List.of(), ""));
			}
			nested.forEach(Runnable::run);
		}

		/** 객체의 속성 하나: 스칼라면 표의 행, 객체나 목록이면 표 뒤에 따로 보일 블록 */
		private void member(Class<?> owner, String path, String title, String name, Shape shape, Map<?, ?> map, List<Prop> props,
				List<Runnable> nested, List<Block> out) {
			String child = path + "/" + name;
			String childTitle = title.isEmpty() ? name : title + "/" + name;
			if (!map.containsKey(name)) {
				Input absent = absent(child, shape);
				props.add(prop(owner, name, label(shape, absent), absent));
				return;
			}
			Object value = map.get(name);
			Shape fitted = fit(shape, value);
			switch (fitted) {
				case ObjectOf o -> nested.add(() -> object(child, childTitle, o.type(), (Map<?, ?>) value, out));
				case ListOf l -> nested.add(() -> grid(child, childTitle, l.element(), (List<?>) value, out));
				case Untyped u when value instanceof Map<?, ?> m -> nested.add(() -> untypedObject(child, childTitle, m, out));
				case Untyped u when value instanceof List<?> list -> nested.add(() -> grid(child, childTitle, Object.class, list, out));
				default -> {
					Input input = input(child, fitted);
					props.add(prop(owner, name, label(fitted, input), input));
				}
			}
		}

		/** 목록 하나. 객체의 목록이면 속성마다 열, 스칼라의 목록이면 "값" 열 하나 */
		private void grid(String path, String title, Type element, List<?> list, List<Block> out) {
			Shape shape = shape(element);
			boolean maps = list.stream().anyMatch((item) -> item instanceof Map);
			String type = (shape instanceof Untyped) ? "" : typeLabel(new ListOf(element));
			String newItem = newItem(element);
			Class<?> owner = (shape instanceof ObjectOf o) ? o.type() : null;
			List<String> names = new ArrayList<>();
			List<Shape> shapes = new ArrayList<>();
			if (shape instanceof ObjectOf o) {
				for (Property property : properties(o.type())) {
					names.add(property.name());
					shapes.add(shape(property.type()));
				}
			}
			if (shape instanceof ObjectOf || (shape instanceof Untyped && maps)) {
				for (Object item : list) {
					if (item instanceof Map<?, ?> map) {
						for (Object key : map.keySet()) {
							if (!names.contains(String.valueOf(key))) {
								names.add(String.valueOf(key));
								shapes.add(UNTYPED);
							}
						}
					}
				}
				List<Row> rows = new ArrayList<>();
				for (int i = 0; i < list.size(); i++) {
					Map<?, ?> map = (list.get(i) instanceof Map<?, ?> m) ? m : Map.of();
					List<Input> cells = new ArrayList<>();
					for (int c = 0; c < names.size(); c++) {
						cells.add(cell(path + "/" + i + "/" + names.get(c), shapes.get(c), map.containsKey(names.get(c)), map.get(names.get(c))));
					}
					rows.add(new Row(i + 1, cells));
				}
				List<Column> columns = new ArrayList<>();
				for (int c = 0; c < names.size(); c++) {
					columns.add(column(owner, names.get(c), columnLabel(shapes.get(c), rows, c)));
				}
				out.add(new Block(path, title, type, true, List.of(), columns, rows, newItem));
				return;
			}
			List<Row> rows = new ArrayList<>();
			for (int i = 0; i < list.size(); i++) {
				rows.add(new Row(i + 1, List.of(cell(path + "/" + i, shape, true, list.get(i)))));
			}
			out.add(new Block(path, title, type, true, List.of(), List.of(new Column("값", "", "", columnLabel(shape, rows, 0))), rows, newItem));
		}

		/** 열의 종류. 타입을 모르면 값이 있는 첫 칸의 종류 */
		private static String columnLabel(Shape shape, List<Row> rows, int column) {
			if (!(shape instanceof Untyped)) {
				return label(shape, null);
			}
			return rows.stream()
				.map((r) -> r.cells().get(column))
				.filter((i) -> i.present() && !i.control().equals("fixed") && !i.kind().equals("null"))
				.findFirst()
				.map((i) -> WorkbenchTerms.KINDS.getOrDefault(i.kind(), i.kind()))
				.orElse("");
		}

		/** 그리드의 칸 하나. 칸 안의 객체나 목록은 고치지 않고 요약만 보인다 */
		private Input cell(String path, Shape shape, boolean present, Object value) {
			if (!present) {
				return absent(path, shape);
			}
			if (value instanceof List<?> list) {
				return new Input(id(), path, "목록 " + list.size() + "개", "text", "fixed", List.of(), false, true, "");
			}
			if (value instanceof Map<?, ?>) {
				return new Input(id(), path, "객체", "text", "fixed", List.of(), false, true, "");
			}
			return input(path, fit(shape, value));
		}

		private Input absent(String path, Shape shape) {
			String kind = (shape instanceof Scalar s) ? s.kind() : "text";
			return new Input(id(), path, "", kind, "absent", List.of(), false, false, "");
		}

		private Input input(String path, Shape shape) {
			FixtureEditor.Field field = this.fields.get(path);
			if (field == null) {
				return new Input(id(), path, "", "text", "fixed", List.of(), false, true, "");
			}
			String kind = (shape instanceof Scalar s) ? s.kind() : field.kind();
			List<String> options = (shape instanceof Scalar s) ? s.options() : List.of();
			boolean isNull = field.kind().equals("null");
			if (!field.editable()) {
				return new Input(id(), path, field.value(), kind, "fixed", options, false, true, "");
			}
			String shown = this.draft.getOrDefault(path, isNull ? "" : field.value());
			boolean empty = shown.isEmpty();
			String control = switch (kind) {
				case "bool" -> (shown.equals("true") || shown.equals("false")) ? "checkbox" : "text";
				case "int" -> (empty || INT.matcher(shown).matches()) ? "number" : "text";
				case "number" -> (empty || NUMBER.matcher(shown).matches()) ? "number" : "text";
				case "date" -> (empty || DATE.matcher(shown).matches()) ? "date" : "text";
				case "datetime" -> (empty || DATETIME.matcher(shown).matches()) ? "datetime-local" : "text";
				case "time" -> (empty || TIME.matcher(shown).matches()) ? "time" : "text";
				case "enum" -> options.contains(shown) ? "select" : "text";
				default -> "text";
			};
			return new Input(id(), path, shown, kind, control, options, true, true, isNull ? "null" : "");
		}

		private String id() {
			return "fixture-" + (this.next++);
		}

		/** 표의 왼쪽 열이나 그리드 머리에 보일 종류 */
		private static String label(Shape shape, Input input) {
			return switch (shape) {
				case Scalar s -> s.label();
				case ObjectOf o -> simpleName(o.type());
				case ListOf l -> typeLabel(l);
				case Untyped u -> (input == null || input.kind().equals("null")) ? ""
						: WorkbenchTerms.KINDS.getOrDefault(input.kind(), input.kind());
			};
		}
	}
}
