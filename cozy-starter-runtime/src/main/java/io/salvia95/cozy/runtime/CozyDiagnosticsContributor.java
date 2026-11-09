package io.salvia95.cozy.runtime;

/**
 * 진단 파일에 내용을 더한다. 이 타입의 빈마다 zip에 텍스트 파일 하나가 생긴다.
 *
 * <pre>{@code
 * @Bean
 * CozyDiagnosticsContributor myDiagnostics(MyRepository repository) {
 *     return CozyDiagnosticsContributor.of("my-tool.txt", () -> "대기 작업: " + repository.countPending());
 * }
 * }</pre>
 *
 * 비밀 값(토큰, 비밀번호, 개인 정보)을 넣지 않는다. 내용을 만들다 예외가 나면 그 파일에는 오류 메시지만 남고
 * 나머지 진단 파일은 정상적으로 만들어진다. 문서: docs/conventions/runtime.md
 */
public interface CozyDiagnosticsContributor {

	/** zip 안의 파일 이름. 영문 소문자·숫자·하이픈과 확장자 (예: {@code flyway.txt}). */
	String fileName();

	/** 파일 내용 */
	String content() throws Exception;

	static CozyDiagnosticsContributor of(String fileName, ContentSupplier content) {
		return new CozyDiagnosticsContributor() {
			@Override
			public String fileName() {
				return fileName;
			}

			@Override
			public String content() throws Exception {
				return content.get();
			}
		};
	}

	/** 예외를 던질 수 있는 내용 공급자 */
	@FunctionalInterface
	interface ContentSupplier {

		String get() throws Exception;

	}
}
