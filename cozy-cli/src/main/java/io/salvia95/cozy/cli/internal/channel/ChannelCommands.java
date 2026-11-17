package io.salvia95.cozy.cli.internal.channel;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.Json;
import io.salvia95.cozy.cli.internal.channel.ChannelIndex.ChannelFile;
import io.salvia95.cozy.cli.internal.channel.ChannelIndex.Release;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * {@code cozy channel}: 업데이트 채널을 정하고 보고({@code set}, {@code show}), 관리자가 채널 폴더를 만든다({@code add}).
 * {@code release}는 릴리스 워크플로가 릴리스 하나의 색인을 만들 때 쓴다. 이 명령들은 네트워크를 쓰지 않는다.
 */
public final class ChannelCommands {

	/** 채널 주소를 덮어쓰는 환경 변수 */
	public static final String CHANNEL_ENV = "COZY_CHANNEL";

	/** 인증 토큰 환경 변수 */
	public static final String TOKEN_ENV = "COZY_CHANNEL_TOKEN";

	/** 머리 이름을 정하지 않았을 때: {@code Authorization: Bearer <토큰>} */
	public static final String DEFAULT_HEADER = "Authorization: Bearer";

	static final String USAGE = "cozy channel set <주소> [--header <이름>] | show [--json] | add <채널 폴더> <릴리스 폴더> [--dry-run]"
			+ " | release <릴리스 폴더> <channel.yml>";

	/** 채널 폴더 안에서 색인을 두는 폴더. 판 폴더와 나란하다 */
	public static final String INDEX_DIR = "index";

	private static final Pattern WINDOWS_DRIVE = Pattern.compile("[A-Za-z]:\\\\.*");

	private ChannelCommands() {
	}

	/**
	 * @param args {@code channel} 뒤의 인자
	 * @param env 환경 변수
	 * @param windows Windows인가(설정 파일의 자리)
	 * @param today 릴리스 날짜
	 */
	public static void run(List<String> args, Map<String, String> env, boolean windows, LocalDate today, boolean dryRun,
			boolean json, PrintStream out) {
		String sub = args.isEmpty() ? "" : args.getFirst();
		List<String> rest = args.subList(Math.min(1, args.size()), args.size());
		switch (sub) {
			case "set" -> set(rest, UserConfig.file(env, windows), out);
			case "show" -> show(env, UserConfig.file(env, windows), json, out);
			case "add" -> add(rest, dryRun, out);
			case "release" -> release(rest, today, out);
			default -> throw new CliException("cozy channel 뒤에는 set, show, add, release를 적습니다: " + USAGE);
		}
	}

	private static void set(List<String> args, Path config, PrintStream out) {
		String address = null;
		String header = null;
		for (int i = 0; i < args.size(); i++) {
			if (args.get(i).equals("--header")) {
				if (i + 1 >= args.size() || args.get(i + 1).isBlank()) {
					throw new CliException("--header 뒤에 머리 이름(예: PRIVATE-TOKEN)을 적습니다.");
				}
				header = args.get(++i);
			}
			else if (address == null) {
				address = args.get(i);
			}
			else {
				throw new CliException("채널 주소는 하나만 적습니다: " + USAGE);
			}
		}
		if (address == null) {
			throw new CliException("채널 주소를 적습니다: cozy channel set <주소>. 주소는 " + ChannelIndex.FILE_NAME + "를 가리킵니다.");
		}
		check(address);
		Properties properties = UserConfig.read(config);
		properties.setProperty(UserConfig.CHANNEL, address);
		if (header != null) {
			properties.setProperty(UserConfig.HEADER, header);
		}
		else {
			properties.remove(UserConfig.HEADER);
		}
		UserConfig.write(config, properties);
		out.println("[cozy] 채널을 정했습니다: " + address);
		out.println("  설정 파일: " + config);
		if (header != null) {
			out.println("  토큰은 환경 변수 " + TOKEN_ENV + "나 설정 파일의 " + UserConfig.TOKEN + "에 둡니다(환경 변수를 권합니다).");
		}
		out.println("  확인: cozy channel show");
	}

