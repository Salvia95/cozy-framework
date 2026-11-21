package io.salvia95.cozy.cli.internal.channel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.channel.ChannelIndex.ChannelFile;
import io.salvia95.cozy.cli.internal.channel.ChannelIndex.Release;
import io.salvia95.cozy.cli.internal.channel.UpdateCommands.Plan;

/**
 * {@code cozy upgrade [--to 판]}: 채널에서 판을 받아 크기와 SHA-256을 확인하고, cozy 명령이 끝난 뒤에 설치한다. {@code --check}(또는
 * {@code --dry-run})이면 받지 않고 새 판만 보인다({@link UpdateCommands#check}).
 *
 * <ul>
 * <li>설치 방식은 설치한 모양을 따른다. MSI로 설치했으면 받은 MSI를 사용자별로 조용히 설치하고, zip 번들로 설치했으면 받은 번들의
 * {@code cozy install}로 같은 폴더에 다시 설치한다.</li>
 * <li>실행 중인 cozy 명령의 파일(cozy.cmd, cozy-cli.jar, JDK)을 바꾸지 않도록 설치 스크립트를 따로 띄우고 cozy는 바로 끝난다. 스크립트는
 * cozy가 끝나기를 기다렸다 설치하고 종료 코드를 남긴다. 다음 cozy 명령이 결과를 보인다({@link #report}).</li>
 * <li>이전 판의 파일을 남긴다. zip은 {@code cozy install}이 옮긴다. MSI는 설치 폴더의 번들 저장소를 하드 링크로 떠 두었다가, 설치한 뒤
 * 새 저장소에 없는 파일을 사용자 저장소로 옮긴다.</li>
 * <li>호환이 깨지는 판(주 버전이 바뀐 판)은 {@code --to}로 적어야 올린다. 낮은 판으로는 가지 않는다.</li>
 * </ul>
 */
public final class UpgradeCommands {

	/** 사용법. 거절할 때마다 이것을 보인다 */
	public static final String USAGE = "cozy upgrade [--check] [--to <version>] [--json]";

	/**
	 * {@code cozy upgrade} 뒤의 인자.
	 *
	 * @param check 받지 않고 새 판만 보인다({@code --check}, {@code --dry-run})
	 * @param to 적은 판. 없으면 {@code null}
	 * @param json 기계가 읽는 출력({@code --check}와 함께)
	 */
	public record Request(boolean check, Version to, boolean json) {

		/**
		 * @param args {@code upgrade}(또는 별칭 {@code update}) 뒤의 인자. {@code --json}과 {@code --dry-run}은 cozy 명령이 먼저 읽는다
		 */
		public static Request parse(List<String> args, boolean dryRun, boolean json) {
			boolean check = dryRun;
			Version to = null;
			for (int i = 0; i < args.size(); i++) {
				String arg = args.get(i);
				if (arg.equals("--check")) {
					check = true;
				}
				else if (arg.equals("--to") && to == null && i + 1 < args.size()) {
					try {
						to = Version.parse(args.get(++i), "--to");
					}
					catch (CliException ex) {
						throw refused(args);
					}
				}
				else {
					throw refused(args);
				}
			}
			if (json && !check) {
				List<String> all = new ArrayList<>(args);
				all.add("--json");
				throw refused(all);
			}
			return new Request(check, to, json);
		}

		private static CliException refused(List<String> args) {
			return new CliException(USAGE + "로 씁니다(받은 인자: " + (args.isEmpty() ? "없음" : String.join(" ", args))
					+ "). 새 판은 cozy upgrade --check로 봅니다.");
		}
	}

	/** 설치를 맡긴 기록. 사용자 설정 파일 옆에 둔다. 다음 cozy 명령이 결과를 보이고 지운다 */
	public static final String RECORD_FILE = "upgrade.properties";

	/** 이 시간이 지나도 설치 스크립트가 시작한 흔적이 없으면 시작하지 못한 것으로 본다 */
	static final Duration START_GRACE = Duration.ofMinutes(2);

	/** 받을 파일의 이름. 폴더 밖을 가리킬 수 없다 */
	private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

	private UpgradeCommands() {
	}

