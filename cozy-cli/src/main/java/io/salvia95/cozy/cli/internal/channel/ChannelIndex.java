package io.salvia95.cozy.cli.internal.channel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.Json;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * 채널 색인 {@code cozy-channel.json}. 채널에 있는 릴리스와 그 파일(색인 주소 기준의 상대 경로, SHA-256, 크기)을 적는다.
 * 모르는 {@code schema}, 빠지거나 형식이 틀린 필드, 같은 판이 두 번 있는 색인은 읽지 않는다. 모르는 키는 건너뛴다.
 *
 * @param releases 새 판부터
 */
public record ChannelIndex(List<Release> releases) {

	/** 색인 파일 이름. 채널 주소는 이 이름으로 끝난다 */
	public static final String FILE_NAME = "cozy-channel.json";

	/** 지금 쓰는 색인 형식 */
	public static final int SCHEMA = 1;

	/** 파일 종류: MSI, JDK를 담은 Windows x64 번들, JDK 없는 번들 */
	public static final List<String> KINDS = List.of("msi", "windows-x64", "portable");

	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

	/**
	 * @param version 판
	 * @param date 릴리스 날짜
	 * @param breaking 앱을 올릴 때 손이 가는 판(주 버전이 바뀐 판)
	 * @param summary 한 줄 요약
	 * @param appSteps 앱 폴더에서 돌릴 명령
	 * @param files 받을 파일
	 */
	public record Release(Version version, LocalDate date, boolean breaking, String summary, List<String> appSteps,
			List<ChannelFile> files) {

		public Release withFiles(List<ChannelFile> files) {
			return new Release(this.version, this.date, this.breaking, this.summary, this.appSteps, files);
		}
	}

	/**
	 * @param kind {@link #KINDS} 중 하나
	 * @param path 색인 주소 기준의 상대 경로
	 * @param sha256 소문자 16진수
	 * @param size 바이트
	 */
	public record ChannelFile(String kind, String path, String sha256, long size) {

		/** 경로의 마지막 이름 */
		public String name() {
			return this.path.substring(this.path.lastIndexOf('/') + 1);
		}
	}

	public static ChannelIndex empty() {
		return new ChannelIndex(List.of());
	}

	/** 판이 같은 릴리스 */
	public Release find(Version version) {
		return this.releases.stream().filter(release -> release.version().equals(version)).findFirst().orElse(null);
	}

	/** 릴리스를 더한 새 색인. 새 판부터 놓는다 */
	public ChannelIndex with(Release release) {
		List<Release> all = new ArrayList<>(this.releases);
		all.add(release);
		all.sort(Comparator.comparing(Release::version).reversed());
		return new ChannelIndex(List.copyOf(all));
	}

	/** 파일에서 읽는다. 없으면 빈 색인이다 */
	public static ChannelIndex read(Path file) {
		if (!Files.exists(file)) {
			return empty();
		}
		try {
			return parse(Files.readString(file), file.toString());
		}
		catch (IOException ex) {
			throw new CliException(file + "을 읽지 못했습니다: " + ex.getMessage());
		}
	}

	/**
	 * @param where 메시지에 보일 출처(파일이나 주소)
	 */
	public static ChannelIndex parse(String text, String where) {
		Object root;
		try {
			root = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
		}
		catch (YAMLException ex) {
			throw invalid(where, "JSON으로 읽을 수 없습니다");
		}
		Map<?, ?> map = map(root, where, "색인");
		Object schema = map.get("schema");
		if (!(schema instanceof Integer number) || number != SCHEMA) {
			throw invalid(where, "schema가 " + SCHEMA + "이 아닙니다(지금 " + schema + "). 이 cozy 명령이 읽는 형식은 " + SCHEMA
					+ "입니다. 채널이 더 새로운 형식이면 cozy를 먼저 새 판으로 설치하세요");
		}
		if (!(map.get("releases") instanceof List<?> list)) {
			throw invalid(where, "releases 목록이 없습니다");
		}
		List<Release> releases = new ArrayList<>();
		Set<Version> seen = new HashSet<>();
		for (Object item : list) {
			Release release = release(map(item, where, "releases의 항목"), where);
			if (!seen.add(release.version())) {
				throw invalid(where, "판 " + release.version() + "이 두 번 있습니다");
			}
			releases.add(release);
		}
		releases.sort(Comparator.comparing(Release::version).reversed());
		return new ChannelIndex(List.copyOf(releases));
	}

