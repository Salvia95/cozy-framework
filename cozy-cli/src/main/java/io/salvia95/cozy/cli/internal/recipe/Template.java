package io.salvia95.cozy.cli.internal.recipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 생성 템플릿: cozy-cli jar의 {@code recipes/} 아래 텍스트에 {@code {{이름}}}을 채운다. 템플릿 엔진 의존 없이 JDK만 쓴다(ADR-0032 §2).
 * 채울 값이 없는 이름이 남으면 실패한다(템플릿 오타를 조용히 넘기지 않는다).
 */
public final class Template {

	private static final Pattern NAME = Pattern.compile("\\{\\{([a-zA-Z0-9]+)}}");

	private final Map<String, String> values = new LinkedHashMap<>();

	public Template put(String name, Object value) {
		this.values.put(name, String.valueOf(value));
		return this;
	}

	public Template putAll(Template other) {
		this.values.putAll(other.values);
		return this;
	}

	public String render(String resource) {
		return fill(load(resource));
	}

	public String fill(String text) {
		Matcher m = NAME.matcher(text);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			String value = this.values.get(m.group(1));
			if (value == null) {
				throw new IllegalStateException("[cozy] 생성 템플릿에 채울 값이 없습니다: {{" + m.group(1) + "}}");
			}
			m.appendReplacement(out, Matcher.quoteReplacement(value));
		}
		m.appendTail(out);
		return out.toString();
	}

	static String load(String resource) {
		try (InputStream in = Template.class.getResourceAsStream("/recipes/" + resource)) {
			if (in == null) {
				throw new IllegalStateException("[cozy] 생성 템플릿이 없습니다: recipes/" + resource);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
