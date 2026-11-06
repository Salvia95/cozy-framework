package io.salvia95.cozy.runtime.internal.dist;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 패키징의 순수 함수 부분 (PRD §9.6). 실제 jpackage·훈련 실행은 samples/dist-e2e가 검증한다.
 */
class CozyPackagerTest {

	@Test
	void javaOptionsAreAppendedToTheJavaOptionsSection() {
		String cfg = "[Application]\napp.mainjar=$APPDIR/app.jar\n\n[JavaOptions]\njava-options=-Xmx512m\n\n[ArgOptions]\narguments=x\n";
		String result = CozyPackager.withJavaOptions(cfg, "-XX:AOTCache=$APPDIR/voc.aot");
		assertThat(result).contains("java-options=-Xmx512m\n\njava-options=-XX:AOTCache=$APPDIR/voc.aot\n[ArgOptions]");
		assertThat(CozyPackager.withJavaOptions("[Application]\n", "-Xmx1g"))
			.isEqualTo("[Application]\n[JavaOptions]\njava-options=-Xmx1g\n");
		assertThat(CozyPackager.withJavaOptions("[JavaOptions]\njava-options=-Xmx512m", "-A", "-B"))
			.isEqualTo("[JavaOptions]\njava-options=-Xmx512m\njava-options=-A\njava-options=-B\n");
	}

	@Test
	void javaOptionsPropertyIsSplitOnWhitespace() {
		assertThat(CozyPackager.splitOptions(" -Xmx512m  -Dfile.encoding=UTF-8 ")).containsExactly("-Xmx512m",
				"-Dfile.encoding=UTF-8");
		assertThat(CozyPackager.splitOptions("")).isEmpty();
	}

	@Test
	void sqliteNativeLibraryMatchesPlatform() {
		assertThat(new CozyPackager.Platform("win", "x64").sqliteNativeEntry())
			.isEqualTo("org/sqlite/native/Windows/x86_64/sqlitejdbc.dll");
		assertThat(new CozyPackager.Platform("linux", "arm64").sqliteNativeEntry())
			.isEqualTo("org/sqlite/native/Linux/aarch64/libsqlitejdbc.so");
	}
}
