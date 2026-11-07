package io.salvia95.cozy.cli.internal.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.salvia95.cozy.cli.internal.CliException;

/** 레시피가 쓰는 편집기와 이름 규칙: 알아보는 형식만 바꾸고, 나머지는 추측하지 않고 실패한다 (ADR-0032 §2) */
class EditsTest {

	static final String TAIL = "package it.cozy.app.board;\n\nimport org.springframework.modulith.ApplicationModule;\n";

	@Nested
	class PackageInfoDependencies {

		@Test
		void emptyArrayBecomesAnArrayOfOne() {
			String source = "/** 게시판. */\n@ApplicationModule(allowedDependencies = {})\n" + TAIL;
			assertThat(PackageInfos.addDependencies("p", source, List.of("memo :: spi")))
				.isEqualTo("/** 게시판. */\n@ApplicationModule(allowedDependencies = { \"memo :: spi\" })\n" + TAIL);
		}

		@Test
		void singleStringAndArrayKeepWhatIsThere() {
			String single = "@ApplicationModule(allowedDependencies = \"memo :: spi\")\n" + TAIL;
			assertThat(PackageInfos.addDependencies("p", single, List.of("activity :: spi")))
				.startsWith("@ApplicationModule(allowedDependencies = { \"memo :: spi\", \"activity :: spi\" })\n");
			String array = "@ApplicationModule(\n\tallowedDependencies = { \"memo :: spi\" , \"activity::spi\" }\n)\n" + TAIL;
			assertThat(PackageInfos.addDependencies("p", array, List.of("activity :: spi", "memo :: spi"))).isSameAs(array);
		}

		@Test
		void aWholeModuleDependencyAlreadyCoversItsSpi() {
			String source = "@ApplicationModule(allowedDependencies = \"memo\")\n" + TAIL;
			assertThat(PackageInfos.addDependencies("p", source, List.of("memo :: spi"))).isSameAs(source);
		}

		@Test
		void missingAttributeIsAdded() {
			assertThat(PackageInfos.addDependencies("p", "@ApplicationModule\n" + TAIL, List.of("memo :: spi")))
				.startsWith("@ApplicationModule(allowedDependencies = { \"memo :: spi\" })\n");
			assertThat(PackageInfos.addDependencies("p", "@ApplicationModule(displayName = \"게시판\")\n" + TAIL,
					List.of("memo :: spi")))
				.startsWith("@ApplicationModule(displayName = \"게시판\", allowedDependencies = { \"memo :: spi\" })\n");
		}

		@Test
		void openModulesAndComputedValuesAreNotGuessed() {
			assertThatThrownBy(() -> PackageInfos.addDependencies("p", TAIL, List.of("memo :: spi")))
				.isInstanceOf(CliException.class)
				.hasMessageContaining("@ApplicationModule이 없습니다");
			assertThatThrownBy(() -> PackageInfos.addDependencies("p",
					"@ApplicationModule(allowedDependencies = { Deps.MEMO })\n" + TAIL, List.of("activity :: spi")))
				.hasMessageContaining("allowedDependencies를 읽을 수 없습니다");
			assertThatThrownBy(() -> PackageInfos.addDependencies("p", "@ApplicationModule(allowedDependencies = {\n" + TAIL,
					List.of("memo :: spi")))
				.hasMessageContaining("Java 문법 오류");
		}
	}

	@Nested
	class Manifest {

		static final String ENTRY = "- id: memo/card\n  template: memo/spi/card\n  signature: memoCard(item)\n  purpose: 카드.\n";

		@Test
		void createsTheFileOrFillsAnEmptyList() {
			assertThat(ManifestEdits.append(null, "memo/card", "memo/spi/card", "memoCard(item)", ENTRY))
				.isEqualTo("# 앱 fragment 매니페스트 (docs/conventions/ui.md)\nfragments:\n  - id: memo/card\n"
						+ "    template: memo/spi/card\n    signature: memoCard(item)\n    purpose: 카드.\n");
			String empty = "# 머리 주석\nfragments: []\n";
			assertThat(ManifestEdits.append(empty, "memo/card", "memo/spi/card", "memoCard(item)", ENTRY))
				.isEqualTo("# 머리 주석\nfragments:\n  - id: memo/card\n    template: memo/spi/card\n    signature: memoCard(item)\n"
						+ "    purpose: 카드.\n");
		}