	private static Release release(Map<?, ?> map, String where) {
		Version version = Version.parse(string(map, "version", where), where);
		String at = where + "의 " + version;
		LocalDate date;
		try {
			date = LocalDate.parse(string(map, "date", at));
		}
		catch (DateTimeParseException ex) {
			throw invalid(at, "date가 yyyy-MM-dd가 아닙니다");
		}
		if (!(map.get("breaking") instanceof Boolean breaking)) {
			throw invalid(at, "breaking(true나 false)이 없습니다");
		}
		String summary = string(map, "summary", at);
		if (!(map.get("appSteps") instanceof List<?> steps) || !steps.stream().allMatch(String.class::isInstance)) {
			throw invalid(at, "appSteps(문자열 목록)가 없습니다");
		}
		if (!(map.get("files") instanceof List<?> files) || files.isEmpty()) {
			throw invalid(at, "files 목록이 없습니다");
		}
		List<ChannelFile> channelFiles = new ArrayList<>();
		Set<String> kinds = new HashSet<>();
		for (Object item : files) {
			Map<?, ?> file = map(item, at, "files의 항목");
			String kind = string(file, "kind", at);
			if (!KINDS.contains(kind) || !kinds.add(kind)) {
				throw invalid(at, "파일 종류 '" + kind + "'를 모르거나 두 번 있습니다(" + String.join(", ", KINDS) + ")");
			}
			String path = string(file, "path", at);
			if (path.startsWith("/") || path.contains("\\") || path.contains(":") || path.endsWith("/")) {
				throw invalid(at, "path '" + path + "'는 색인 기준의 상대 경로가 아닙니다");
			}
			String sha256 = string(file, "sha256", at);
			if (!SHA256.matcher(sha256).matches()) {
				throw invalid(at, path + "의 sha256이 소문자 16진수 64자가 아닙니다");
			}
			if (!(file.get("size") instanceof Number size) || size.longValue() <= 0) {
				throw invalid(at, path + "의 size가 없습니다");
			}
			channelFiles.add(new ChannelFile(kind, path, sha256, size.longValue()));
		}
		@SuppressWarnings("unchecked")
		List<String> appSteps = List.copyOf((List<String>) steps);
		return new Release(version, date, breaking, summary, appSteps, List.copyOf(channelFiles));
	}

	/** 같은 글자가 나오는 JSON. 키 순서는 형식 설명과 같다 */
	public String toJson() {
		List<Object> releases = new ArrayList<>();
		for (Release release : this.releases) {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("version", release.version().toString());
			map.put("date", release.date().toString());
			map.put("breaking", release.breaking());
			map.put("summary", release.summary());
			map.put("appSteps", release.appSteps());
			List<Object> files = new ArrayList<>();
			for (ChannelFile file : release.files()) {
				Map<String, Object> f = new LinkedHashMap<>();
				f.put("kind", file.kind());
				f.put("path", file.path());
				f.put("sha256", file.sha256());
				f.put("size", file.size());
				files.add(f);
			}
			map.put("files", files);
			releases.add(map);
		}
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("schema", SCHEMA);
		root.put("releases", releases);
		return Json.write(root);
	}

	/** 파일의 SHA-256(소문자 16진수) */
	public static String sha256(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[1 << 16];
			for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
				digest.update(buffer, 0, n);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (IOException ex) {
			throw new CliException(file + "을 읽지 못했습니다: " + ex.getMessage());
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static Map<?, ?> map(Object value, String where, String what) {
		if (!(value instanceof Map<?, ?> map)) {
			throw invalid(where, what + "이 JSON 객체가 아닙니다");
		}
		return map;
	}

	private static String string(Map<?, ?> map, String key, String where) {
		if (!(map.get(key) instanceof String value) || value.isBlank()) {
			throw invalid(where, key + "(문자열)가 없습니다");
		}
		return value;
	}

	private static CliException invalid(String where, String reason) {
		return new CliException("채널 색인 " + where + "을 읽을 수 없습니다: " + reason + ". " + FILE_NAME
				+ "의 형식은 docs/conventions/channel.md에 있습니다.");
	}
}
