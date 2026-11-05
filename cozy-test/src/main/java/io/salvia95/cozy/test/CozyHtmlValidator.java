package io.salvia95.cozy.test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nu.validator.client.EmbeddedValidator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Nu Html Checker(vnu)로 렌더링 결과를 검사한다 (PRD §6.5 검사 4). 네트워크 없이 jar 안의 스키마만 쓴다.
 *
 * <p>htmx 속성은 HTML 표준 밖이라 vnu가 오류로 본다. Cozy가 쓰는 htmx 속성 이름만 허용 목록으로 걸러낸다. 목록에 없는
 * {@code hx-*}(오타, 인라인 스크립트인 {@code hx-on})는 그대로 오류다 (docs/adr/0022).
 */
public final class CozyHtmlValidator {

	/** 허용하는 htmx 속성 (htmx 2.x 문서의 속성 중 스크립트를 실행하지 않는 것) */
	public static final Set<String> HTMX_ATTRIBUTES = Set.of("hx-get", "hx-post", "hx-put", "hx-patch", "hx-delete",
			"hx-target", "hx-swap", "hx-swap-oob", "hx-trigger", "hx-include", "hx-push-url", "hx-replace-url",
			"hx-confirm", "hx-indicator", "hx-select", "hx-select-oob", "hx-vals", "hx-boost", "hx-disabled-elt",
			"hx-disable", "hx-sync", "hx-params", "hx-headers", "hx-encoding", "hx-ext", "hx-history",
			"hx-history-elt", "hx-inherit", "hx-disinherit", "hx-preserve", "hx-prompt", "hx-request", "hx-validate");

	private static final Pattern ATTRIBUTE_NOT_ALLOWED = Pattern
		.compile("Attribute “(hx-[^”]+)” not allowed on element “[^”]+” at this point\\.");

	private static final JsonMapper JSON = new JsonMapper();

	/**
	 * @param type {@code error}, {@code warning}
	 * @param line 렌더링 결과의 줄
	 * @param message vnu 메시지
	 * @param extract 문제 부분 발췌
	 */
	public record Message(String type, int line, String message, String extract) {

		public boolean isError() {
			return this.type.equals("error") || this.type.equals("non-document-error");
		}

		public String format() {
			return this.type + " " + this.line + "행: " + this.message
					+ ((this.extract != null) ? " [" + this.extract.replace('\n', ' ').strip() + "]" : "");
		}
	}

	/** 완전한 HTML 문서를 검사한다 */
	public List<Message> validate(String html) {
		String json;
		try {
			EmbeddedValidator validator = new EmbeddedValidator();
			validator.setOutputFormat(EmbeddedValidator.OutputFormat.JSON);
			json = validator.validate(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			return List.of(new Message("non-document-error", 0, "vnu가 문서를 검사하지 못했습니다: " + ex, null));
		}
		List<Message> messages = new ArrayList<>();
		for (JsonNode node : JSON.readTree(json).path("messages")) {
			String type = node.path("type").asString("");
			String subType = node.path("subType").asString("");
			if (type.equals("info") && !subType.equals("warning")) {
				continue;
			}
			String message = node.path("message").asString("");
			Matcher attribute = ATTRIBUTE_NOT_ALLOWED.matcher(message);
			if (attribute.matches() && HTMX_ATTRIBUTES.contains(attribute.group(1))) {
				continue;
			}
			String effective = type.equals("info") ? "warning" : type;
			messages.add(new Message(effective, node.path("lastLine").asInt(0), message,
					node.has("extract") ? node.path("extract").asString() : null));
		}
		return messages;
	}

	/** 부분 갱신 fragment를 최소 문서로 감싸 검사한다. 표 행 등은 알맞은 부모 요소로 감싼다 */
	public List<Message> validateFragment(String fragment) {
		String trimmed = fragment.stripLeading().toLowerCase();
		String open = "";
		String close = "";
		if (trimmed.startsWith("<tr")) {
			open = "<table><tbody>";
			close = "</tbody></table>";
		}
		else if (trimmed.startsWith("<td") || trimmed.startsWith("<th")) {
			open = "<table><tbody><tr>";
			close = "</tr></tbody></table>";
		}
		else if (trimmed.startsWith("<li")) {
			open = "<ul>";
			close = "</ul>";
		}
		else if (trimmed.startsWith("<option")) {
			open = "<select aria-label=\"fragment\">";
			close = "</select>";
		}
		return validate("<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"utf-8\"><title>fragment</title></head><body>"
				+ open + fragment + close + "</body></html>");
	}
}
