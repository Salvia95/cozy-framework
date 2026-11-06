package io.salvia95.cozy.runtime.internal.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * 배포 zip (PRD §9.4, §9.6): 최상위 폴더 하나, 실행 권한 보존, 풀 위치 밖으로 나가는 항목 거부.
 */
class ZipArchivesTest {

	@TempDir
	Path temp;

	@Test
	void roundTripKeepsSingleRootAndContent() throws IOException {
		Path app = Files.createDirectories(this.temp.resolve("voc"));
		Files.createDirectories(app.resolve("lib/app"));
		Files.writeString(app.resolve("lib/app/voc.cfg"), "[Application]\n", StandardCharsets.UTF_8);
		// AOT 캐시가 jar의 수정 시각을 대조하므로 시각이 보존되어야 한다 (짝수 초: zip의 시각 단위)
		FileTime time = FileTime.from(Instant.parse("2026-11-06T01:02:04Z"));
		Files.setLastModifiedTime(app.resolve("lib/app/voc.cfg"), time);
		Path zip = this.temp.resolve("voc.zip");
		ZipArchives.create(app, zip);

		Path target = this.temp.resolve("out/voc.new");
		Files.createDirectories(target.getParent());
		assertThat(ZipArchives.extractSingleRoot(zip, target)).isEqualTo("voc");
		assertThat(target.resolve("lib/app/voc.cfg")).hasContent("[Application]");
		assertThat(Files.getLastModifiedTime(target.resolve("lib/app/voc.cfg"))).isEqualTo(time);
		assertThat(ZipArchives.sha256(zip)).hasSize(64).isEqualTo(ZipArchives.sha256(zip));
		assertThatThrownBy(() -> ZipArchives.extractSingleRoot(zip, target)).hasMessageContaining("이미 있습니다");
	}

	@Test
	@DisabledOnOs(OS.WINDOWS)
	void executablePermissionSurvives() throws IOException {
		Path app = Files.createDirectories(this.temp.resolve("voc/bin"));
		Path launcher = Files.writeString(app.resolve("voc"), "#!/bin/sh\n");
		Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwxr-xr-x"));
		Path zip = this.temp.resolve("voc.zip");
		ZipArchives.create(this.temp.resolve("voc"), zip);
		Path target = this.temp.resolve("voc.new");
		ZipArchives.extractSingleRoot(zip, target);
		assertThat(Files.isExecutable(target.resolve("bin/voc"))).isTrue();
	}

	@Test
	void entriesEscapingTheTargetAreRejectedAndNothingIsLeft() throws IOException {
		Path zip = this.temp.resolve("evil.zip");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
			entry(out, "voc/ok.txt");
			entry(out, "voc/../../evil.txt");
		}
		Path target = this.temp.resolve("x/voc.new");
		Files.createDirectories(target.getParent());
		assertThatThrownBy(() -> ZipArchives.extractSingleRoot(zip, target)).isInstanceOf(IOException.class);
		assertThat(target).doesNotExist();
		assertThat(this.temp.resolve("evil.txt")).doesNotExist();
	}

	@Test
	void moreThanOneTopLevelEntryIsRejected() throws IOException {
		Path zip = this.temp.resolve("two.zip");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
			entry(out, "a/x.txt");
			entry(out, "b/y.txt");
		}
		assertThatThrownBy(() -> ZipArchives.extractSingleRoot(zip, this.temp.resolve("t")))
			.hasMessageContaining("최상위 폴더가 하나만");
	}

	private static void entry(ZipOutputStream out, String name) throws IOException {
		out.putNextEntry(new ZipEntry(name));
		OutputStream body = out;
		body.write("x".getBytes(StandardCharsets.UTF_8));
		out.closeEntry();
	}
}
