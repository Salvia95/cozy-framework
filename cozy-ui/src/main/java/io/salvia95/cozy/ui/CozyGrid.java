package io.salvia95.cozy.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

/**
 * 입력 그리드 (inputGrid 컴포넌트 입력). 열마다 입력 방식을 정하고, 행마다 칸의 입력 이름·값·오류를 담는다.
 *
 * <p>Spring 폼의 목록 속성이면 {@link #bind}로 만든다. 행 추가·복제·삭제 버튼은 감싼 폼을 {@code row} 파라미터로 제출하고,
 * 컨트롤러는 {@link RowAction}으로 목록을 고친다.
 *
 * @param key 그리드 이름 (행 작업 값에 들어간다. Spring 폼이면 목록 속성 이름)
 * @param columns 열
 * @param rows 행
 * @param editableRows 행 추가·복제·삭제 버튼을 보이는지
 */
public record CozyGrid(String key, List<Column> columns, List<Row> rows, boolean editableRows) {

	/** 열이 받을 수 있는 입력 방식 */
	public static final List<String> INPUTS = List.of("text", "number", "date", "email", "select", "radio", "checkbox");

	private static final Set<String> CHOICES = Set.of("select", "radio");

	public CozyGrid {
		if (key == null || !key.matches("[A-Za-z0-9_.\\[\\]-]+")) {
			throw new IllegalArgumentException("[cozy] CozyGrid의 이름이 비었거나 쓸 수 없는 글자가 있습니다: " + key);
		}
		columns = List.copyOf(columns);
		rows = List.copyOf(rows);
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).cells().size() != columns.size()) {
				throw new IllegalArgumentException("[cozy] CozyGrid '" + key + "'의 " + (i + 1) + "행은 칸이 "
						+ rows.get(i).cells().size() + "개이고 열은 " + columns.size() + "개입니다");
			}
		}
	}

	/**
	 * Spring 폼의 목록 속성으로 그리드를 만든다. 칸의 이름은 {@code 속성[행].열}이고, 값과 오류는 바인딩 결과에서 읽는다.
	 *
	 * @param property 폼의 목록 속성 이름 (그리드 이름이 된다)
	 * @param columns 열
	 * @param rows 목록 속성의 값 (빈 생성자와 getter·setter가 있는 행 객체, 또는 Map)
	 * @param errors 폼의 바인딩 결과. 없으면 {@code null}
	 */
	public static CozyGrid bind(String property, List<Column> columns, List<?> rows, BindingResult errors) {
		List<Row> built = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			Object row = rows.get(i);
			List<Cell> cells = new ArrayList<>();
			for (Column column : columns) {
				String name = property + "[" + i + "]." + column.name();
				Object value = (errors != null) ? errors.getFieldValue(name) : read(row, column.name());
				FieldError error = (errors != null) ? errors.getFieldError(name) : null;
				cells.add(new Cell(name, (value == null) ? "" : String.valueOf(value),
						(error == null) ? null : error.getDefaultMessage()));
			}
			built.add(new Row(cells));
		}
		return new CozyGrid(property, columns, built, true);
	}

	private static Object read(Object row, String property) {
		if (row == null) {
			return null;
		}
		if (row instanceof Map<?, ?> map) {
			return map.get(property);
		}
		return new BeanWrapperImpl(row).getPropertyValue(property);
	}

	/**
	 * 열 하나.
	 *
	 * @param name 이름 (행 객체의 속성 이름)
	 * @param label 머리 (화면에 보이는 열 이름)
	 * @param input 입력 방식 (text, number, date, email, select, radio, checkbox)
	 * @param required 필수 (머리에 * 표시. 검사는 서버가 한다)
	 * @param options 선택지 (select, radio만)
	 */
	public record Column(String name, String label, String input, boolean required, CozyOptions options) {

		public Column {
			if (name == null || name.isBlank() || label == null || label.isBlank()) {
				throw new IllegalArgumentException("[cozy] 그리드 열의 이름과 머리는 비울 수 없습니다: " + name + ", " + label);
			}
			if (!INPUTS.contains(input)) {
				throw new IllegalArgumentException("[cozy] 그리드 열 '" + name + "'의 입력 방식 '" + input + "'은 쓸 수 없습니다. "
						+ "text, number, date, email, select, radio, checkbox 가운데 하나입니다");
			}
			if (CHOICES.contains(input) != (options != null)) {
				throw new IllegalArgumentException("[cozy] 그리드 열 '" + name + "': 선택지는 select와 radio 열에만 있고, 그 두 열에는 꼭 있어야 합니다");
			}
		}

		public static Column text(String name, String label) {
			return new Column(name, label, "text", false, null);
		}

		public static Column number(String name, String label) {
			return new Column(name, label, "number", false, null);
		}

		public static Column date(String name, String label) {
			return new Column(name, label, "date", false, null);
		}

		public static Column email(String name, String label) {
			return new Column(name, label, "email", false, null);
		}

		public static Column select(String name, String label, CozyOptions options) {
			return new Column(name, label, "select", false, options);
		}

		public static Column radio(String name, String label, CozyOptions options) {
			return new Column(name, label, "radio", false, options);
		}

		public static Column checkbox(String name, String label) {
			return new Column(name, label, "checkbox", false, null);
		}

		/** 필수로 표시한 같은 열 */
		public Column asRequired() {
			return new Column(this.name, this.label, this.input, true, this.options);
		}
	}

	/**
	 * 행 하나.
	 *
	 * @param cells 칸 (열 순서)
	 */
	public record Row(List<Cell> cells) {

		public Row {
			cells = List.copyOf(cells);
		}
	}

	/**
	 * 칸 하나.
	 *
	 * @param name 입력 이름 (폼에 실려 갈 파라미터 이름)
	 * @param value 값 (체크박스는 true나 false)
	 * @param error 오류 문장. 없으면 null
	 */
	public record Cell(String name, String value, String error) {

		public Cell {
			Objects.requireNonNull(name, "name");
			value = (value == null) ? "" : value;
		}
	}

	/**
	 * 행 작업 버튼이 제출한 {@code row} 값: {@code add:{그리드}}, {@code duplicate:{그리드}/{행}}, {@code delete:{그리드}/{행}}. 행은 0부터다.
	 *
	 * <pre>{@code
	 * @PostMapping(params = "row")
	 * String row(@RequestParam String row, @ModelAttribute("form") MemoForm form) {
	 *     CozyGrid.RowAction.parse(row).ifPresent((a) -> a.apply("items", form.getItems(), ItemRow::new, ItemRow::copy));
	 *     return "memo/api/form";
	 * }
	 * }</pre>
	 *
	 * @param op add, duplicate, delete
	 * @param key 그리드 이름
	 * @param index 행 (add는 -1)
	 */
	public record RowAction(String op, String key, int index) {

		private static final Pattern FORMAT = Pattern.compile("(add):([^/]+)|(duplicate|delete):([^/]+)/(\\d+)");

		/** 값이 없으면 비어 있고, 형식이 다르면 예외다. */
		public static Optional<RowAction> parse(String value) {
			if (value == null) {
				return Optional.empty();
			}
			Matcher matcher = FORMAT.matcher(value);
			if (!matcher.matches()) {
				throw new IllegalArgumentException("[cozy] 행 작업 값 '" + value
						+ "'의 형식이 다릅니다. add:{그리드}, duplicate:{그리드}/{행}, delete:{그리드}/{행} 가운데 하나입니다");
			}
			if (matcher.group(1) != null) {
				return Optional.of(new RowAction("add", matcher.group(2), -1));
			}
			return Optional.of(new RowAction(matcher.group(3), matcher.group(4), Integer.parseInt(matcher.group(5))));
		}

		/**
		 * 이 작업이 그 그리드의 것이면 목록을 고친다. 다른 그리드의 작업이면 그대로 둔다.
		 *
		 * @param grid 그리드 이름
		 * @param rows 고칠 목록 (바뀌는 목록)
		 * @param blank 추가할 빈 행
		 * @param copy 복제할 행의 사본
		 */
		public <T> void apply(String grid, List<T> rows, Supplier<T> blank, UnaryOperator<T> copy) {
			if (!this.key.equals(grid)) {
				return;
			}
			if (this.op.equals("add")) {
				rows.add(blank.get());
				return;
			}
			if (this.index >= rows.size()) {
				throw new IllegalArgumentException("[cozy] 그리드 '" + grid + "'에 " + (this.index + 1) + "행이 없습니다 (행 " + rows.size() + "개)");
			}
			if (this.op.equals("duplicate")) {
				rows.add(this.index + 1, copy.apply(rows.get(this.index)));
			}
			else {
				rows.remove(this.index);
			}
		}
	}
}
