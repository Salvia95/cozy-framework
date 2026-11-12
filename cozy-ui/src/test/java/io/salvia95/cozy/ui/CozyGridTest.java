package io.salvia95.cozy.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.validation.BeanPropertyBindingResult;

import io.salvia95.cozy.ui.CozyGrid.Column;
import io.salvia95.cozy.ui.CozyGrid.RowAction;

class CozyGridTest {

	static final CozyOptions KINDS = CozyOptions.of(Map.of("text", "글자"));

	public static class ItemRow {

		private String title;

		private String kind;

		private boolean required;

		public ItemRow() {
		}

		ItemRow(String title, String kind, boolean required) {
			this.title = title;
			this.kind = kind;
			this.required = required;
		}

		public String getTitle() {
			return this.title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getKind() {
			return this.kind;
		}

		public void setKind(String kind) {
			this.kind = kind;
		}

		public boolean isRequired() {
			return this.required;
		}

		public void setRequired(boolean required) {
			this.required = required;
		}

		ItemRow copy() {
			return new ItemRow(this.title, this.kind, this.required);
		}
	}

	public static class ItemsForm {

		private List<ItemRow> items = new ArrayList<>();

		public List<ItemRow> getItems() {
			return this.items;
		}

		public void setItems(List<ItemRow> items) {
			this.items = items;
		}
	}

	@Test
	void columnsDeclareTheirInput() {
		assertThat(Column.text("title", "이름").asRequired()).isEqualTo(new Column("title", "이름", "text", true, null));
		assertThat(Column.select("kind", "종류", KINDS).input()).isEqualTo("select");
		assertThat(Column.radio("kind", "종류", KINDS).options()).isEqualTo(KINDS);
		assertThat(Column.checkbox("required", "필수").input()).isEqualTo("checkbox");
		assertThat(List.of(Column.number("n", "수"), Column.date("d", "날짜"), Column.email("e", "메일")))
			.extracting(Column::input).containsExactly("number", "date", "email");
	}

	@Test
	void choiceColumnsNeedOptionsAndOthersRefuseThem() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Column("kind", "종류", "select", false, null))
			.withMessageContaining("선택지");
		assertThatIllegalArgumentException().isThrownBy(() -> new Column("title", "이름", "text", false, KINDS))
			.withMessageContaining("선택지");
		assertThatIllegalArgumentException().isThrownBy(() -> new Column("title", "이름", "color", false, null))
			.withMessageContaining("text, number, date, email, select, radio, checkbox");
	}

	@Test
	void rowsMustHaveOneCellPerColumn() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new CozyGrid("items", List.of(Column.text("title", "이름")),
					List.of(new CozyGrid.Row(List.of())), true))
			.withMessageContaining("1행");
	}

	@Test
	void bindReadsSpringIndexedPropertiesAndErrors() {
		ItemsForm form = new ItemsForm();
		form.getItems().add(new ItemRow("회의록", "text", true));
		form.getItems().add(new ItemRow(null, null, false));
		BeanPropertyBindingResult errors = new BeanPropertyBindingResult(form, "form");
		errors.rejectValue("items[1].title", "NotBlank", "이름을 입력하세요");
		CozyGrid grid = CozyGrid.bind("items",
				List.of(Column.text("title", "이름").asRequired(), Column.select("kind", "종류", KINDS),
						Column.checkbox("required", "필수")),
				form.getItems(), errors);
		assertThat(grid.key()).isEqualTo("items");
		assertThat(grid.editableRows()).isTrue();
		assertThat(grid.rows().get(0).cells()).containsExactly(new CozyGrid.Cell("items[0].title", "회의록", null),
				new CozyGrid.Cell("items[0].kind", "text", null), new CozyGrid.Cell("items[0].required", "true", null));
		assertThat(grid.rows().get(1).cells().get(0)).isEqualTo(new CozyGrid.Cell("items[1].title", "", "이름을 입력하세요"));
		assertThat(grid.rows().get(1).cells().get(2).value()).isEqualTo("false");
	}

	@Test
	void bindAcceptsMapRowsAndNoErrors() {
		CozyGrid grid = CozyGrid.bind("rows", List.of(Column.text("title", "이름")), List.of(Map.of("title", "가")), null);
		assertThat(grid.rows().get(0).cells()).containsExactly(new CozyGrid.Cell("rows[0].title", "가", null));
	}

	@Test
	void rowActionsChangeOnlyTheirOwnGrid() {
		List<ItemRow> items = new ArrayList<>(List.of(new ItemRow("가", "text", true), new ItemRow("나", null, false)));
		assertThat(RowAction.parse("duplicate:items/0")).hasValue(new RowAction("duplicate", "items", 0));
		RowAction.parse("duplicate:items/0").orElseThrow().apply("items", items, ItemRow::new, ItemRow::copy);
		assertThat(items).extracting(ItemRow::getTitle).containsExactly("가", "가", "나");
		RowAction.parse("delete:items/2").orElseThrow().apply("items", items, ItemRow::new, ItemRow::copy);
		assertThat(items).extracting(ItemRow::getTitle).containsExactly("가", "가");
		RowAction.parse("add:items").orElseThrow().apply("items", items, ItemRow::new, ItemRow::copy);
		assertThat(items).hasSize(3).last().extracting(ItemRow::getTitle).isNull();
		RowAction.parse("add:other").orElseThrow().apply("items", items, ItemRow::new, ItemRow::copy);
		assertThat(items).as("다른 그리드의 동작").hasSize(3);
	}

	@Test
	void malformedRowActionsAreRefused() {
		assertThat(RowAction.parse(null)).isEmpty();
		for (String bad : List.of("", "move:items/0", "delete:items", "add:items/0", "delete:items/x", "delete:/0")) {
			assertThatIllegalArgumentException().as(bad).isThrownBy(() -> RowAction.parse(bad));
		}
		List<ItemRow> items = new ArrayList<>(List.of(new ItemRow("가", null, false)));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RowAction("delete", "items", 3).apply("items", items, ItemRow::new, ItemRow::copy))
			.withMessageContaining("4행");
	}
}
