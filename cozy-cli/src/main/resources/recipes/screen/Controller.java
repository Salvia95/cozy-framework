package {{pkg}};

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import {{viewImport}};

/**
 * {{title}} 화면 (cozy add screen). 값은 spi의 Finder로 읽어 {@link {{View}}}로 바꾼 뒤 모델 view로 넘긴다. 뷰는 {{viewName}}이다.
 * 다른 모듈의 Facade(쓰기)는 부르지 않는다(C8).
 */
@Controller
@RequestMapping("{{path}}")
class {{Controller}} {

	@GetMapping
	String {{method}}(Model model) {
		// cozy:slot view-model — Finder로 값을 모아 {{View}}를 만들고 model에 view라는 이름으로 넣으세요. 돌려줄 뷰: {{viewName}}. 쓸 수 있는 Finder는 cozy describe로 봅니다
		throw new UnsupportedOperationException("cozy:slot view-model");
	}
}
