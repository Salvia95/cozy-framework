package io.salvia95.cozy.runtime.internal.dist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 제3자 라이선스 목록 검사와 반입 (PRD §11, ADR-0026) */
class ThirdPartyLicensesTest {

	static final Set<String> ALLOWED = ThirdPartyLicenses.allowedSet("Apache-2.0|MIT|EPL-2.0");

	static final String LIST = """

			Lists of 3 third-party dependencies.
			     (EPL-2.0) (LGPL-2.1-only) Logback Classic Module (ch.qos.logback:logback-classic:1.5.38 - http://logback.qos.ch/logback-classic)
			     (Apache-2.0) SQLite JDBC (org.xerial:sqlite-jdbc:3.54.1.0 - https://github.com/xerial/sqlite-jdbc)
			     (MIT) SLF4J API Module (org.slf4j:slf4j-api:2.0.20 - http://www.slf4j.org)
			""";

	@Test
	void parsesEveryLineWithAllLicenses() {
		var dependencies = ThirdPartyLicenses.parse(LIST);
		assertThat(dependencies).hasSize(3);
		assertThat(dependencies.get(0).coordinates()).isEqualTo("ch.qos.logback:logback-classic:1.5.38");
		assertThat(dependencies.get(0).licenses()).containsExactly("EPL-2.0", "LGPL-2.1-only");
		assertThat(ThirdPartyLicenses.violations(dependencies, ALLOWED)).isEmpty();
	}

	@Test
	void dependencyWithoutAllowedLicenseIsReported() {
		String list = LIST.replace("Lists of 3", "Lists of 4")
				+ "     (GPL-3.0-only) Some Lib (com.example:some-lib:1.0 - https://example.com)\n";
		assertThat(ThirdPartyLicenses.violations(ThirdPartyLicenses.parse(list), ALLOWED))
			.containsExactly("com.example:some-lib:1.0 [GPL-3.0-only]");
	}

	@Test
	void unknownLicenseAndBrokenListFailClosed() {
		String unknown = LIST.replace("(MIT) SLF4J", "(Unknown license) SLF4J");
		assertThat(ThirdPartyLicenses.violations(ThirdPartyLicenses.parse(unknown), ALLOWED)).hasSize(1);
		assertThatThrownBy(() -> ThirdPartyLicenses.parse(LIST.replace("Lists of 3", "Lists of 5")))
			.hasMessageContaining("머리말(5)");
		assertThatThrownBy(() -> ThirdPartyLicenses.parse("something else")).hasMessageContaining("형식이 아닙니다");
		assertThatThrownBy(() -> ThirdPartyLicenses.parse(LIST.replace("     (MIT) SLF4J", "     SLF4J")))
			.hasMessageContaining("읽을 수 없습니다");
		assertThat(ThirdPartyLicenses.parse("The project has no dependencies.\n")).isEmpty();
	}

	@Test
	void bundleCopiesListAndVendorLicenses(@TempDir Path dir) throws Exception {
		Path list = Files.writeString(dir.resolve("THIRD-PARTY.txt"), LIST);
		Path lib = Files.createDirectories(dir.resolve("lib"));
		try (var zip = new ZipOutputStream(Files.newOutputStream(lib.resolve("cozy-ui-1.0.0.jar")))) {
			zip.putNextEntry(new ZipEntry("META-INF/resources/cozy/vendor/htmx/2.0.11/LICENSE"));
			((OutputStream) zip).write("Zero-Clause BSD".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("META-INF/resources/cozy/vendor/htmx/2.0.11/htmx.min.js"));
			zip.closeEntry();
		}
		Path image = Files.createDirectories(dir.resolve("image"));

		int[] counts = ThirdPartyLicenses.bundle(list, "Apache-2.0|MIT|EPL-2.0", lib, image);

		assertThat(counts).containsExactly(3, 1);
		String bundled = Files.readString(image.resolve("licenses/THIRD-PARTY.txt"));
		assertThat(bundled).contains("org.xerial:sqlite-jdbc:3.54.1.0").contains("htmx-2.0.11").contains("legal/");
		assertThat(image.resolve("licenses/vendor/htmx-2.0.11-LICENSE.txt")).hasContent("Zero-Clause BSD");
	}

	@Test
	void bundleFailsWhenListIsMissingOrNotAllowed(@TempDir Path dir) throws Exception {
		Path lib = Files.createDirectories(dir.resolve("lib"));
		assertThatThrownBy(() -> ThirdPartyLicenses.bundle(dir.resolve("none.txt"), "MIT", lib, dir))
			.hasMessageContaining("목록이 없습니다");
		Path list = Files.writeString(dir.resolve("THIRD-PARTY.txt"), LIST);
		assertThatThrownBy(() -> ThirdPartyLicenses.bundle(list, "Apache-2.0", lib, dir))
			.hasMessageContaining("org.slf4j:slf4j-api:2.0.20")
			.hasMessageContaining("ch.qos.logback:logback-classic:1.5.38");
		assertThat(dir.resolve("licenses")).doesNotExist();
	}
}
