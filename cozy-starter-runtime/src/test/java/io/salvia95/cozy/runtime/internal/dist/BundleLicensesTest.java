package io.salvia95.cozy.runtime.internal.dist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 오프라인 번들 저장소의 라이선스 목록 */
class BundleLicensesTest {

	@TempDir
	Path dir;

	@Test
	void normalizesLicenseNamesAndUrlsToSpdx() {
		assertThat(BundleLicenses.normalize("The Apache Software License, Version 2.0", "")).isEqualTo("Apache-2.0");
		assertThat(BundleLicenses.normalize("Apache 2", "")).isEqualTo("Apache-2.0");
		assertThat(BundleLicenses.normalize("Eclipse Public License - v 2.0", "")).isEqualTo("EPL-2.0");
		assertThat(BundleLicenses.normalize("EPLv2", "")).isEqualTo("EPL-2.0");
		assertThat(BundleLicenses.normalize("EPL 1.0", "")).isEqualTo("EPL-1.0");
		assertThat(BundleLicenses.normalize("Lesser General Public License (LGPL) v 3.0", "")).isEqualTo("LGPL-3.0-only");
		assertThat(BundleLicenses.normalize("GPL2 w/ CPE", "")).isEqualTo(BundleLicenses.GPL2_CPE);
		assertThat(BundleLicenses.normalize("Mozilla Public License Version 2.0", "")).isEqualTo("MPL-2.0");
		assertThat(BundleLicenses.normalize("Eclipse Distribution License - v 1.0", "")).isEqualTo("BSD-3-Clause");
		assertThat(BundleLicenses.normalize("The MIT License", "")).isEqualTo("MIT");
		// 이름만으로 정할 수 없으면 URL로 정한다
		assertThat(BundleLicenses.normalize("BSD", "http://asm.ow2.io/license.html")).isEqualTo("BSD-3-Clause");
		assertThat(BundleLicenses.normalize("BSD", "https://example.com/COPYING")).isNull();
		// 버전 없는 LGPL, GPL(예외 없음)은 맞추지 않는다
		assertThat(BundleLicenses.normalize("GNU Lesser General Public License", "")).isNull();
		assertThat(BundleLicenses.normalize("GNU General Public License v3.0", "")).isEqualTo("GPL-3.0-only");
	}

	@Test
	void resolvesLicensesFromPomParentOverridesAndCozy() throws Exception {
		pom("org.example", "parent", "1", null, "MIT License", false);
		pom("org.example", "child", "2.0", "org.example:parent:1", null, true);
		pom("org.example", "dual", "1.0", null, "GNU Lesser General Public License|Apache License, Version 2.0", true);
		pom("org.example", "nolicense", "1.0", null, null, false);
		pom("io.salvia95.cozy", "cozy-parent", "1.0.0", "org.example:parent:1", null, false);

		var overrides = BundleLicenses.parseOverrides("""
				# 주석 줄
				org.example:nolicense:1.0   Apache-2.0   # 상위 저장소 LICENSE 확인
				""");
		Map<String, BundleLicenses.Artifact> found = byCoordinates(BundleLicenses.scan(dir, overrides));

		assertThat(found.get("org.example:child:2.0").licenses()).containsExactly("MIT");
		assertThat(found.get("org.example:child:2.0").from()).isEqualTo("parent org.example:parent:1");
		assertThat(found.get("org.example:dual:1.0").licenses())
			.containsExactly("?GNU Lesser General Public License", "Apache-2.0");
		assertThat(found.get("org.example:nolicense:1.0").licenses()).containsExactly("Apache-2.0");
		assertThat(found.get("org.example:nolicense:1.0").from()).isEqualTo("override: 상위 저장소 LICENSE 확인");
		// Cozy 자신은 POM에 라이선스를 두지 않는다(앱이 cozy-parent를 상속)
		assertThat(found.get("io.salvia95.cozy:cozy-parent:1.0.0").licenses()).containsExactly("Apache-2.0");
		assertThat(found.get("io.salvia95.cozy:cozy-parent:1.0.0").from()).isEqualTo("Cozy");
		assertThat(BundleLicenses.problems(found.values())).isEmpty();
	}

