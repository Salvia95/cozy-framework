package io.salvia95.cozy.ui.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** 화면 스펙 형식 (PRD §10). default, empty가 없으면 실패한다. */
class ScreenSpecTest {

	static ScreenSpec parse(String yaml, String fileName) {
		return ScreenSpec.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), fileName, "specs/" + fileName);
	}

	static final String VALID = """
			screen: memo-list
			template: memo/api/list
			fragments: [result]
			model:
			  result: x.Result
			states:
			  default: { result: { total: 1 } }
			  empty: { result: { total: 0 } }
			  invalid:
			    result: {}
			    _errors: { result: { total: 음수입니다 } }
			""";

	@Test
	void validSpec() {
		ScreenSpec spec = parse(VALID, "memo-list.yml");
		assertThat(spec.fragments()).containsExactly("result");
		assertThat(spec.model()).containsEntry("result", "x.Result");
		assertThat(spec.states()).containsKeys("default", "empty", "invalid");
	}

	@Test
	void defaultAndEmptyAreRequired() {
		assertThatIllegalStateException().isThrownBy(() -> parse(VALID.replace("  empty:", "  other:"), "memo-list.yml"))
			.withMessageContaining("'empty'가 없습니다");
	}

	@Test
	void screenMustMatchTemplateAndFileName() {
		assertThatIllegalStateException().isThrownBy(() -> parse(VALID.replace("screen: memo-list", "screen: memos"), "memos.yml"))
			.withMessageContaining("'memo-list'여야 합니다");
		assertThatIllegalStateException().isThrownBy(() -> parse(VALID, "list.yml"))
			.withMessageContaining("memo-list.yml이어야 합니다");
	}

	@Test
	void screenNameDropsApiFolderAndScreensLiveInApi() {
		ScreenSpec nested = parse(VALID.replace("screen: memo-list", "screen: memo-admin-list")
			.replace("template: memo/api/list", "template: memo/api/admin/list"), "memo-admin-list.yml");
		assertThat(nested.template()).isEqualTo("memo/api/admin/list");
		assertThat(ScreenSpec.screenName("memo/api/list")).isEqualTo("memo-list");
		assertThatIllegalStateException().isThrownBy(() -> parse(VALID.replace("template: memo/api/list", "template: memo/list"), "memo-list.yml"))
			.withMessageContaining("화면 템플릿은 {모듈}/api/ 아래에 있어야 합니다");
		assertThatIllegalStateException().isThrownBy(() -> parse(VALID.replace("template: memo/api/list", "template: memo/spi/list"), "memo-list.yml"))
			.withMessageContaining("화면 템플릿은 {모듈}/api/ 아래에 있어야 합니다");
	}

	@Test
	void everyStateGivesEveryModelAndNothingElse() {
		assertThatIllegalStateException().isThrownBy(() -> parse(VALID.replace("  empty: { result: { total: 0 } }", "  empty: {}"), "memo-list.yml"))
			.withMessageContaining("상태 'empty'에 모델 'result'의 값이 없습니다");
		assertThatIllegalStateException().isThrownBy(() -> parse(VALID.replace("  empty: { result: { total: 0 } }", "  empty: { result: {}, extra: 1 }"), "memo-list.yml"))
			.withMessageContaining("'extra'가 model에 선언되어 있지 않습니다");
	}
}