	/** 주소의 형식: 파일 서버(UNC, file:, 절대 경로)나 웹 서버(http, https). 색인 파일 이름으로 끝난다 */
	static void check(String address) {
		boolean known = address.startsWith("\\\\") || address.startsWith("file:") || address.startsWith("http://")
				|| address.startsWith("https://") || address.startsWith("/") || WINDOWS_DRIVE.matcher(address).matches();
		if (!known) {
			throw new CliException("채널 주소 '" + address + "'의 형식을 모릅니다. 파일 서버(\\\\서버\\공유\\…, file:///…, 절대 경로)나 "
					+ "웹 서버(https://…, http://…)의 " + ChannelIndex.FILE_NAME + " 주소를 적습니다.");
		}
		String name = address.substring(Math.max(address.lastIndexOf('/'), address.lastIndexOf('\\')) + 1);
		if (!name.equals(ChannelIndex.FILE_NAME)) {
			throw new CliException("채널 주소 '" + address + "'가 " + ChannelIndex.FILE_NAME + "를 가리키지 않습니다. 채널 폴더라면 끝에 "
					+ ChannelIndex.FILE_NAME + "를 붙입니다.");
		}
	}

	private static void show(Map<String, String> env, Path config, boolean json, PrintStream out) {
		Properties properties = UserConfig.read(config);
		String fromEnv = env.get(CHANNEL_ENV);
		boolean useEnv = fromEnv != null && !fromEnv.isBlank();
		String address = useEnv ? fromEnv : properties.getProperty(UserConfig.CHANNEL);
		String header = properties.getProperty(UserConfig.HEADER, DEFAULT_HEADER);
		String tokenEnv = env.get(TOKEN_ENV);
		String tokenSource = (tokenEnv != null && !tokenEnv.isBlank()) ? "환경 변수 " + TOKEN_ENV
				: (properties.getProperty(UserConfig.TOKEN, "").isBlank() ? null : "설정 파일");
		if (json) {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("address", address);
			map.put("source", (address == null) ? null : (useEnv ? CHANNEL_ENV : config.toString()));
			map.put("header", header);
			map.put("token", tokenSource != null);
			map.put("config", config.toString());
			out.print(Json.write(map));
			return;
		}
		if (address == null) {
			out.println("[cozy] 채널을 정하지 않았습니다. 업데이트를 확인하지 않습니다.");
			out.println("  정하기: cozy channel set <주소> (파일 서버, 웹 서버, GitLab, GitHub의 " + ChannelIndex.FILE_NAME + ")");
			out.println("  설정 파일: " + config);
			return;
		}
		out.println("채널:   " + address);
		out.println("출처:   " + (useEnv ? "환경 변수 " + CHANNEL_ENV : "설정 파일 " + config));
		out.println("머리:   " + header);
		out.println("토큰:   " + ((tokenSource == null) ? "없음" : "있음 (" + tokenSource + ")"));
	}