	/** 설치 스크립트를 띄운다. cozy 명령이 끝난 뒤에도 돈다 */
	@FunctionalInterface
	public interface Launcher {

		void start(List<String> command) throws IOException;
	}

	/**
	 * 이 PC에서 cozy와 떨어져 도는 프로세스로 띄운다. Linux는 새 세션({@code setsid})으로 띄워 터미널을 닫아도 돈다. Windows는
	 * {@code start}로 새 콘솔(숨긴 창)에 띄워 cozy를 실행한 창을 닫아도 돈다.
	 */
	public static Launcher detached(boolean windows) {
		return (command) -> {
			List<String> line = new ArrayList<>();
			if (windows) {
				// start는 따옴표로 감싼 첫 인자를 창 제목으로 읽는다. ProcessBuilder는 빈칸이 있는 인자를 따옴표로 감싼다
				line.addAll(List.of("cmd.exe", "/d", "/c", "start", "cozy upgrade", "/min"));
			}
			else if (Files.isExecutable(Path.of("/usr/bin/setsid")) || Files.isExecutable(Path.of("/bin/setsid"))) {
				line.add("setsid");
			}
			line.addAll(command);
			new ProcessBuilder(line).redirectInput(ProcessBuilder.Redirect.from(new File(windows ? "NUL" : "/dev/null")))
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
		};
	}

	/** 설치 스크립트가 끝나기를 기다릴 프로세스. 이 cozy-cli와, Windows는 이것을 띄운 PowerShell(cozy.ps1)도 */
	public static List<Long> waitFor(boolean windows) {
		ProcessHandle self = ProcessHandle.current();
		List<Long> pids = new ArrayList<>(List.of(self.pid()));
		if (windows) {
			self.parent().ifPresent((parent) -> pids.add(parent.pid()));
		}
		return pids;
	}

