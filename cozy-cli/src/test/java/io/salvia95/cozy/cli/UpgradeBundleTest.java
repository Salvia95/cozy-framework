package io.salvia95.cozy.cli;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.salvia95.cozy.cli.internal.channel.ChannelIndex;
import io.salvia95.cozy.cli.internal.channel.ChannelIndex.ChannelFile;
import io.salvia95.cozy.cli.internal.channel.ChannelIndex.Release;
import io.salvia95.cozy.cli.internal.channel.Version;
import io.salvia95.cozy.rules.Violation;
import org.attoparser.MarkupParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * zip으로 설치한 cozy가 파일 채널에서 스스로 올라간다. 번들의 진짜 cozy 스크립트와 cozy-cli(이 빌드의 클래스)로 설치 → 채널 정하기 →
 * {@code cozy upgrade} → cozy가 끝난 뒤 도는 설치 스크립트 → 다음 명령의 결과 보이기까지 돌린다. 번들의 Maven과 저장소는 작은 가짜다.
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "bash의 cozy를 실행한다. Windows는 MSI 검증(verify-msi.ps1)이 같은 흐름을 본다")
class UpgradeBundleTest {

	@TempDir
	Path temp;

	@Test
	void anInstalledBundleUpgradesFromAFileChannelAndKeepsWhatOnlyTheOldReleaseHad() throws Exception {
		Path old = bundle("2.1.0", Map.of("io/salvia95/cozy/cozy-ui/2.1.0/cozy-ui-2.1.0.jar", "ui 2.1.0",
				"org/example/shared/1.0/shared-1.0.jar", "shared"));
		Path next = zip(bundle("2.1.1", Map.of("io/salvia95/cozy/cozy-ui/2.1.1/cozy-ui-2.1.1.jar", "ui 2.1.1",
				"org/example/shared/1.0/shared-1.0.jar", "shared")));
		Path index = channel("2.1.1", next);
		Path dest = this.temp.resolve("installed");
		Path userRepository = this.temp.resolve("home/.local/share/cozy/repository");
		assertThat(run(old.resolve("cozy"), "install", dest.toString()).code()).isZero();
		assertThat(run(dest.resolve("bin/cozy"), "channel", "set", index.toString()).code()).isZero();

		Result upgrade = run(dest.resolve("bin/cozy"), "upgrade");

		assertThat(upgrade.code()).as(upgrade.output()).isZero();
		assertThat(upgrade.output()).contains("지금 판:  2.1.0", "올릴 판:  2.1.1", "받은 번들의 cozy install로 다시 설치합니다");
		Path result = this.temp.resolve("home/.local/share/cozy/upgrade/2.1.1/result");
		long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
		while (!Files.exists(result) && System.nanoTime() < deadline) {
			Thread.sleep(200);
		}
		String log = Files.readString(result.resolveSibling("install.log"));
		assertThat(result).as(log).hasContent("0");
		assertThat(dest.resolve("COZY_VERSION")).hasContent("2.1.1");
		assertThat(dest.resolve("repository/io/salvia95/cozy/cozy-ui/2.1.1/cozy-ui-2.1.1.jar")).hasContent("ui 2.1.1");
		assertThat(dest.resolve("repository/io/salvia95/cozy/cozy-ui/2.1.0")).doesNotExist();
		// 이전 판에만 있던 파일은 사용자 저장소로 옮겨져 올리기 전의 앱도 빌드된다. 두 판에 다 있는 것은 옮기지 않는다
		assertThat(userRepository.resolve("io/salvia95/cozy/cozy-ui/2.1.0/cozy-ui-2.1.0.jar")).hasContent("ui 2.1.0");
		assertThat(userRepository.resolve("org/example/shared")).doesNotExist();
		assertThat(log).contains("이전 판에만 있던 파일 1개를 사용자 저장소로 옮겼습니다");

		Result after = run(dest.resolve("bin/cozy"), "update");

		assertThat(after.output()).contains("[cozy] 판을 올렸습니다: 2.1.0 → 2.1.1.", "지금 판:  2.1.1", "[cozy] 최신 판입니다.");
		assertThat(this.temp.resolve("home/.local/share/cozy/upgrade/2.1.1")).doesNotExist();
		assertThat(run(dest.resolve("bin/cozy"), "update").output()).doesNotContain("판을 올렸습니다");
	}

