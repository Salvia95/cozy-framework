package io.salvia95.cozy.ui.internal;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import io.salvia95.cozy.ui.CozyViews;

/**
 * 컨트롤러와 뷰의 모듈 일치 (PRD §6.5 검사 7, §10 컨트롤러 규약).
 *
 * <p>핸들러 클래스의 모듈(메인 패키지 바로 아래 패키지, 또는 {@code @ApplicationModule(id)})과 반환한 뷰 이름의 첫 경로가
 * 다르면 위반이다. {@code cozy.ui.view-module-check=fail}(cozy-test가 테스트에서 켠다)이면 요청을 실패시키고,
 * {@code warn}(기본값, 운영)이면 경고 로그만 남긴다.
 */
public class ViewModuleInterceptor implements HandlerInterceptor {

	public static final String PROPERTY = "cozy.ui.view-module-check";

	private static final Log logger = LogFactory.getLog(ViewModuleInterceptor.class);

	private static final String MODULITH_ANNOTATION = "org.springframework.modulith.ApplicationModule";

	public enum Mode {

		WARN, FAIL

	}

	private final List<String> basePackages;

	private final Mode mode;

	private final Map<String, String> moduleIds = new ConcurrentHashMap<>();

	public ViewModuleInterceptor(List<String> basePackages, Mode mode) {
		this.basePackages = List.copyOf(basePackages);
		this.mode = mode;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) {
		if (!(handler instanceof HandlerMethod method) || modelAndView == null || modelAndView.getViewName() == null) {
			return;
		}
		String view = modelAndView.getViewName();
		if (view.startsWith("redirect:") || view.startsWith("forward:") || view.equals(CozyViews.EMPTY)) {
			return;
		}
		String problem = check(method.getBeanType(), view);
		if (problem == null) {
			return;
		}
		String message = "[C7] " + method.getBeanType().getName() + "#" + method.getMethod().getName() + " → 뷰 \""
				+ view + "\" — " + problem;
		if (this.mode == Mode.FAIL) {
			throw new IllegalStateException(message);
		}
		logger.warn(message);
	}

	/** @return 위반이면 고치는 방법이 담긴 설명, 아니면 null */
	String check(Class<?> handlerType, String view) {
		String pkg = handlerType.getPackageName();
		String base = this.basePackages.stream().filter((b) -> pkg.startsWith(b + ".")).findFirst().orElse(null);
		if (base == null) {
			return this.basePackages.contains(pkg)
					? "화면 컨트롤러는 메인 패키지가 아니라 모듈 패키지(" + pkg + ".{모듈})에 둡니다." : null;
		}
		String modulePackage = base + "." + pkg.substring(base.length() + 1).split("\\.")[0];
		String module = this.moduleIds.computeIfAbsent(modulePackage, (p) -> moduleId(p, handlerType.getClassLoader()));
		String templatePath = view.contains("::") ? view.substring(0, view.indexOf("::")).strip() : view;
		int slash = templatePath.indexOf('/');
		String first = (slash > 0) ? templatePath.substring(0, slash) : null;
		if (module.equals(first)) {
			return null;
		}
		return module + " 모듈의 컨트롤러는 " + module + "/로 시작하는 뷰를 반환합니다. 템플릿을 templates/" + module
				+ "/로 옮기거나, 컨트롤러를 " + ((first != null) ? first : "알맞은") + " 모듈로 옮기세요.";
	}

	/** 모듈 이름: {@code @ApplicationModule(id)}가 있으면 그것, 없으면 패키지의 단순 이름 (Modulith 기본 규칙) */
	private static String moduleId(String modulePackage, ClassLoader classLoader) {
		String simple = modulePackage.substring(modulePackage.lastIndexOf('.') + 1);
		try {
			Class<?> info = Class.forName(modulePackage + ".package-info", false, classLoader);
			for (Annotation annotation : info.getAnnotations()) {
				if (annotation.annotationType().getName().equals(MODULITH_ANNOTATION)) {
					Object id = annotation.annotationType().getMethod("id").invoke(annotation);
					if (id instanceof String s && !s.isBlank()) {
						return s;
					}
				}
			}
		}
		catch (ReflectiveOperationException ex) {
			// package-info가 없으면 기본 규칙
		}
		return simple;
	}
}
