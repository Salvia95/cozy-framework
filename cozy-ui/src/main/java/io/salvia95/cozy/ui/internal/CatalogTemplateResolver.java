package io.salvia95.cozy.ui.internal;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.AbstractTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.StringTemplateResource;

import io.salvia95.cozy.ui.catalog.FragmentManifest;
import io.salvia95.cozy.ui.catalog.FragmentManifest.Entry;
import io.salvia95.cozy.ui.catalog.FragmentManifest.Param;

/**
 * 카탈로그 미리보기용 템플릿을 매니페스트에서 만든다 (PRD §6.4).
 *
 * <p>템플릿 이름 {@code cozy-catalog:{id}|{state}}를 fragment 호출 한 개짜리 템플릿으로 바꾼다. 값 파라미터는 모델의
 * {@code cozySample}에서, fragment 파라미터는 자리 표시 fragment로 채운다. 템플릿 파일에 동적 참조를 두지 않기 위해(R7)
 * 이 일을 Java가 맡는다.
 */
public class CatalogTemplateResolver extends AbstractTemplateResolver {

	public static final String PREFIX = "cozy-catalog:";

	private final FragmentManifest manifest;

	public CatalogTemplateResolver(FragmentManifest manifest) {
		this.manifest = manifest;
		setName("cozy-catalog");
		setOrder(0);
		setResolvablePatterns(Set.of(PREFIX + "*"));
	}

	public static String viewName(String id, String state) {
		return PREFIX + id + "|" + state;
	}

	@Override
	protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate,
			String template, Map<String, Object> templateResolutionAttributes) {
		String spec = template.substring(PREFIX.length());
		int bar = spec.lastIndexOf('|');
		Entry entry = this.manifest.require(spec.substring(0, bar));
		String state = spec.substring(bar + 1);
		if (!entry.samples().containsKey(state)) {
			throw new IllegalArgumentException("[cozy] " + entry.id() + "에 상태 '" + state + "'가 없습니다");
		}
		return new StringTemplateResource(markup(entry, entry.samples().get(state)));
	}

	static String markup(Entry entry, Map<String, Object> sample) {
		String args = entry.params()
			.stream()
			.map((param) -> argument(param, sample.get(param.name())))
			.collect(Collectors.joining(", "));
		String call = "~{" + entry.template() + " :: " + entry.fragmentName() + "(" + args + ")}";
		if (entry.document()) {
			return "<!DOCTYPE html><html th:replace=\"" + call + "\"></html>";
		}
		String body = "<div th:replace=\"" + call + "\"></div>";
		if (sample.containsKey(CatalogController.FORM_KEY)) {
			body = "<form th:object=\"${cozySampleForm}\">" + body + "</form>";
		}
		return "<!DOCTYPE html><html th:replace=\"~{cozy/layout/base :: base('" + entry.id()
				+ "', ~{::main})}\"><main class=\"container-xl py-4\">" + body + "</main></html>";
	}

	/**
	 * fragment 파라미터: 샘플 값이 있으면 그 fragment({@code template :: selector}, 템플릿을 생략하면 cozy/dev/catalog),
	 * 없으면 이름을 보여 주는 자리 표시.
	 */
	private static String argument(Param param, Object sampleValue) {
		if (param.isFragment()) {
			if (sampleValue instanceof String selector && !selector.isBlank()) {
				String target = selector.contains("::") ? selector : "cozy/dev/catalog :: " + selector;
				return param.name() + "=~{" + target + "}";
			}
			return param.name() + "=~{cozy/dev/catalog :: placeholder('" + param.name() + "')}";
		}
		return param.name() + "=${cozySample['" + param.name() + "']}";
	}

	@Override
	protected TemplateMode computeTemplateMode(IEngineConfiguration configuration, String ownerTemplate,
			String template, Map<String, Object> templateResolutionAttributes) {
		return TemplateMode.HTML;
	}

	@Override
	protected org.thymeleaf.cache.ICacheEntryValidity computeValidity(IEngineConfiguration configuration,
			String ownerTemplate, String template, Map<String, Object> templateResolutionAttributes) {
		return org.thymeleaf.cache.NonCacheableCacheEntryValidity.INSTANCE;
	}
}