	/** 번들 폴더: 진짜 cozy 스크립트, 이 빌드의 cozy-cli, 가짜 Maven 배포판과 저장소 */
	Path bundle(String version, Map<String, String> repository) throws Exception {
		Path bundle = Files.createDirectories(this.temp.resolve("build/cozy-offline-bundle-" + version));
		for (String script : List.of("cozy", "cozy.cmd", "cozy.ps1")) {
			Files.copy(BundleCommandsTest.BUNDLE.resolve(script), bundle.resolve(script));
		}
		Files.setPosixFilePermissions(bundle.resolve("cozy"), PosixFilePermissions.fromString("rwxr-xr-x"));
		write(bundle.resolve("COZY_VERSION"), version + "\n");
		write(bundle.resolve("LICENSE"), "license");
		write(bundle.resolve("NOTICE"), "notice");
		write(bundle.resolve("docs/README.md"), "docs");
		write(bundle.resolve("licenses/THIRD-PARTY.txt"), "list");
		for (Map.Entry<String, String> file : repository.entrySet()) {
			write(bundle.resolve("repository").resolve(file.getKey()), file.getValue());
		}
		cliJar(bundle.resolve("lib/cli/cozy-cli.jar"));
		Path maven = this.temp.resolve("maven-" + version + "/apache-maven-3.10.1");
		write(maven.resolve("conf/settings.xml"), "<settings/>");
		write(maven.resolve("bin/mvn"), "#!/bin/sh\necho maven\n");
		exec(maven.getParent(), "zip", "-qr", bundle.resolve("apache-maven-3.10.1-bin.zip").toString(), "apache-maven-3.10.1");
		return bundle;
	}

	/** 이 빌드의 클래스를 가리키는 실행 jar. 빈 jar에 Main-Class와 Class-Path만 둔다 */
	static void cliJar(Path jar) throws IOException, URISyntaxException {
		List<Path> classPath = new ArrayList<>();
		for (Class<?> type : List.of(CozyCli.class, Violation.class, Yaml.class, MarkupParser.class)) {
			classPath.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()));
		}
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, CozyCli.class.getName());
		manifest.getMainAttributes()
			.put(Attributes.Name.CLASS_PATH, classPath.stream().map((path) -> path.toUri().toString()).collect(Collectors.joining(" ")));
		Files.createDirectories(jar.getParent());
		try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
			out.flush();
		}
	}

	Path zip(Path bundle) throws Exception {
		Path zip = bundle.resolveSibling(bundle.getFileName() + ".zip");
		exec(bundle.getParent(), "zip", "-qr", zip.toString(), bundle.getFileName().toString());
		return zip;
	}

	/** 채널 폴더: {@code {판}/번들 zip}과 {@code index/cozy-channel.json} */
	Path channel(String version, Path zip) throws IOException {
		Path channel = this.temp.resolve("channel");
		Path file = Files.createDirectories(channel.resolve(version)).resolve(zip.getFileName());
		Files.copy(zip, file);
		Version parsed = Version.parse(version, "test");
		ChannelIndex index = ChannelIndex.empty()
			.with(new Release(parsed, LocalDate.parse("2026-12-01"), false, "고친 판", List.of(), List.of(new ChannelFile("portable",
					"../" + version + "/" + file.getFileName(), ChannelIndex.sha256(file), Files.size(file)))));
		return write(channel.resolve("index/cozy-channel.json"), index.toJson());
	}

	record Result(int code, String output) {
	}

	Result run(Path script, String... args) throws Exception {
		List<String> command = new ArrayList<>(List.of("bash", script.toString()));
		command.addAll(List.of(args));
		ProcessBuilder builder = new ProcessBuilder(command).directory(this.temp.toFile()).redirectErrorStream(true);
		Map<String, String> env = builder.environment();
		env.put("HOME", this.temp.resolve("home").toString());
		Stream.of("COZY_REPOSITORY", "COZY_HOME", "COZY_CHANNEL", "COZY_CHANNEL_TOKEN", "XDG_CONFIG_HOME").forEach(env::remove);
		Process process = builder.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
		return new Result(process.exitValue(), output);
	}

	static void exec(Path dir, String... command) throws Exception {
		Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
		assertThat(process.exitValue()).as(output).isZero();
	}

	static Path write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		return Files.writeString(file, text, StandardCharsets.UTF_8);
	}
}
