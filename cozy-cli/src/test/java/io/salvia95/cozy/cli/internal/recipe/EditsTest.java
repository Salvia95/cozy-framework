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

	/** 2단계 편집기 (ADR-0033 §2·§3): javac와 XML 파서가 찾은 위치에 덧붙이고, 알아볼 수 없으면 실패한다 */
	@Nested
	class JavaMembersAndImports {

		static final String FINDER = """
				package it.cozy.app.activity.spi;

				import java.util.List;

				import it.cozy.app.activity.spi.dto.ActivityItem;

				/** 활동 읽기. */
				public interface ActivityFinder {

					List<ActivityItem> recent(int limit);
				}
				""";

		@Test
		void aMemberGoesBeforeTheLastBraceOfItsType() {
			assertThat(JavaEdits.addMember("f", FINDER, "ActivityFinder", "\tlong count();")).endsWith("""
						List<ActivityItem> recent(int limit);

						long count();
					}
					""");
			String empty = "package a;\n\npublic interface AFacade {\n}\n";
			assertThat(JavaEdits.addMember("f", empty, "AFacade", "\tvoid record(String message);"))
				.isEqualTo("package a;\n\npublic interface AFacade {\n\n\tvoid record(String message);\n}\n");
		}

		@Test
		void aMemberOfANestedTypeIsNotTheTopLevelBodyEnd() {
			String source = "package a;\n\nclass AImpl {\n\n\trecord Inner(int x) {\n\t}\n\n\tvoid run() {\n\t\t// 본문 }\n\t}\n}\n";
			assertThat(JavaEdits.addMember("f", source, "AImpl", "\tvoid more() {\n\t}"))
				.endsWith("\t\t// 본문 }\n\t}\n\n\tvoid more() {\n\t}\n}\n");
		}

		@Test
		void aTypeThatIsNotThereOrABrokenFileFails() {
			assertThatThrownBy(() -> JavaEdits.addMember("f", FINDER, "MemoFinder", "\tlong count();"))
				.isInstanceOf(CliException.class)
				.hasMessageContaining("최상위 타입 MemoFinder를 하나로 찾지 못했습니다");
			assertThatThrownBy(() -> JavaEdits.addMember("f", FINDER.replace("limit);", "limit"), "ActivityFinder", "\tlong count();"))
				.isInstanceOf(CliException.class)
				.hasMessageContaining("Java 문법 오류");
			assertThatThrownBy(() -> JavaEdits.addMember("f", FINDER, "ActivityFinder", "\tlong count("))
				.as("덧붙인 결과도 다시 읽어 본다")
				.isInstanceOf(CliException.class)
				.hasMessageContaining("Java 문법 오류");
		}

		@Test
		void importsGoAfterTheLastImportOnlyWhenMissing() {
			assertThat(JavaEdits.addImports("f", FINDER, List.of("java.util.List", "java.util.Optional", "it.cozy.app.activity.spi.Other")))
				.contains("import it.cozy.app.activity.spi.dto.ActivityItem;\nimport java.util.Optional;\n\n/** 활동 읽기. */")
				.doesNotContain("import it.cozy.app.activity.spi.Other;");
			assertThat(JavaEdits.addImports("f", FINDER, List.of("java.util.List", "java.lang.String"))).isSameAs(FINDER);
			String wildcard = FINDER.replace("import java.util.List;", "import java.util.*;");
			assertThat(JavaEdits.addImports("f", wildcard, List.of("java.util.Optional"))).isSameAs(wildcard);
			String none = "package a;\n\npublic interface AFacade {\n}\n";
			assertThat(JavaEdits.addImports("f", none, List.of("java.time.Instant")))
				.isEqualTo("package a;\n\nimport java.time.Instant;\n\npublic interface AFacade {\n}\n");
		}

		@Test
		void theSameSimpleNameFromAnotherPackageFails() {
			assertThatThrownBy(() -> JavaEdits.addImports("f", FINDER, List.of("java.awt.List")))
				.isInstanceOf(CliException.class)
				.hasMessageContaining("이미 java.util.List를 import하고 있어 java.awt.List를 import할 수 없습니다");
		}

		@Test
		void signaturesAreComparedWithoutBodies() {
			JavaEdits.Parsed parsed = JavaEdits.parse("f", FINDER);
			assertThat(parsed.presence("ActivityFinder", new JavaEdits.Signature("recent", "List<ActivityItem>", List.of("int"))))
				.isEqualTo(JavaEdits.Presence.PRESENT);
			assertThat(parsed.presence("ActivityFinder", new JavaEdits.Signature("recent", "List< ActivityItem >", List.of("int"))))
				.as("공백은 비교하지 않는다")
				.isEqualTo(JavaEdits.Presence.PRESENT);
			assertThat(parsed.presence("ActivityFinder", new JavaEdits.Signature("recent", "List<ActivityItem>", List.of("long"))))
				.isEqualTo(JavaEdits.Presence.CONFLICT);
			assertThat(parsed.presence("ActivityFinder", new JavaEdits.Signature("count", "long", List.of())))
				.isEqualTo(JavaEdits.Presence.ABSENT);
			String impl = "package a;\n\nclass Impl {\n\n\t@Override\n\tpublic List<ActivityItem> recent(@Param(\"limit\") final int limit) {\n"
					+ "\t\treturn List.of(); // 에이전트가 채운 본문\n\t}\n}\n";
			assertThat(JavaEdits.parse("f", impl)
				.presence("Impl", new JavaEdits.Signature("recent", "List<ActivityItem>", List.of("int"))))
				.as("어노테이션, final, 본문은 시그니처가 아니다")
				.isEqualTo(JavaEdits.Presence.PRESENT);
		}
	}

	@Nested
	class MapperXml {

		static final String NS = "it.cozy.app.activity.internal.infra.ActivityMapper";

		static final String XML = """
				<?xml version="1.0" encoding="UTF-8"?>
				<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
				<mapper namespace="it.cozy.app.activity.internal.infra.ActivityMapper">

				  <insert id="insert">
				    INSERT INTO activity (message) VALUES (#{message})
				  </insert>
				</mapper>
				""";

		@Test
		void statementsAreReadWithoutFetchingTheDtd() {
			assertThat(XmlEdits.statements("x", XML, NS)).containsExactly(new XmlEdits.Statement("insert", "insert"));
		}

		@Test
		void aStatementGoesBeforeTheClosingMapper() {
			String result = XmlEdits.addStatement("x", XML, NS, "  <select id=\"recent\" resultType=\"R\">\n    SELECT 1\n  </select>");
			assertThat(result).endsWith("""
					  </insert>

					  <select id="recent" resultType="R">
					    SELECT 1
					  </select>
					</mapper>
					""");
			assertThat(XmlEdits.statements("x", result, NS)).extracting(XmlEdits.Statement::id).containsExactly("insert", "recent");
		}

		@Test
		void anotherNamespaceOrABrokenFileFails() {
			assertThatThrownBy(() -> XmlEdits.statements("x", XML, "it.cozy.app.memo.internal.infra.MemoMapper"))
				.isInstanceOf(CliException.class)
				.hasMessageContaining("namespace(" + NS + ")가 it.cozy.app.memo.internal.infra.MemoMapper가 아닙니다");
			assertThatThrownBy(() -> XmlEdits.statements("x", XML.replace("</insert>", ""), NS)).isInstanceOf(CliException.class)
				.hasMessageContaining("XML 오류");
			assertThatThrownBy(() -> XmlEdits.addStatement("x", XML.replace("</mapper>\n", "</mapper>\n<!-- 끝 -->\n"), NS, "  <select/>"))
				.isInstanceOf(CliException.class)
				.hasMessageContaining("</mapper>로 끝나지 않습니다");
			assertThatThrownBy(() -> XmlEdits.addStatement("x", XML, NS, "  <select id=\"x\">")).as("덧붙인 결과도 다시 읽어 본다")
				.isInstanceOf(CliException.class)
				.hasMessageContaining("XML 오류");
		}
	}

	@Nested
	class PomEvents {

		static final String POM = """
				<project>
				  <dependencies>
				    <dependency>
				      <groupId>io.salvia95.cozy</groupId>
				      <artifactId>cozy-ui</artifactId>
				    </dependency>
				    <!-- SQLite + MyBatis + Flyway -->
				    <dependency>
				      <groupId>io.salvia95.cozy</groupId>
				      <artifactId>cozy-starter-sqlite</artifactId>
				    </dependency>
				    <dependency>
				      <groupId>io.salvia95.cozy</groupId>
				      <artifactId>cozy-test</artifactId>
				      <scope>test</scope>
				    </dependency>
				  </dependencies>
				</project>
				""";

		@Test
		void theEventsStarterGoesRightAfterTheSqliteStarter() {
			assertThat(XmlEdits.addEventsStarter("pom.xml", POM)).contains("""
					      <artifactId>cozy-starter-sqlite</artifactId>
					    </dependency>
					    <!-- 모듈 사이 이벤트 (cozy enable events) -->
					    <dependency>
					      <groupId>io.salvia95.cozy</groupId>
					      <artifactId>cozy-starter-events</artifactId>
					    </dependency>
					    <dependency>
					      <groupId>io.salvia95.cozy</groupId>
					      <artifactId>cozy-test</artifactId>
					""");
			String oneLine = "<project><dependencies>\n  <dependency><groupId>io.salvia95.cozy</groupId><artifactId>cozy-starter-sqlite</artifactId></dependency>\n"
					+ "</dependencies></project>\n";
			assertThat(XmlEdits.addEventsStarter("pom.xml", oneLine)).contains("<artifactId>cozy-starter-sqlite</artifactId></dependency>\n"
					+ "  <dependency><groupId>io.salvia95.cozy</groupId><artifactId>cozy-starter-events</artifactId></dependency>\n");
		}

		@Test
		void alreadyEnabledChangesNothing() {
			String enabled = XmlEdits.addEventsStarter("pom.xml", POM);
			assertThat(XmlEdits.addEventsStarter("pom.xml", enabled)).isSameAs(enabled);
			String archetype = POM.replace("cozy-starter-sqlite", "cozy-starter-events");
			assertThat(XmlEdits.addEventsStarter("pom.xml", archetype)).isSameAs(archetype);
		}

		@Test
		void withoutExactlyOneSqliteStarterItFails() {
			assertThatThrownBy(() -> XmlEdits.addEventsStarter("pom.xml", POM.replace("cozy-starter-sqlite", "cozy-starter-x")))
				.isInstanceOf(CliException.class)
				.hasMessageContaining("이벤트에는 저장(persistence)이 필요합니다");
			String commented = POM.replace("    <!-- SQLite + MyBatis + Flyway -->\n",
					"    <!-- <dependency><groupId>io.salvia95.cozy</groupId><artifactId>cozy-starter-sqlite</artifactId></dependency> -->\n");
			assertThatThrownBy(() -> XmlEdits.addEventsStarter("pom.xml", commented)).as("주석 안의 블록과 헷갈리지 않는다")
				.isInstanceOf(CliException.class)
				.hasMessageContaining("cozy-starter-sqlite 의존 블록을 하나로 찾지 못했습니다");
			String managed = POM.replace("<project>\n", "<project>\n  <dependencyManagement><dependencies>\n    <dependency><groupId>"
					+ "io.salvia95.cozy</groupId><artifactId>cozy-starter-sqlite</artifactId></dependency>\n  </dependencies></dependencyManagement>\n");
			assertThatThrownBy(() -> XmlEdits.addEventsStarter("pom.xml", managed)).isInstanceOf(CliException.class)
				.hasMessageContaining("하나로 찾지 못했습니다");
		}
	}
}
