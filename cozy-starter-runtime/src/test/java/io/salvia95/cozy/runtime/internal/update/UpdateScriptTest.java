package io.salvia95.cozy.runtime.internal.update;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Linux 교체 스크립트가 이전 앱 프로세스가 살아 있는지 판단하는 방법. 스크립트는 이전 앱이 끝날 때까지 기다리고, 그래도 살아 있으면 업데이트를
 * 취소한다. 끝난 프로세스를 살아 있다고 보면 업데이트가 이유 없이 취소된다.
 *
 * <p>스크립트의 {@code alive} 함수를 그대로 꺼내 돌리고, {@code kill}과 {@code ps}는 셸 함수로 바꿔 상황을 정한다.
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Windows는 PowerShell 스크립트(Get-Process)가 돈다")
class UpdateScriptTest {

	@TempDir
	Path temp;

	/** kill: ok(늘 있음), dead(없음), once(처음 한 번만 있고 그 뒤로 없음). ps: 상태 글자를 낸다(빈 글이면 찾지 못함) */
	int alive(String kill, String ps) throws IOException, InterruptedException {
		String script = """
				kill() {
				  case "$KILL" in
				    ok) return 0 ;;
				    dead) return 1 ;;
				    once) [ -e "$MARK" ] && return 1; : > "$MARK"; return 0 ;;
				  esac
				}
				ps() { printf '%%s' "$PS_OUT"; }
				%s
				alive 4242
				""".formatted(aliveFunction());
		ProcessBuilder builder = new ProcessBuilder(List.of("bash", "-c", script)).redirectErrorStream(true);
		Map<String, String> env = builder.environment();
		env.put("KILL", kill);
		env.put("PS_OUT", ps);
		env.put("MARK", this.temp.resolve("killed-once").toString());
		Process process = builder.start();
		assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
		assertThat(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEmpty();
		return process.exitValue();
	}

	String aliveFunction() throws IOException {
		Path app = Files.createDirectories(this.temp.resolve("apps/demo"));
		String bash = UpdateScript.bash(new UpdateScript.Parameters(1, new AppImage(app, app.resolve("bin/demo")),
				this.temp.resolve("data"), new UpdateFiles(this.temp.resolve("update")), "1.0.0", "1.1.0", Duration.ofSeconds(60)));
		Matcher function = Pattern.compile("(?ms)^alive\\(\\) \\{\\n.*?^\\}\\n").matcher(bash);
		assertThat(function.find()).as("스크립트의 alive 함수").isTrue();
		return function.group();
	}

	@Test
	void aRunningProcessIsAlive() throws Exception {
		assertThat(alive("ok", "S")).isZero();
		assertThat(alive("ok", "R")).isZero();
	}

	@Test
	void anEndedProcessIsNotAlive() throws Exception {
		assertThat(alive("dead", "")).isNotZero();
	}

	@Test
	void aZombieIsNotAlive() throws Exception {
		// 끝났지만 부모가 아직 거두지 않은 프로세스
		assertThat(alive("ok", "Z")).isNotZero();
	}

	@Test
	void aProcessReapedBetweenTheChecksIsNotAlive() throws Exception {
		// kill -0은 좀비라 성공했고, ps를 부르기 전에 부모가 거둬 ps가 아무것도 찾지 못했다.
		// 예전에는 이것을 살아 있다고 보아 업데이트를 취소했다(CI에서 간헐적으로 실패하던 경우)
		assertThat(alive("once", "")).isNotZero();
	}

	@Test
	void withoutPsALiveProcessIsStillAlive() throws Exception {
		// ps가 없거나 실패해도 프로세스가 있으면 기다린다. 앱이 도는 중에 파일을 바꾸지 않는다
		assertThat(alive("ok", "")).isZero();
	}
}