	@Test
	void unknownDisallowedAndUnjustifiedEntriesFailClosed() throws Exception {
		pom("org.example", "gpl", "1.0", null, "GNU General Public License v3.0", true);
		pom("org.example", "none", "1.0", null, null, true);
		pom("org.example", "odd", "1.0", null, "Some Custom License", false);

		var problems = BundleLicenses.problems(BundleLicenses.scan(dir, Map.of()));
		assertThat(problems).containsExactly(
				"org.example:gpl:1.0 [GPL-3.0-only] (POM)",
				"org.example:none:1.0 [라이선스 정보 없음]",
				"org.example:odd:1.0 [?Some Custom License] (POM)");
		// 예외 목록에는 근거(# 뒤)가 있어야 한다
		assertThatThrownBy(() -> BundleLicenses.parseOverrides("org.example:none:1.0 MIT\n"))
			.hasMessageContaining("근거");
		assertThatThrownBy(() -> BundleLicenses.parseOverrides("org.example:none MIT # 좌표가 틀림\n"))
			.hasMessageContaining("groupId:artifactId:version");
	}

	@Test
	void weakCopyleftOnlyJarsGetTheirSourceJarWithChecksum() throws Exception {
		pom("org.example", "epl", "1.0", null, "Eclipse Public License v2.0", true);
		pom("org.example", "eplpom", "1.0", null, "EPL-2.0", false);
		pom("org.example", "permissive", "1.0", null, "EPL-2.0|MIT", true);
		var artifacts = BundleLicenses.scan(dir, Map.of());
		assertThat(BundleLicenses.missingSources(artifacts)).extracting(BundleLicenses.Artifact::coordinates)
			.containsExactly("org.example:epl:1.0");

		// 소스 jar를 받는 곳(테스트는 file: 저장소)
		Path remote = dir.resolve("remote");
		Path sources = Files.createDirectories(remote.resolve("org/example/epl/1.0")).resolve("epl-1.0-sources.jar");
		byte[] content = "source".getBytes(StandardCharsets.UTF_8);
		Files.write(sources, content);
		Files.writeString(sources.resolveSibling("epl-1.0-sources.jar.sha1"),
				HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(content)) + "  epl-1.0-sources.jar\n");

