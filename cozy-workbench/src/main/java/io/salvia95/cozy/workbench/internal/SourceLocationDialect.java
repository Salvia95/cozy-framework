package io.salvia95.cozy.workbench.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.context.IWebContext;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.IWebRequest;

/**
 * 워크벤치 미리보기의 요소마다 소스 위치를 싣는다: {@code data-cozy-src="템플릿:줄"}. 워크벤치가 요소에서 소스를 찾는 데 쓴다.
 *
 * <p>
 * 화면 미리보기 경로({@code /cozy/dev/catalog/screen})를 {@code inspect} 파라미터와 함께 부를 때만 싣는다. 그 밖의 요청(앱 화면, 렌더링 검사가
 * 부르는 미리보기)의 HTML은 달라지지 않는다. 줄 번호는 파일의 실제 줄이다({@link SourceLines}).
 */
public class SourceLocationDialect extends AbstractProcessorDialect {

	public static final String ATTRIBUTE = "data-cozy-src";

	/** 위치를 싣는 미리보기 경로 */
	public static final String PREVIEW_PATH = "/cozy/dev/catalog/screen";

	/** 위치를 실어 달라는 요청 파라미터 */
	public static final String PARAMETER = "inspect";

	/** 표준 처리기(th:*)가 끝난 뒤에 돈다 */
	private static final int PRECEDENCE = 100_000;

	public SourceLocationDialect() {
		super("Cozy workbench source locations", "cozysrc", PRECEDENCE);
	}

	@Override
	public Set<IProcessor> getProcessors(String dialectPrefix) {
		return Set.of(new Processor(dialectPrefix));
	}

	/** 모든 요소에 맞는다 */
	private static final class Processor extends AbstractElementTagProcessor {

		Processor(String dialectPrefix) {
			super(TemplateMode.HTML, dialectPrefix, null, false, null, false, PRECEDENCE);
		}

		@Override
		protected void doProcess(ITemplateContext context, IProcessableElementTag tag, IElementTagStructureHandler handler) {
			if (tag.getTemplateName() == null || tag.getLine() < 1 || !asked(context)) {
				return;
			}
			handler.setAttribute(ATTRIBUTE, tag.getTemplateName() + ":" + lines(context, tag.getTemplateName()).actual(tag.getLine()));
		}

		/** 템플릿의 줄 보정. 요청 하나 동안만 기억한다 (dev에서는 템플릿이 바뀔 수 있다) */
		private static SourceLines lines(ITemplateContext context, String template) {
			IWebExchange exchange = ((IWebContext) context).getExchange();
			String key = SourceLines.class.getName() + "." + template;
			if (exchange.getAttributeValue(key) instanceof SourceLines known) {
				return known;
			}
			SourceLines lines = SourceLines.of(read(template));
			exchange.setAttributeValue(key, lines);
			return lines;
		}

		/** 클래스패스의 템플릿 원문. 읽지 못하면 빈 문자열(줄을 보정하지 않는다) */
		private static String read(String template) {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			try (InputStream in = (loader != null) ? loader.getResourceAsStream("templates/" + template + ".html") : null) {
				return (in != null) ? new String(in.readAllBytes(), StandardCharsets.UTF_8) : "";
			}
			catch (IOException ex) {
				return "";
			}
		}

		private static boolean asked(ITemplateContext context) {
			if (!(context instanceof IWebContext web)) {
				return false;
			}
			IWebRequest request = web.getExchange().getRequest();
			return PREVIEW_PATH.equals(request.getPathWithinApplication()) && request.getParameterValue(PARAMETER) != null;
		}
	}
}
