package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 화면 DTO의 속성 설명을 소스의 Javadoc에서 읽는다. record는 {@code @param}, 폼 클래스는 필드 위의 Javadoc이다.
 */
class SourceDocsTest {

	/** 이름이 같은 중첩 record를 찾기 위한 자리 */
	static final class Views {

		record Row(long id, String title) {
		}
	}

	static class Form {
	}

	static final String VIEWS = """
			package x;

			/** 메모 화면 DTO */
			public final class Views {

				/**
				 * 목록 한 행
				 *
				 * @param id 번호
				 * @param title 제목. 100자까지
				 * @param at 언제 (yyyy-MM-dd
				 *     HH:mm)
				 */
				@Deprecated
				public record Row(long id, String title, String at) {

					// } 주석 안의 괄호는 세지 않는다
					public static Row of() {
						return null;
					}
				}
			}
			""";

	static final String FORM = """
			package x;

			public class Form {

				/** 제목 */
				@NotBlank(message = "제목을 입력하세요")
				@Size(max = 100, message = "제목은 100자까지 쓸 수 있습니다")
				private String title;

				/**
				 * 본문. 여러 줄을 쓴다.
				 */
				private String body;

				private boolean pinned;

				/** 게터의 설명은 속성이 아니다 */
				public String getTitle() {
					return this.title;
				}

				static class Inner {

					/** 안쪽 클래스의 필드 */
					private String title;
				}
			}
			""";

	@Test
	void aRecordsPropertiesAreItsParamTags() {
		Map<String, String> docs = SourceDocs.of(VIEWS, Views.Row.class);
		assertThat(docs).containsExactly(Map.entry("id", "번호"), Map.entry("title", "제목. 100자까지"),
				Map.entry("at", "언제 (yyyy-MM-dd HH:mm)"));
		assertThat(SourceDocs.shortName(docs.get("at"))).isEqualTo("언제");
		assertThat(SourceDocs.shortName(docs.get("title"))).isEqualTo("제목");
		assertThat(SourceDocs.shortName("만든 시각")).isEqualTo("만든 시각");
	}

	@Test
	void aFormClassesPropertiesAreTheDocsAboveItsFields() {
		assertThat(SourceDocs.of(FORM, Form.class)).containsExactly(Map.entry("title", "제목"), Map.entry("body", "본문."));
		assertThat(SourceDocs.of("class Other {}", Form.class)).isEmpty();
	}

	@Test
	void theSourceFileOfANestedTypeIsTheOuterTypesFile() {
		assertThat(SourceDocs.file(Views.Row.class)).isEqualTo("src/main/java/io/salvia95/cozy/workbench/internal/SourceDocsTest.java");
	}

	@Test
	void frameworkNamesMatchTheFrameworkTypesDocs() throws Exception {
		Path page = Path.of("../cozy-ui/src/main/java/io/salvia95/cozy/ui/CozyPage.java");
		Map<String, String> docs = SourceDocs.of(Files.readString(page), io.salvia95.cozy.ui.CozyPage.class);
		Map<String, String> names = new java.util.LinkedHashMap<>();
		docs.forEach((name, description) -> names.put(name, SourceDocs.shortName(description)));
		assertThat(SourceDocs.FRAMEWORK.get("io.salvia95.cozy.ui.CozyPage")).isEqualTo(names);
		Path options = Path.of("../cozy-ui/src/main/java/io/salvia95/cozy/ui/CozyOptions.java");
		for (Class<?> type : java.util.List.of(io.salvia95.cozy.ui.CozyOptions.class, io.salvia95.cozy.ui.CozyOptions.Option.class)) {
			Map<String, String> optionNames = new java.util.LinkedHashMap<>();
			SourceDocs.of(Files.readString(options), type)
				.forEach((name, description) -> optionNames.put(name, SourceDocs.shortName(description)));
			assertThat(optionNames).as(type.getName()).isNotEmpty();
			assertThat(SourceDocs.FRAMEWORK.get(type.getName())).as(type.getName()).isEqualTo(optionNames);
		}
	}
}
