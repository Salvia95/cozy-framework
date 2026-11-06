package io.salvia95.cozy.ui.testapp;

import java.util.List;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.salvia95.cozy.ui.CozyFlash;
import io.salvia95.cozy.ui.CozyNavItem;
import io.salvia95.cozy.ui.CozyPage;
import io.salvia95.cozy.ui.CozyViews;
import io.salvia95.cozy.ui.HtmxRequest;

@SpringBootApplication
public class UiTestApp {

	@Bean
	CozyNavItem second() {
		return new CozyNavItem("둘째", "/second", 20, "file");
	}

	@Bean
	CozyNavItem first() {
		return new CozyNavItem("항목", "/uitest", 10, "list");
	}

	@Controller
	static class ItemController {

		@GetMapping("/uitest")
		String list(@RequestParam(required = false) String q, Model model) {
			List<String> items = List.of("가", "나", "다").stream().filter((i) -> q == null || i.contains(q)).toList();
			model.addAttribute("q", q);
			model.addAttribute("items", items);
			model.addAttribute("page", CozyPage.of(1, 10, items.size()));
			return CozyViews.pageOrFragment("uitest/api/list", "result");
		}

		@PostMapping("/uitest")
		String save(RedirectAttributes flash) {
			flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("저장했습니다"));
			return CozyViews.redirect("/uitest");
		}

		@GetMapping("/uitest/htmx")
		@ResponseBody
		String htmx(HtmxRequest htmx) {
			return htmx.htmx() + "|" + htmx.partial() + "|" + htmx.target() + "|" + htmx.trigger();
		}

		@GetMapping("/uitest/missing")
		String missing() {
			throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
		}

	}
}