		BundleLicenses.fetchSources(BundleLicenses.missingSources(artifacts), remote.toUri(), new PrintStream(new ByteArrayOutputStream()));
		assertThat(dir.resolve("org/example/epl/1.0/epl-1.0-sources.jar")).hasBinaryContent(content);
		assertThat(BundleLicenses.missingSources(BundleLicenses.scan(dir, Map.of()))).isEmpty();
	}

	@Test
	void sourceJarWithWrongChecksumOrMissingFails() throws Exception {
		pom("org.example", "epl", "1.0", null, "EPL-2.0", true);
		Path remote = dir.resolve("remote");
		Path sources = Files.createDirectories(remote.resolve("org/example/epl/1.0")).resolve("epl-1.0-sources.jar");
		Files.writeString(sources, "source");
		Files.writeString(sources.resolveSibling("epl-1.0-sources.jar.sha1"), "0000000000000000000000000000000000000000");
		var missing = BundleLicenses.missingSources(BundleLicenses.scan(dir, Map.of()));
		var quiet = new PrintStream(new ByteArrayOutputStream());

		assertThatThrownBy(() -> BundleLicenses.fetchSources(missing, remote.toUri(), quiet)).hasMessageContaining("SHA-1");
		assertThat(dir.resolve("org/example/epl/1.0/epl-1.0-sources.jar")).doesNotExist();
		assertThatThrownBy(() -> BundleLicenses.fetchSources(missing, dir.resolve("empty").toUri(), quiet))
			.hasMessageContaining("org.example:epl:1.0");
	}

	@Test
	void mainWritesListOnlyWhenEverythingPasses() throws Exception {
		Path repo = Files.createDirectories(dir.resolve("repository"));
		pom(repo, "org.example", "lib", "1.0", null, "Apache-2.0", true);
		pom(repo, "org.example", "epl", "1.0", null, "EPL-2.0", true);
		Files.writeString(repo.resolve("org/example/epl/1.0/epl-1.0-sources.jar"), "source");
		Path overrides = Files.writeString(dir.resolve("overrides.txt"), "# 없음\n");
		Path out = dir.resolve("bundle/licenses/THIRD-PARTY.txt");
		var stdout = new ByteArrayOutputStream();

		int status = BundleLicenses.run(List.of("--repository=" + repo, "--overrides=" + overrides, "--output=" + out,
				"--title=Cozy offline bundle 1.0.0", "--maven-distribution=apache-maven-3.10.0-bin.zip"),
				new PrintStream(stdout, true, StandardCharsets.UTF_8));

		assertThat(status).isZero();
		String list = Files.readString(out);
		assertThat(list).startsWith("Cozy offline bundle 1.0.0").contains("apache-maven-3.10.0-bin.zip")
			.contains("Lists of 2 artifacts in repository/.")
			.contains("     (Apache-2.0) org.example:lib:1.0\n")
			.contains("     (EPL-2.0) org.example:epl:1.0 [source: repository/org/example/epl/1.0/epl-1.0-sources.jar]\n");

		// 문제가 있으면 목록을 쓰지 않고 실패한다
		Files.delete(out);
		pom(repo, "org.example", "gpl", "1.0", null, "GPL-3.0", true);
		var errors = new ByteArrayOutputStream();
		int failed = BundleLicenses.run(List.of("--repository=" + repo, "--overrides=" + overrides, "--output=" + out),
				new PrintStream(errors, true, StandardCharsets.UTF_8));
		assertThat(failed).isEqualTo(1);
		assertThat(out).doesNotExist();
		assertThat(errors.toString(StandardCharsets.UTF_8)).contains("org.example:gpl:1.0");
	}

	private void pom(String group, String artifact, String version, String parent, String licenses, boolean jar)
			throws Exception {
		pom(this.dir, group, artifact, version, parent, licenses, jar);
	}

	private static void pom(Path repo, String group, String artifact, String version, String parent, String licenses,
			boolean jar) throws Exception {
		Path folder = Files.createDirectories(repo.resolve(group.replace('.', '/')).resolve(artifact).resolve(version));
		StringBuilder xml = new StringBuilder("<?xml version=\"1.0\"?>\n<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n");
		if (parent != null) {
			String[] p = parent.split(":");
			xml.append("  <parent><groupId>").append(p[0]).append("</groupId><artifactId>").append(p[1])
				.append("</artifactId><version>").append(p[2]).append("</version></parent>\n");
		}
		xml.append("  <artifactId>").append(artifact).append("</artifactId>\n");
		if (licenses != null) {
			xml.append("  <licenses>\n");
			for (String name : licenses.split("\\|")) {
				xml.append("    <license><name>").append(name).append("</name></license>\n");
			}
			xml.append("  </licenses>\n");
		}
		xml.append("</project>\n");
		Files.writeString(folder.resolve(artifact + "-" + version + ".pom"), xml);
		if (jar) {
			Files.writeString(folder.resolve(artifact + "-" + version + ".jar"), "jar");
		}
	}

	private static Map<String, BundleLicenses.Artifact> byCoordinates(List<BundleLicenses.Artifact> artifacts) {
		var map = new java.util.LinkedHashMap<String, BundleLicenses.Artifact>();
		artifacts.forEach((artifact) -> map.put(artifact.coordinates(), artifact));
		return map;
	}
}