		@Test
		void appendsToABlockListWithItsIndent() {
			String text = "fragments:\n    - id: a/b\n      template: a/spi/b\n      signature: b()\n";
			assertThat(ManifestEdits.append(text, "memo/card", "memo/spi/card", "memoCard(item)", ENTRY))
				.isEqualTo(text + "    - id: memo/card\n      template: memo/spi/card\n      signature: memoCard(item)\n"
						+ "      purpose: 카드.\n");
		}

		@Test
		void sameEntryIsUnchangedAndADifferentOneFails() {
			String text = "fragments:\n  - id: memo/card\n    template: memo/spi/card\n    signature: memoCard(item)\n";
			assertThat(ManifestEdits.append(text, "memo/card", "memo/spi/card", "memoCard(item)", ENTRY)).isSameAs(text);
			assertThatThrownBy(() -> ManifestEdits.append(text, "memo/card", "memo/spi/card", "memoCard(item, size)", ENTRY))
				.hasMessageContaining("id가 memo/card인 항목이 이미 있고 내용이 다릅니다");
		}

		@Test
		void otherShapesAreLeftToAPerson() {
			assertThatThrownBy(() -> ManifestEdits.append("fragments: [{ id: a/b }]\n", "memo/card", "t", "s()", ENTRY))
				.hasMessageContaining("블록 목록");
			assertThatThrownBy(() -> ManifestEdits.append("fragments:\n  - id: a/b\nother: 1\n", "memo/card", "t", "s()", ENTRY))
				.hasMessageContaining("마지막 최상위 키");
			assertThatThrownBy(() -> ManifestEdits.append("other: 1\n", "memo/card", "t", "s()", ENTRY))
				.hasMessageContaining("'fragments:' 목록이 없습니다");
		}
	}

	@Nested
	class NamesAndFields {

		@Test
		void moduleNamesAreLowercaseAndNotReserved() {
			assertThat(Names.module("memo2")).isEqualTo("memo2");
			assertThatThrownBy(() -> Names.module("Memo")).isInstanceOf(CliException.class);
			assertThatThrownBy(() -> Names.module("fragments")).isInstanceOf(CliException.class);
			assertThatThrownBy(() -> Names.module("class")).isInstanceOf(CliException.class);
		}

		@Test
		void labelsCannotBreakOutOfTemplatesOrStrings() {
			assertThat(Names.label(" 할 일 ", "라벨")).isEqualTo("할 일");
			for (String bad : List.of("메모'", "a\"b", "<b>", "{x}", "$x", "a\\b", "a;b", " ")) {
				assertThatThrownBy(() -> Names.label(bad, "라벨")).as(bad).isInstanceOf(CliException.class);
			}
		}

		@Test
		void koreanParticlesFollowTheLastSyllable() {
			assertThat(Names.objectParticle("메모")).isEqualTo("메모를");
			assertThat(Names.objectParticle("할 일")).isEqualTo("할 일을");
			assertThat(Names.subjectParticle("본문")).isEqualTo("본문이");
			assertThat(Names.topicParticle("제목")).isEqualTo("제목은");
			assertThat(Names.topicParticle("내용")).isEqualTo("내용은");
			assertThat(Names.topicParticle("메모")).isEqualTo("메모는");
		}

		@Test
		void fieldsParseKindRequiredAndLabel() {
			Field title = Field.parse("title:text!:제목");
			assertThat(title.kind()).isEqualTo(Field.Kind.TEXT);
			assertThat(title.required()).isTrue();
			assertThat(title.label()).isEqualTo("제목");
			assertThat(title.sql()).isEqualTo("TEXT    NOT NULL CHECK (length(title) > 0)");
			Field due = Field.parse("dueOn:date");
			assertThat(due.column()).isEqualTo("due_on");
			assertThat(due.label()).isEqualTo("dueOn");
			assertThat(due.javaType()).isEqualTo("LocalDate");
			assertThat(Field.parse("count:long").javaSample()).isEqualTo("1L");
			assertThatThrownBy(() -> Field.parse("id:long")).isInstanceOf(CliException.class);
			assertThatThrownBy(() -> Field.parse("title:string")).isInstanceOf(CliException.class);
			assertThatThrownBy(() -> Field.parse("Title:text")).isInstanceOf(CliException.class);
		}
	}
}