	/**
	 * @param to 적은 판({@code --to}). 없으면 주 버전이 같은 가장 새 판
	 * @param now 지금 시각(설치 기록에 적는다)
	 * @param launcher 설치 스크립트를 띄운다
	 * @param waitFor 설치 스크립트가 끝나기를 기다릴 프로세스
	 */
	public static void upgrade(Version to, Map<String, String> env, boolean windows, Instant now, ChannelClient client,
			Launcher launcher, List<Long> waitFor, PrintStream out) {
		Path config = UserConfig.file(env, windows);
		ChannelSettings settings = UpdateCommands.settings(env, config);
		Installed installed = Installed.require(env);
		Version current = installed.release();
		Path recordFile = config.resolveSibling(RECORD_FILE);
		Record pending = Record.read(recordFile);
		if (pending != null && pending.state(now) == State.RUNNING) {
			throw new CliException("판(" + pending.version() + ")을 설치하는 중입니다. 설치가 끝나면 다음 cozy 명령이 결과를 보입니다. 기록: "
					+ pending.log());
		}
		ChannelIndex index = client.index(settings);
		Release target = target(current, to, index, out);
		if (target == null) {
			return;
		}
		String kind = kind(installed, windows);
		ChannelFile file = target.files()
			.stream()
			.filter((candidate) -> candidate.kind().equals(kind))
			.findFirst()
			.orElseThrow(() -> new CliException("채널의 판(" + target.version() + ")에 이 설치에 맞는 파일(" + kind + ")이 없습니다. 있는 "
					+ "파일: " + target.files().stream().map(ChannelFile::kind).collect(Collectors.joining(", ")) + ". 채널 관리자가 "
					+ "릴리스의 파일을 모두 cozy channel add로 더했는지 확인합니다."));
		if (!FILE_NAME.matcher(file.name()).matches()) {
			throw new CliException("채널 색인의 파일 이름(" + file.name() + ")을 쓸 수 없습니다. 설치하지 않습니다.");
		}

		out.println("지금 판:  " + current);
		out.println("올릴 판:  " + target.version());
		out.println("채널:     " + settings.address());
		List<Release> between = index.releases()
			.stream()
			.filter((release) -> release.version().compareTo(current) > 0 && release.version().compareTo(target.version()) <= 0)
			.toList();
		UpdateCommands.releaseLines(between, out);
		out.println();

		Path upgrades = dataDir(env, windows).resolve("upgrade");
		Path work = upgrades.resolve(target.version().toString());
		clearOthers(upgrades, work);
		Path download = work.resolve(file.name());
		fetch(client, settings, file, download, out);

		Path script;
		if (kind.equals("msi")) {
			snapshot(installed.home().resolve("repository"), work.resolve("previous-repository"));
			script = write(work.resolve("install.ps1"), msiScript(work, download, installed.home(), userRepository(env), waitFor,
					target.version()), true);
		}
		else {
			Path bundle = unpack(download, work.resolve("bundle"), windows, target.version());
			script = windows
					? write(work.resolve("install.ps1"), zipScript(work, bundle, installed.home(), download, waitFor, target.version()), true)
					: write(work.resolve("install.sh"), shellScript(work, bundle, installed.home(), download, waitFor, target.version()),
							false);
		}
		deleteIfExists(work.resolve("result"));
		deleteIfExists(work.resolve("pid"));
		Record record = new Record(target.version(), current, kind, installed.home(), work, download, now);
		record.write(recordFile);
		try {
			launcher.start(windows
					? List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File",
							script.toString())
					: List.of("bash", script.toString()));
		}
		catch (IOException ex) {
			deleteIfExists(recordFile);
			throw new CliException("설치 스크립트(" + script + ")를 띄우지 못했습니다: " + ex.getMessage() + ". 받은 파일로 손으로 설치할 수 "
					+ "있습니다: " + download);
		}
		out.println("[cozy] cozy 명령이 끝나면 " + (kind.equals("msi") ? "받은 MSI를 창 없이 설치합니다" : "받은 번들의 cozy install로 다시 설치합니다")
				+ "(" + installed.home() + "). 1~2분 걸리고, 결과는 다음 cozy 명령이 보입니다.");
		out.println("[cozy] 기록: " + record.log());
		List<String> steps = between.stream().flatMap((release) -> release.appSteps().stream()).distinct().toList();
		if (!steps.isEmpty()) {
			out.println("[cozy] 앱을 이 판으로 올릴 때 앱 폴더에서 돌릴 명령: " + String.join(", ", steps));
		}
		Plan plan = Plan.of(target.version(), index);
		if (plan.breakingLatest() != null) {
			out.println("[cozy] 호환이 깨지는 판(" + plan.breakingLatest() + ")도 있습니다. 앱에서 돌릴 명령은 cozy upgrade --check로 봅니다.");
		}
	}

	/** 올릴 판. 올릴 것이 없으면 알리고 {@code null} */
	static Release target(Version current, Version to, ChannelIndex index, PrintStream out) {
		if (to != null) {
			int compared = to.compareTo(current);
			if (compared == 0) {
				out.println("[cozy] 지금 판입니다: " + current + ".");
				return null;
			}
			if (compared < 0) {
				throw new CliException("낮은 판으로는 가지 않습니다(지금 판 " + current + ", 적은 판 " + to + "). 낮은 판이 필요하면 지금 판을 "
						+ "지운 뒤 그 판을 설치합니다.");
			}
			Release release = index.find(to);
			if (release == null) {
				throw new CliException("채널에 판(" + to + ")이 없습니다. 채널의 판은 cozy upgrade --check로 봅니다.");
			}
			return release;
		}
		Plan plan = Plan.of(current, index);
		if (plan.latest() == null) {
			throw new CliException("채널에 릴리스가 없습니다. 관리자가 cozy channel add로 릴리스를 더합니다.");
		}
		if (plan.upgrade() != null) {
			return index.find(plan.upgrade());
		}
		if (plan.breakingLatest() != null) {
			out.println("새 판 " + plan.newer().size() + "개");
			UpdateCommands.releaseLines(plan.newer(), out);
			out.println();
			throw new CliException("새 판은 모두 호환이 깨지는 판입니다. 앱에서 돌릴 명령을 보고 준비가 되면 판을 적어 올립니다: cozy upgrade --to "
					+ plan.breakingLatest());
		}
		if (plan.latest().compareTo(current) < 0) {
			throw new CliException("채널의 최신 판(" + plan.latest() + ")이 지금 판(" + current + ")보다 낮습니다. 낮은 판으로는 가지 않습니다. "
					+ "채널 주소가 맞는지 cozy channel show로 확인합니다.");
		}
		out.println("[cozy] 최신 판입니다.");
		return null;
	}

	/** 설치한 모양에 맞는 채널 파일의 종류 */
	static String kind(Installed installed, boolean windows) {
		if (installed.msi()) {
			if (!windows) {
				throw new CliException("MSI로 설치한 cozy(" + installed.home() + ")는 Windows에서 올립니다.");
			}
			return "msi";
		}
		if (windows) {
			return installed.bundledJdk() ? "windows-x64" : "portable";
		}
		if (installed.bundledJdk()) {
			throw new CliException("이 설치(" + installed.home() + ")는 JDK를 담은 번들입니다. 채널에는 Windows용 JDK 번들(windows-x64)과 "
					+ "JDK 없는 번들(portable)만 있어 올리지 않습니다. JDK 25를 설치한 뒤 JDK 없는 번들을 cozy install로 설치합니다.");
		}
		return "portable";
	}

	/** 사용자 데이터 폴더. 사용자 저장소와 받은 판을 둔다(Windows {@code %LOCALAPPDATA%\Cozy}, 그 밖 {@code ~/.local/share/cozy}) */
	static Path dataDir(Map<String, String> env, boolean windows) {
		if (windows) {
			String local = env.get("LOCALAPPDATA");
			if (local == null || local.isBlank()) {
				throw new CliException("환경 변수 LOCALAPPDATA가 없어 받은 판을 둘 곳(%LOCALAPPDATA%\\Cozy\\upgrade)을 알 수 없습니다.");
			}
			return Path.of(local, "Cozy");
		}
		String home = env.get("HOME");
		if (home == null || home.isBlank()) {
			home = System.getProperty("user.home");
		}
		return Path.of(home, ".local", "share", "cozy");
	}

	/** Maven이 쓰는 사용자 저장소. 설치한 Maven의 settings.xml과 같은 곳이다 */
	private static Path userRepository(Map<String, String> env) {
		return dataDir(env, true).resolve("repository");
	}

	/** 다른 판을 받다 남은 폴더를 지운다 */
	private static void clearOthers(Path upgrades, Path keep) {
		if (!Files.isDirectory(upgrades)) {
			return;
		}
		try (Stream<Path> children = Files.list(upgrades)) {
			for (Path child : children.filter((path) -> !path.equals(keep)).toList()) {
				delete(child);
			}
		}
		catch (IOException ex) {
			throw new CliException("받은 판을 두는 폴더 " + upgrades + "를 정리하지 못했습니다: " + ex.getMessage());
		}
	}

	/** 전에 받은 파일이 크기와 SHA-256까지 맞으면 그대로 쓰고, 아니면 받는다 */
	private static void fetch(ChannelClient client, ChannelSettings settings, ChannelFile file, Path download, PrintStream out) {
		try {
			if (Files.isRegularFile(download) && Files.size(download) == file.size()
					&& ChannelIndex.sha256(download).equalsIgnoreCase(file.sha256())) {
				out.println("[cozy] 전에 받은 파일을 씁니다. SHA-256이 채널 색인과 같습니다: " + download);
				return;
			}
		}
		catch (IOException ex) {
			// 다시 받는다
		}
		out.println("[cozy] 받는 중: " + file.name() + " (" + Math.max(1, file.size() / (1024 * 1024)) + "MB)");
		client.download(settings, file, download);
		out.println("[cozy] 받았습니다. 크기와 SHA-256이 채널 색인과 같습니다: " + download);
	}

	/** 받은 번들을 풀고 번들 폴더를 돌려준다. 그 판의 번들이 아니면 실패한다 */
	static Path unpack(Path zip, Path dir, boolean windows, Version version) {
		delete(dir);
		try {
			Files.createDirectories(dir);
		}
		catch (IOException ex) {
			throw new CliException(dir + "를 만들지 못했습니다: " + ex.getMessage());
		}
		if (windows) {
			extract(zip, dir.toAbsolutePath().normalize());
		}
		else {
			unzip(zip, dir);
		}
		List<Path> roots;
		try (Stream<Path> children = Files.list(dir)) {
			roots = children.filter((child) -> Files.isRegularFile(child.resolve(Installed.VERSION_FILE))).toList();
		}
		catch (IOException ex) {
			throw new CliException(dir + "를 읽지 못했습니다: " + ex.getMessage());
		}
		if (roots.size() != 1) {
			throw new CliException("받은 " + zip.getFileName() + "이 Cozy 번들이 아닙니다(" + Installed.VERSION_FILE + "가 있는 폴더 하나를 "
					+ "담지 않았습니다). 설치하지 않습니다.");
		}
		Path root = roots.getFirst();
		String found;
		try {
			found = Files.readString(root.resolve(Installed.VERSION_FILE)).strip();
		}
		catch (IOException ex) {
			throw new CliException(root.resolve(Installed.VERSION_FILE) + "을 읽지 못했습니다: " + ex.getMessage());
		}
		if (!found.equals(version.toString())) {
			throw new CliException("받은 번들의 판(" + found + ")이 채널 색인의 판(" + version + ")과 다릅니다. 설치하지 않습니다.");
		}
		String command = windows ? "cozy.ps1" : "cozy";
		if (!Files.isRegularFile(root.resolve(command))) {
			throw new CliException("받은 번들에 " + command + "가 없습니다. 설치하지 않습니다.");
		}
		return root;
	}

	/** zip을 푼다. 폴더 밖을 가리키는 항목이 있으면 실패한다 */
	static void extract(Path zip, Path dir) {
		try (ZipFile archive = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
			Enumeration<? extends ZipEntry> entries = archive.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				Path target = dir.resolve(entry.getName().replace('\\', '/')).normalize();
				if (!target.startsWith(dir) || target.equals(dir)) {
					throw new CliException(zip.getFileName() + "에 폴더 밖을 가리키는 항목(" + entry.getName() + ")이 있습니다. 설치하지 "
							+ "않습니다.");
				}
				if (entry.isDirectory()) {
					Files.createDirectories(target);
					continue;
				}
				Files.createDirectories(target.getParent());
				try (InputStream in = archive.getInputStream(entry)) {
					Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		catch (IOException ex) {
			throw new CliException("받은 " + zip.getFileName() + "을 풀지 못했습니다: " + ex.getMessage());
		}
	}

	/** Linux는 실행 권한(번들 JDK, 스크립트)을 지키는 unzip으로 푼다. cozy install도 unzip을 쓴다 */
	private static void unzip(Path zip, Path dir) {
		try {
			Process process = new ProcessBuilder("unzip", "-q", zip.toString(), "-d", dir.toString()).redirectErrorStream(true)
				.start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
			int code = process.waitFor();
			if (code != 0) {
				throw new CliException("받은 " + zip.getFileName() + "을 풀지 못했습니다(unzip 종료 코드 " + code + "): " + output);
			}
		}
		catch (IOException ex) {
			throw new CliException("받은 번들을 풀려면 unzip이 필요합니다(cozy install도 쓴다): " + ex.getMessage());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new CliException("받은 번들을 푸는 중에 멈췄습니다.");
		}
	}

	/**
	 * 번들 저장소를 떠 둔다. 하드 링크라 크기가 늘지 않고, MSI가 원래 파일을 지워도 남는다. 하드 링크를 만들 수 없는 곳(다른 드라이브 등)이면
	 * 복사한다. Maven의 잠금 파일은 뜨지 않는다.
	 */
	static void snapshot(Path repository, Path copy) {
		delete(copy);
		if (!Files.isDirectory(repository)) {
			throw new CliException("설치 폴더에 번들 저장소(" + repository + ")가 없습니다. 이전 판의 파일을 남길 수 없어 올리지 않습니다.");
		}
		try (Stream<Path> files = Files.walk(repository)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				Path relative = repository.relativize(file);
				if (relative.getNameCount() > 0 && relative.getName(0).toString().equals(".locks")) {
					continue;
				}
				Path target = copy.resolve(relative.toString());
				Files.createDirectories(target.getParent());
				try {
					Files.createLink(target, file);
				}
				catch (UnsupportedOperationException | IOException ex) {
					Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
				}
			}
		}
		catch (IOException ex) {
			delete(copy);
			throw new CliException("이전 판의 파일을 남기려고 번들 저장소를 뜨지 못했습니다(" + repository + " → " + copy + "): "
					+ ex.getMessage());
		}
	}

	static String shellScript(Path work, Path bundle, Path home, Path download, List<Long> waitFor, Version version) {
		return """
				#!/usr/bin/env bash
				# cozy upgrade가 만든 설치 스크립트. cozy 명령이 끝나면 받은 번들의 cozy install로 판(%1$s)을 설치하고 종료 코드를 result에 남긴다
				exec >> %2$s 2>&1
				echo "$$" > %3$s
				for pid in %4$s; do while kill -0 "$pid" 2>/dev/null; do sleep 0.2; done; done
				echo "[cozy] $(date '+%%F %%T') 판(%1$s)을 설치합니다: %5$s"
				code=0
				bash %6$s install %5$s < /dev/null || code=$?
				if [ "$code" -eq 0 ]; then rm -rf %7$s %8$s; fi
				echo "[cozy] $(date '+%%F %%T') 끝났습니다 (종료 코드 $code)"
				echo "$code" > %9$s
				""".formatted(version, sh(work.resolve("install.log")), sh(work.resolve("pid")),
				waitFor.stream().map(String::valueOf).collect(Collectors.joining(" ")), sh(home), sh(bundle.resolve("cozy")),
				sh(work.resolve("bundle")), sh(download), sh(work.resolve("result")));
	}

	static String zipScript(Path work, Path bundle, Path home, Path download, List<Long> waitFor, Version version) {
		return powershell(work, waitFor, version, """
				$out = Join-Path $work 'cozy-install.log'
				$err = Join-Path $work 'cozy-install-error.log'
				$p = Start-Process -FilePath 'powershell.exe' -ArgumentList ('-NoProfile -ExecutionPolicy Bypass -File "' + %1$s + '" install "' + %2$s + '"') -Wait -PassThru -NoNewWindow -RedirectStandardOutput $out -RedirectStandardError $err
				$code = $p.ExitCode
				if ($code -eq 0) {
				  Remove-Item -Recurse -Force -LiteralPath %3$s, %4$s -ErrorAction SilentlyContinue
				}
				else {
				  Write-Log ('cozy install 출력: ' + $out + ', ' + $err)
				}
				""".formatted(ps(bundle.resolve("cozy.ps1")), ps(home), ps(work.resolve("bundle")), ps(download)), "");
	}

	static String msiScript(Path work, Path msi, Path home, Path userRepository, List<Long> waitFor, Version version) {
		return powershell(work, waitFor, version, """
				$old = %2$s
				$new = %3$s
				$user = %4$s
				$p = Start-Process -FilePath 'msiexec.exe' -ArgumentList ('/i "' + %1$s + '" /qn /norestart /l*v "' + (Join-Path $work 'msiexec.log') + '"') -Wait -PassThru
				$code = $p.ExitCode
				# 다시 시작해야 끝나는 설치(3010, 1641)도 파일은 설치됐다
				if ($code -eq 3010 -or $code -eq 1641) { $code = 0 }
				if ($code -eq 0) {
				  # 설치하기 전의 번들 저장소에만 있던 파일(이전 판의 cozy 모듈, 그 판만 쓰는 의존성)을 사용자 저장소로 옮긴다
				  $moved = 0
				  $prefix = $old.TrimEnd('\\') + '\\'
				  foreach ($file in Get-ChildItem -LiteralPath $old -Recurse -File -Force) {
				    $relative = $file.FullName.Substring($prefix.Length)
				    $target = Join-Path $user $relative
				    if ((Test-Path -LiteralPath (Join-Path $new $relative)) -or (Test-Path -LiteralPath $target)) { continue }
				    New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
				    Move-Item -LiteralPath $file.FullName -Destination $target
				    $moved++
				  }
				  Write-Log ('이전 판에만 있던 파일 ' + $moved + '개를 사용자 저장소로 옮겼습니다: ' + $user)
				  Remove-Item -Force -LiteralPath %1$s -ErrorAction SilentlyContinue
				}
				else {
				  Write-Log ('msiexec 기록: ' + (Join-Path $work 'msiexec.log'))
				}
				""".formatted(ps(msi), ps(work.resolve("previous-repository")), ps(home.resolve("repository")), ps(userRepository)),
				"Remove-Item -Recurse -Force -LiteralPath (Join-Path $work 'previous-repository') -ErrorAction SilentlyContinue\n");
	}

	/** PowerShell 설치 스크립트의 틀: 기다리기, 설치, 결과 남기기 */
	private static String powershell(Path work, List<Long> waitFor, Version version, String install, String cleanup) {
		return """
				# cozy upgrade가 만든 설치 스크립트. cozy 명령이 끝나면 판(%1$s)을 설치하고 종료 코드를 result에 남긴다
				$ErrorActionPreference = 'Continue'
				$work = %2$s
				$log = Join-Path $work 'install.log'
				function Write-Log([string]$Text) {
				  Add-Content -LiteralPath $log -Encoding UTF8 -Value ('[cozy] ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' ' + $Text)
				}
				Set-Content -LiteralPath (Join-Path $work 'pid') -Value $PID -Encoding ASCII
				foreach ($id in @(%3$s)) { Wait-Process -Id $id -ErrorAction SilentlyContinue }
				# cozy.cmd를 읽는 cmd가 마지막 줄(exit /b)까지 읽기를 기다린다
				Start-Sleep -Seconds 2
				Write-Log '판(%1$s)을 설치합니다'
				$code = 1
				try {
				%4$s}
				catch {
				  Write-Log ('설치하지 못했습니다: ' + $_)
				}
				finally {
				  %5$s}
				Write-Log ('끝났습니다 (종료 코드 ' + $code + ')')
				Set-Content -LiteralPath (Join-Path $work 'result') -Value $code -Encoding ASCII
				""".formatted(version, ps(work), waitFor.stream().map(String::valueOf).collect(Collectors.joining(", ")),
				install.indent(2), cleanup);
	}

	private static String sh(Path path) {
		return "'" + path.toString().replace("'", "'\\''") + "'";
	}

	private static String ps(Path path) {
		return "'" + path.toString().replace("'", "''") + "'";
	}

	/** PowerShell 스크립트는 BOM 있는 UTF-8과 CRLF로 쓴다. Windows PowerShell 5.1은 BOM이 없으면 한글 경로를 시스템 코드 페이지로 읽는다 */
	private static Path write(Path file, String text, boolean powershell) {
		try {
			Files.createDirectories(file.getParent());
			String content = powershell ? "﻿" + text.replace("\r\n", "\n").replace("\n", "\r\n") : text;
			Files.writeString(file, content, StandardCharsets.UTF_8);
			return file;
		}
		catch (IOException ex) {
			throw new CliException("설치 스크립트 " + file + "를 쓰지 못했습니다: " + ex.getMessage());
		}
	}

	/**
	 * 맡긴 설치의 결과를 한 번 보인다. 끝났으면 결과를 보이고 기록을 지운다(성공하면 받은 파일도). 설치하는 중이면 그렇다고 보인다. 명령의
	 * 결과(출력, 종료 코드)를 바꾸지 않는다.
	 */
	public static void report(Map<String, String> env, boolean windows, Instant now, PrintStream err) {
		try {
			Path recordFile = UserConfig.file(env, windows).resolveSibling(RECORD_FILE);
			Record record = Record.read(recordFile);
			if (record == null) {
				return;
			}
			State state = record.state(now);
			if (state == State.RUNNING) {
				err.println("[cozy] 판(" + record.version() + ")을 설치하는 중입니다. 기록: " + record.log());
				return;
			}
			if (state == State.VANISHED) {
				err.println("[cozy] 판(" + record.version() + ")의 설치 스크립트가 결과를 남기지 않고 끝났습니다. 기록: " + record.log()
						+ ". 받은 파일로 손으로 설치할 수 있습니다: " + record.download());
			}
			else {
				int code = record.result();
				String installed = record.installedVersion();
				if (code == 0 && record.version().toString().equals(installed)) {
					err.println("[cozy] 판을 올렸습니다: " + record.from() + " → " + record.version() + ".");
					delete(record.work());
				}
				else if (code == 0) {
					err.println("[cozy] 설치 스크립트는 끝났지만 설치 폴더(" + record.home() + ")의 판이 " + installed + "입니다(올린 판 "
							+ record.version() + "). 기록: " + record.log());
				}
				else {
					err.println("[cozy] 판(" + record.version() + ")을 설치하지 못했습니다(종료 코드 " + code + "). 기록: " + record.log()
							+ ". cozy upgrade를 다시 하면 받은 파일을 그대로 씁니다. 손으로 설치할 수도 있습니다: " + record.download());
				}
			}
			Files.deleteIfExists(recordFile);
		}
		catch (Exception ex) {
			// 결과를 보이지 못해도 명령은 그대로 돈다. 기록은 다음 명령이 다시 본다
		}
	}

	enum State {
		/** 설치 스크립트가 돌고 있거나 아직 시작하지 않았다 */
		RUNNING,
		/** 종료 코드를 남겼다 */
		DONE,
		/** 결과를 남기지 않고 끝났다 */
		VANISHED
	}

	/**
	 * 설치를 맡긴 기록.
	 *
	 * @param version 올릴 판
	 * @param from 올리기 전의 판
	 * @param kind 받은 파일의 종류
	 * @param home 설치 폴더
	 * @param work 받은 파일과 설치 스크립트를 둔 폴더. 스크립트가 {@code pid}, {@code result}, {@code install.log}를 쓴다
	 * @param download 받은 파일
	 * @param started 맡긴 시각
	 */
	record Record(Version version, Version from, String kind, Path home, Path work, Path download, Instant started) {

		static Record read(Path file) {
			if (!Files.isRegularFile(file)) {
				return null;
			}
			Properties properties = UserConfig.read(file);
			try {
				return new Record(Version.parse(properties.getProperty("version", ""), "version"),
						Version.parse(properties.getProperty("from", ""), "from"), properties.getProperty("kind", ""),
						Path.of(properties.getProperty("home", "")), Path.of(properties.getProperty("work", "")),
						Path.of(properties.getProperty("file", "")), Instant.parse(properties.getProperty("started", "")));
			}
			catch (CliException | DateTimeParseException | IllegalArgumentException ex) {
				throw new CliException("설치 기록 " + file + "을 읽을 수 없습니다. 지우고 cozy upgrade를 다시 합니다.");
			}
		}

		void write(Path file) {
			Properties properties = new Properties();
			properties.setProperty("version", this.version.toString());
			properties.setProperty("from", this.from.toString());
			properties.setProperty("kind", this.kind);
			properties.setProperty("home", this.home.toString());
			properties.setProperty("work", this.work.toString());
			properties.setProperty("file", this.download.toString());
			properties.setProperty("started", this.started.toString());
			UserConfig.write(file, properties, "cozy upgrade가 맡긴 설치. 다음 cozy 명령이 결과를 보이고 지운다");
		}

		Path log() {
			return this.work.resolve("install.log");
		}

		State state(Instant now) {
			if (Files.isRegularFile(this.work.resolve("result"))) {
				return State.DONE;
			}
			Long pid = pid();
			if (pid != null) {
				return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false) ? State.RUNNING : State.VANISHED;
			}
			return now.isBefore(this.started.plus(START_GRACE)) ? State.RUNNING : State.VANISHED;
		}

		private Long pid() {
			try {
				return Long.valueOf(text(this.work.resolve("pid")));
			}
			catch (IOException | NumberFormatException ex) {
				return null;
			}
		}

		int result() throws IOException {
			try {
				return Integer.parseInt(text(this.work.resolve("result")));
			}
			catch (NumberFormatException ex) {
				return -1;
			}
		}

		String installedVersion() {
			try {
				return text(this.home.resolve(Installed.VERSION_FILE));
			}
			catch (IOException ex) {
				return "알 수 없음";
			}
		}

		private static String text(Path file) throws IOException {
			return Files.readString(file, StandardCharsets.UTF_8).replace("﻿", "").strip();
		}
	}

	private static void deleteIfExists(Path file) {
		try {
			Files.deleteIfExists(file);
		}
		catch (IOException ex) {
			throw new CliException(file + "을 지우지 못했습니다: " + ex.getMessage());
		}
	}

	static void delete(Path dir) {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(dir)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
		catch (IOException ex) {
			throw new CliException(dir + "를 지우지 못했습니다: " + ex.getMessage());
		}
	}
}
