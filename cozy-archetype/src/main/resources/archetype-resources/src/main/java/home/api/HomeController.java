package ${package}.home;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ${package}.home.view.HomeViews;
import ${package}.home.view.NoteFilter;
import ${package}.home.view.NoteForm;
import io.salvia95.cozy.ui.CozyFlash;
import io.salvia95.cozy.ui.CozyViews;

/**
 * 홈 화면. 뷰 이름은 모듈 이름(home/)으로 시작한다.
 *
 * <ul>
 * <li>목록: 전체 페이지와 htmx 필터 갱신을 같은 엔드포인트에서 pageOrFragment로 나눈다.</li>
 * <li>등록: 폼 클래스 + @Valid + BindingResult. 오류면 폼 fragment만 다시 그리고, 성공하면 PRG로 목록에 돌아간다.</li>
 * </ul>
 */
@Controller
class HomeController {

	private final HomeService home;

	HomeController(HomeService home) {
		this.home = home;
	}

	@GetMapping("/")
	String index(@ModelAttribute("filter") NoteFilter filter, Model model) {
		model.addAttribute("form", new NoteForm());
		addLists(filter, model);
		return CozyViews.pageOrFragment("home/index", "result");
	}

	@PostMapping("/notes")
	String add(@Valid @ModelAttribute("form") NoteForm form, BindingResult errors, Model model,
			RedirectAttributes flash) {
		if (errors.hasErrors()) {
			addLists(new NoteFilter(), model);
			model.addAttribute("filter", new NoteFilter());
			return CozyViews.pageOrFragment("home/index", "noteForm");
		}
		this.home.add(form.getTitle());
		flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("메모를 추가했습니다"));
		return CozyViews.redirect("/");
	}

	private void addLists(NoteFilter filter, Model model) {
		model.addAttribute("result", new HomeViews.ListResult(
				this.home.search(filter.getQ()).stream().map(HomeViews.Row::of).toList()));
		model.addAttribute("activity",
				new HomeViews.Activity(this.home.recentActivity().stream().map(HomeViews.ActivityRow::of).toList()));
	}
}
