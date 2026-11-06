package io.salvia95.cozy.ui.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.salvia95.cozy.ui.internal.viewmodule.memo.MemoHandler;
import io.salvia95.cozy.ui.internal.viewmodule.renamed.RenamedHandler;

/** 컨트롤러·뷰 모듈 일치 (PRD §6.5 검사 7). */
class ViewModuleInterceptorTest {

	final ViewModuleInterceptor interceptor = new ViewModuleInterceptor(
			List.of("io.salvia95.cozy.ui.internal.viewmodule"), ViewModuleInterceptor.Mode.FAIL);

	@Test
	void viewInOwnModulePasses() {
		assertThat(this.interceptor.check(MemoHandler.class, "memo/api/list")).isNull();
		assertThat(this.interceptor.check(MemoHandler.class, "memo/api/list :: result")).isNull();
		assertThat(this.interceptor.check(MemoHandler.class, "memo/spi/summary :: memoSummary")).isNull();
	}

	@Test
	void viewMustBeScreenOrSpiFragment() {
		assertThat(this.interceptor.check(MemoHandler.class, "memo/list"))
			.startsWith("컨트롤러는 memo/api/(화면)나 memo/spi/(공개 fragment) 템플릿을 반환합니다");
		assertThat(this.interceptor.check(MemoHandler.class, "memo/internal/row :: row")).isNotNull();
	}

	@Test
	void viewOfOtherModuleFails() {
		assertThat(this.interceptor.check(MemoHandler.class, "wiki/api/list"))
			.startsWith("memo 모듈의 컨트롤러는 memo/로 시작하는 뷰를 반환합니다")
			.contains("컨트롤러를 wiki 모듈로");
		assertThat(this.interceptor.check(MemoHandler.class, "list")).isNotNull();
	}

	@Test
	void renamedModuleUsesApplicationModuleId() {
		assertThat(this.interceptor.check(RenamedHandler.class, "board/api/list")).isNull();
		assertThat(this.interceptor.check(RenamedHandler.class, "renamed/api/list")).isNotNull();
	}

	@Test
	void frameworkControllersAreIgnored() {
		assertThat(this.interceptor.check(CatalogController.class, "cozy/dev/catalog")).isNull();
	}
}
