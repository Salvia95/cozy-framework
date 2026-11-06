package io.salvia95.cozy.runtime.internal.update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;

/**
 * 업데이트 매니페스트 (PRD §9.4). {@code -Pdist}가 만들고, 앱이 {@code cozy.update.manifest-url}에서 읽는다.
 *
 * <pre>{@code
 * {
 *   "appId": "voc-assistant",
 *   "version": "1.1.0",
 *   "minimumVersion": "1.0.0",
 *   "package": "voc-assistant-1.1.0-win-x64.zip",
 *   "sha256": "…",
 *   "releasedAt": "2026-11-20",
 *   "notes": "수정 사항 한 줄"
 * }
 * }</pre>
 *
 * 형식이 하나라도 맞지 않으면 매니페스트 전체를 거부한다.
 */
public record UpdateManifest(String appId, String version, @Nullable String minimumVersion, String packageName,
		String sha256, @Nullable String releasedAt, String notes) {

	/** 매니페스트와 같은 폴더의 파일 이름만 허용한다. 경로나 주소는 받지 않는다. */
	static final Pattern PACKAGE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*\\.zip");

	static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

	private static final JsonMapper MAPPER = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

	public static UpdateManifest parse(byte[] json, String source) {
		JsonNode root;
		try {
			root = MAPPER.readTree(json);
		}
		catch (RuntimeException ex) {
			throw new IllegalArgumentException("[cozy] 업데이트 매니페스트가 JSON이 아닙니다 (" + source + "): "
					+ ex.getMessage());
		}
		if (root == null || !root.isObject()) {
			throw new IllegalArgumentException("[cozy] 업데이트 매니페스트가 JSON 객체가 아닙니다 (" + source + ")");
		}
		List<String> problems = new ArrayList<>();
		String appId = text(root, "appId", true, problems);
		String version = text(root, "version", true, problems);
		String minimumVersion = text(root, "minimumVersion", false, problems);
		String packageName = text(root, "package", true, problems);
		String sha256 = text(root, "sha256", true, problems);
		String releasedAt = text(root, "releasedAt", false, problems);
		String notes = text(root, "notes", false, problems);
		if (version != null && !Versions.isValid(version)) {
			problems.add("version '" + version + "'이 버전 형식이 아닙니다");
		}
		if (minimumVersion != null && !Versions.isValid(minimumVersion)) {
			problems.add("minimumVersion '" + minimumVersion + "'이 버전 형식이 아닙니다");
		}
		if (packageName != null && !PACKAGE_NAME.matcher(packageName).matches()) {
			problems.add("package '" + packageName + "'는 매니페스트와 같은 폴더의 .zip 파일 이름이어야 합니다");
		}
		if (sha256 != null && !SHA256.matcher(sha256).matches()) {
			problems.add("sha256은 소문자 16진수 64자여야 합니다");
		}
		if (!problems.isEmpty()) {
			throw new IllegalArgumentException(
					"[cozy] 업데이트 매니페스트 형식 오류 (" + source + "): " + String.join("; ", problems));
		}
		return new UpdateManifest(appId, version, minimumVersion, packageName, sha256, releasedAt,
				(notes != null) ? notes : "");
	}

	/** {@code -Pdist}가 쓰는 JSON. 필드 순서는 PRD §9.4의 예와 같다. */
	public String toJson() {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("appId", this.appId);
		fields.put("version", this.version);
		if (this.minimumVersion != null) {
			fields.put("minimumVersion", this.minimumVersion);
		}
		fields.put("package", this.packageName);
		fields.put("sha256", this.sha256);
		if (this.releasedAt != null) {
			fields.put("releasedAt", this.releasedAt);
		}
		fields.put("notes", this.notes);
		return MAPPER.writeValueAsString(fields) + "\n";
	}

	private static @Nullable String text(JsonNode root, String field, boolean required, List<String> problems) {
		JsonNode node = root.get(field);
		if (node == null || node.isNull()) {
			if (required) {
				problems.add(field + "가 없습니다");
			}
			return null;
		}
		if (!node.isString()) {
			problems.add(field + "는 문자열이어야 합니다");
			return null;
		}
		String value = node.asString().strip();
		if (value.isEmpty()) {
			if (required) {
				problems.add(field + "가 비어 있습니다");
			}
			return null;
		}
		return value;
	}
}
