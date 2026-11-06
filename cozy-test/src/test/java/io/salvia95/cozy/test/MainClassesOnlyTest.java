package io.salvia95.cozy.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 테스트 클래스 판별은 경로의 마지막 클래스 폴더로 한다 (아키타입 통합 테스트처럼 test-classes 아래에 있는 프로젝트). */
class MainClassesOnlyTest {

	@Test
	void lastClassesFolderDecides() {
		assertThat(MainClassesOnly.isTestLocation("file:/app/target/classes/com/x/App.class")).isFalse();
		assertThat(MainClassesOnly.isTestLocation("file:/app/target/test-classes/com/x/AppTest.class")).isTrue();
		assertThat(MainClassesOnly.isTestLocation(
				"file:/fw/cozy-archetype/target/test-classes/projects/basic/project/demo/target/classes/com/x/App.class"))
			.isFalse();
		assertThat(MainClassesOnly.isTestLocation(
				"file:/fw/cozy-archetype/target/test-classes/projects/basic/project/demo/target/test-classes/com/x/T.class"))
			.isTrue();
		assertThat(MainClassesOnly.isTestLocation("jar:file:/repo/cozy-ui.jar!/io/salvia95/X.class")).isFalse();
		assertThat(MainClassesOnly.isTestLocation("file:C:\\app\\target\\test-classes\\com\\x\\T.class")).isTrue();
	}
}
