package io.salvia95.cozy.workbench.internal;

import java.nio.file.Path;
import java.util.Map;

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;

/**
 * 앱의 템플릿을 소스 폴더({@code src/main/resources/templates/})에서 먼저 읽는다. 워크벤치가 적용한 템플릿이 새로고침만으로 미리보기와 화면에
 * 보인다. 캐시하지 않고, 소스에 없는 템플릿(프레임워크 템플릿 {@code cozy/…}, 카탈로그의 미리보기)은 다음 해석기(빌드 결과)가 읽는다.
 *
 * <p>
 * 워크벤치가 켜진 dev 실행에서 {@code cozy.dev.workbench.live-resources}가 {@code false}가 아닐 때만 쓴다.
 */
public class SourceTemplateResolver extends FileTemplateResolver {

	/** 카탈로그 미리보기(0) 다음, 빌드 결과의 해석기 앞 */
	public static final int ORDER = 1;

	private final boolean available;

	/** @param templates 앱의 {@code src/main/resources/templates}. 앱 프로젝트 폴더를 찾지 못했으면 {@code null}(아무것도 읽지 않는다) */
	public SourceTemplateResolver(Path templates) {
		this.available = templates != null;
		setName("cozy-workbench-source");
		setOrder(ORDER);
		setPrefix(this.available ? templates.toAbsolutePath().normalize() + "/" : "");
		setSuffix(".html");
		setTemplateMode(TemplateMode.HTML);
		setCharacterEncoding("UTF-8");
		setCacheable(false);
		setCheckExistence(true);
	}

	@Override
	protected boolean computeResolvable(IEngineConfiguration configuration, String ownerTemplate, String template,
			Map<String, Object> templateResolutionAttributes) {
		// 프레임워크 템플릿과 이름이 아닌 것(카탈로그 cozy-catalog:…, 경로를 벗어나는 이름)은 보지 않는다
		return this.available && !template.startsWith("cozy/") && !template.contains(":") && !template.contains("..") && !template.startsWith("/")
				&& super.computeResolvable(configuration, ownerTemplate, template, templateResolutionAttributes);
	}
}