	/**
	 * 받은 릴리스(릴리스 색인과 파일)를 채널 폴더의 {@code {판}/}에 복사하고, {@code index/cozy-channel.json}에 더한다. 색인은 마지막에
	 * 바꾼다. 색인을 판 폴더와 나란한 폴더에 두므로 GitLab Generic Package Registry(패키지 판 {@code index}와 {@code 2.1.0})에도 같은
	 * 모양으로 올린다.
	 */
	private static void add(List<String> args, boolean dryRun, PrintStream out) {
		if (args.size() != 2) {
			throw new CliException("cozy channel add <채널 폴더> <릴리스 폴더>로 적습니다. 릴리스 폴더는 릴리스에서 받은 파일과 "
					+ ChannelIndex.FILE_NAME + "가 있는 폴더입니다.");
		}
		Path channel = Path.of(args.get(0)).toAbsolutePath().normalize();
		Path releaseDir = Path.of(args.get(1)).toAbsolutePath().normalize();
		Path releaseIndexFile = releaseDir.resolve(ChannelIndex.FILE_NAME);
		if (!Files.isRegularFile(releaseIndexFile)) {
			throw new CliException(releaseDir + "에 " + ChannelIndex.FILE_NAME + "가 없습니다. 릴리스 자산의 " + ChannelIndex.FILE_NAME
					+ "를 다른 파일과 함께 받습니다(2.1.0부터 있습니다).");
		}
		ChannelIndex releaseIndex = ChannelIndex.read(releaseIndexFile);
		if (releaseIndex.releases().size() != 1) {
			throw new CliException(releaseIndexFile + "에 릴리스가 하나가 아닙니다(" + releaseIndex.releases().size()
					+ "개). 릴리스 자산의 색인은 그 릴리스 하나만 적습니다.");
		}
		Release release = releaseIndex.releases().getFirst();
		for (ChannelFile file : release.files()) {
			Path source = releaseDir.resolve(file.name());
			verify(source, file, releaseIndexFile.toString());
		}
		Path channelIndexFile = channel.resolve(INDEX_DIR).resolve(ChannelIndex.FILE_NAME);
		ChannelIndex index = ChannelIndex.read(channelIndexFile);
		String version = release.version().toString();
		Release existing = index.find(release.version());
		if (existing != null) {
			if (sameFiles(existing, release)) {
				out.println("[cozy] " + version + "은 이미 채널에 있습니다. 바꾼 것이 없습니다.");
				return;
			}
			throw new CliException("채널에 이미 있는 " + version + "과 다른 파일입니다. 게시한 릴리스의 파일은 바꾸지 않습니다. "
					+ "채널의 " + version + "을 지우고 다시 더하거나, 새 판으로 냅니다.");
		}
		List<ChannelFile> files = new ArrayList<>();
		for (ChannelFile file : release.files()) {
			files.add(new ChannelFile(file.kind(), "../" + version + "/" + file.name(), file.sha256(), file.size()));
		}
		ChannelIndex next = index.with(release.withFiles(List.copyOf(files)));
		if (dryRun) {
			out.println("[cozy] (dry-run) " + channel + "에 " + version + "을 더합니다:");
			files.forEach(file -> out.println("  복사 " + version + "/" + file.name()));
			out.println("  바꿈 " + INDEX_DIR + "/" + ChannelIndex.FILE_NAME);
			return;
		}
		try {
			Path target = Files.createDirectories(channel.resolve(version));
			for (ChannelFile file : files) {
				Path to = target.resolve(file.name());
				Path temp = target.resolve(file.name() + ".part");
				Files.copy(releaseDir.resolve(file.name()), temp, StandardCopyOption.REPLACE_EXISTING);
				verify(temp, file, releaseIndexFile.toString());
				Files.move(temp, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			Files.createDirectories(channelIndexFile.getParent());
			Path temp = channelIndexFile.resolveSibling(ChannelIndex.FILE_NAME + ".part");
			Files.writeString(temp, next.toJson());
			Files.move(temp, channelIndexFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException ex) {
			throw new CliException(channel + "에 쓰지 못했습니다: " + ex.getMessage() + ". 채널 색인은 바꾸지 않았습니다.");
		}
		out.println("[cozy] " + version + "을 채널에 더했습니다: " + channel);
		files.forEach(file -> out.println("  " + version + "/" + file.name()));
		out.println("  " + INDEX_DIR + "/" + ChannelIndex.FILE_NAME);
		out.println("  채널 주소: " + channelIndexFile);
		out.println("  웹 서버나 GitLab이면 이 폴더를 같은 모양으로 올립니다. 색인(" + INDEX_DIR + "/" + ChannelIndex.FILE_NAME
				+ ")은 파일을 다 올린 뒤에 올립니다.");
	}

	private static boolean sameFiles(Release a, Release b) {
		Map<String, String> left = new HashMap<>();
		a.files().forEach(file -> left.put(file.kind(), file.sha256()));
		Map<String, String> right = new HashMap<>();
		b.files().forEach(file -> right.put(file.kind(), file.sha256()));
		return left.equals(right);
	}

	private static void verify(Path file, ChannelFile expected, String where) {
		if (!Files.isRegularFile(file)) {
			throw new CliException(file + "이 없습니다(" + where + "에 적힌 파일).");
		}
		String actual = ChannelIndex.sha256(file);
		if (!actual.equals(expected.sha256())) {
			throw new CliException(file + "의 SHA-256이 " + where + "과 다릅니다(" + actual + "). 깨졌거나 다른 파일입니다. 다시 받습니다.");
		}
	}

	/** 릴리스 폴더의 세 파일로 릴리스 하나의 색인을 만든다. 파일은 색인 옆에 있다(GitHub Release의 자산) */
	private static void release(List<String> args, LocalDate today, PrintStream out) {
		if (args.size() != 2) {
			throw new CliException("cozy channel release <릴리스 폴더> <channel.yml>로 적습니다.");
		}
		Path dir = Path.of(args.get(0));
		Path notesFile = Path.of(args.get(1));
		Map<?, ?> notes = notes(notesFile);
		Version version = Version.parse(String.valueOf(notes.get("version")), notesFile.toString());
		Map<String, String> sums = sums(dir.resolve("SHA256SUMS"));
		List<ChannelFile> files = new ArrayList<>();
		Map<String, String> names = Map.of("msi", "cozy-" + version + "-windows-x64.msi", "windows-x64",
				"cozy-offline-bundle-" + version + "-windows-x64.zip", "portable", "cozy-offline-bundle-" + version + ".zip");
		for (String kind : ChannelIndex.KINDS) {
			String name = names.get(kind);
			Path file = dir.resolve(name);
			if (!Files.isRegularFile(file)) {
				throw new CliException(dir + "에 " + name + "이 없습니다. " + notesFile + "의 version: " + version
						+ "과 릴리스 파일의 판이 같은지 확인합니다.");
			}
			String sha256 = ChannelIndex.sha256(file);
			if (!sha256.equals(sums.get(name))) {
				throw new CliException(name + "의 SHA-256이 SHA256SUMS와 다릅니다(" + sha256 + ").");
			}
			try {
				files.add(new ChannelFile(kind, name, sha256, Files.size(file)));
			}
			catch (IOException ex) {
				throw new CliException(file + "을 읽지 못했습니다: " + ex.getMessage());
			}
		}
		if (!(notes.get("summary") instanceof String summary) || summary.isBlank() || summary.contains("\n")) {
			throw new CliException(notesFile + "에 summary(한 줄 요약)가 없습니다.");
		}
		if (!(notes.get("appSteps") instanceof List<?> steps) || !steps.stream().allMatch(String.class::isInstance)) {
			throw new CliException(notesFile + "에 appSteps(앱 폴더에서 돌릴 명령 목록, 없으면 [])가 없습니다.");
		}
		List<String> appSteps = steps.stream().map(String.class::cast).toList();
		Release release = new Release(version, today, version.breaking(), summary.strip(), appSteps, List.copyOf(files));
		Path index = dir.resolve(ChannelIndex.FILE_NAME);
		try {
			Files.writeString(index, new ChannelIndex(List.of(release)).toJson());
		}
		catch (IOException ex) {
			throw new CliException(index + "에 쓰지 못했습니다: " + ex.getMessage());
		}
		out.println("[cozy] 릴리스 색인을 만들었습니다: " + index + " (" + version + (version.breaking() ? ", 호환 깨짐" : "") + ")");
	}

	private static Map<?, ?> notes(Path file) {
		try {
			Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file));
			if (root instanceof Map<?, ?> map) {
				return map;
			}
		}
		catch (IOException | YAMLException ex) {
			throw new CliException(file + "을 읽지 못했습니다: " + ex.getMessage());
		}
		throw new CliException(file + "에 version, summary, appSteps가 없습니다.");
	}

	/** {@code sha256sum} 형식: {@code <해시>  <이름>} */
	private static Map<String, String> sums(Path file) {
		if (!Files.isRegularFile(file)) {
			throw new CliException(file + "이 없습니다. 릴리스 파일의 SHA256SUMS로 받은 파일을 확인합니다.");
		}
		Map<String, String> sums = new HashMap<>();
		Matcher line = Pattern.compile("(?m)^([0-9a-f]{64}) [ *](.+)$").matcher(read(file));
		while (line.find()) {
			sums.put(line.group(2).strip(), line.group(1));
		}
		return sums;
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		}
		catch (IOException ex) {
			throw new CliException(file + "을 읽지 못했습니다: " + ex.getMessage());
		}
	}
}
