package ${package}.home.api;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ${package}.home.api.dto.HomeViews;
import ${package}.home.api.dto.NoteFilter;
import ${package}.home.api.dto.NoteForm;
import ${package}.home.spi.HomeFacade;
import ${package}.home.spi.HomeFinder;
import io.salvia95.cozy.ui.CozyFlash;
import io.salvia95.cozy.ui.CozyViews;

/**
 * 홈 화면. 쓰기는 HomeFacade, 읽기는 HomeFinder(spi)로 하고 spi DTO를 화면 DTO(api.dto)로 바꾼다. 뷰는
 * templates/home/api/의 화면이다.
 *
 * <ul>
 * <li>목록: 전체 페이지와 htmx 필터 갱신을 같은 엔드포인트에서 pageOrFragment로 나눈다.</li>
 * <li>등록: 폼 클래스 + @Valid + BindingResult. 오류면 폼 fragment만 다시 그리고, 성공하면 PRG로 목록에 돌아간다.</li>
 * </ul>
 */
@Controller
class HomeController {

	private final HomeFacade home;

	private final HomeFinder finder;

	HomeController(HomeFacade home, HomeFinder finder) {
		this.home = home;
		this.finder = finder;
	}

	@GetMapping("/")
	String index(@ModelAttribute("filter") NoteFilter filter, Model model) {
		model.addAttribute("form", new NoteForm());
		addLists(filter, model);
		return CozyViews.pageOrFragment("home/api/index", "result");
	}

	@PostMapping("/notes")
	String add(@Valid @ModelAttribute("form") NoteForm form, BindingResult errors, Model model,
			RedirectAttributes flash) {
		if (errors.hasErrors()) {
			addLists(new NoteFilter(), model);
			model.addAttribute("filter", new NoteFilter());
			return CozyViews.pageOrFragment("home/api/index", "noteForm");
		}
		this.home.add(form.getTitle());
		flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("메모를 추가했습니다"));
		return CozyViews.redirect("/");
	}

	private void addLists(NoteFilter filter, Model model) {
		model.addAttribute("result", new HomeViews.ListResult(
				this.finder.search(filter.getQ()).stream().map(HomeViews.Row::of).toList()));
		model.addAttribute("activity",
				new HomeViews.Activity(this.finder.recentActivity().stream().map(HomeViews.ActivityRow::of).toList()));
	}
}
