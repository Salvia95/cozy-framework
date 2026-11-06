package io.salvia95.cozy.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** 아이콘 이름 목록은 jar의 스프라이트에서 읽는다 (ADR-0030). */
class CozyIconsTest {

	@Test
	void namesComeFromTheSprites() {
		assertThat(CozyIcons.outline()).contains("plus", "notes", "layout-dashboard").hasSize(5202);
		assertThat(CozyIcons.filled()).contains("star", "pin").hasSize(1054);
	}

	@Test
	void iconsWhoseIdClashesWithTheSpriteRootIsExcluded() {
		// <symbol id="tabler-icons">는 루트 <svg id="tabler-icons">에 가려 그려지지 않는다
		assertThat(CozyIcons.outline()).doesNotContain("icons");
	}

	@Test
	void unknownNameFailsWithAHint() {
		assertThat(CozyIcons.requireOutline("plus")).isEqualTo("plus");
		assertThatIllegalArgumentException().isThrownBy(() -> CozyIcons.requireOutline("pluss"))
			.withMessageContaining("pluss")
			.withMessageContaining("tabler.io/icons");
		assertThatIllegalArgumentException().isThrownBy(() -> new CozyNavItem("메모", "/memo", 10, "nope"))
			.withMessageContaining("nope");
	}
}
