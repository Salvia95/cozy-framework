package {{apiPkg}};

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import {{dtoPkg}}.{{E}}Filter;
import {{dtoPkg}}.{{E}}Form;
import {{dtoPkg}}.{{E}}Views;
import {{spiPkg}}.{{E}}Facade;
import {{spiPkg}}.{{E}}Finder;
import {{spiDtoPkg}}.{{E}}Item;
import {{spiDtoPkg}}.{{E}}Page;
import io.salvia95.cozy.ui.CozyFlash;
import io.salvia95.cozy.ui.CozyPage;
import io.salvia95.cozy.ui.CozyViews;

/**
 * {{L}} 화면 (cozy add crud). 쓰기는 {{E}}Facade, 읽기는 {{E}}Finder(spi)로 하고 spi DTO를 화면 DTO(api.dto)로 바꾼다. 흐름: 목록 →
 * 만들기 → 상세(읽기 전용) → 수정 → 삭제(확인 창) (docs/conventions/design.md §3).
 */
@Controller
@RequestMapping("/{{module}}")
class {{E}}Controller {

	static final int PAGE_SIZE = 10;

	private final {{E}}Facade facade;

	private final {{E}}Finder finder;

	{{E}}Controller({{E}}Facade facade, {{E}}Finder finder) {
		this.facade = facade;
		this.finder = finder;
	}

	/** 전체 페이지, 또는 htmx 검색·페이지 이동이면 결과 fragment만 */
	@GetMapping
	String list(@ModelAttribute("filter") {{E}}Filter filter, Model model) {
		int number = (filter.getPage() == null || filter.getPage() < 1) ? 1 : filter.getPage();
		{{E}}Page found = this.finder.search(filter.getQ(), (long) (number - 1) * PAGE_SIZE, PAGE_SIZE);
		CozyPage page = CozyPage.of(number, PAGE_SIZE, found.total());
		model.addAttribute("result", new {{E}}Views.ListResult(found.items().stream().map({{E}}Views.Row::of).toList(), page));
		return CozyViews.pageOrFragment("{{module}}/api/list", "result");
	}

	@GetMapping("/new")
	String newForm(Model model) {
		model.addAttribute("form", new {{E}}Form());
		return "{{module}}/api/form";
	}

	@PostMapping
	String create(@Valid @ModelAttribute("form") {{E}}Form form, BindingResult errors, RedirectAttributes flash) {
		if (errors.hasErrors()) {
			return CozyViews.pageOrFragment("{{module}}/api/form", "{{e}}Form");
		}
		long id = this.facade.create({{formArgs}});
		flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("{{Lo}} 만들었습니다"));
		return CozyViews.redirect("/{{module}}/" + id);
	}

	/** 상세(읽기 전용). 수정은 /edit 화면, 삭제는 확인 창을 거친다 */
	@GetMapping("/{id}")
	String detail(@PathVariable long id, Model model) {
		model.addAttribute("item", {{E}}Views.Detail.of(find(id)));
		return "{{module}}/api/detail";
	}

	@GetMapping("/{id}/edit")
	String edit(@PathVariable long id, Model model) {
		{{E}}Item item = find(id);
		model.addAttribute("item", {{E}}Views.Detail.of(item));
		model.addAttribute("form", {{E}}Views.formOf(item));
		return "{{module}}/api/edit";
	}

	@PostMapping("/{id}")
	String update(@PathVariable long id, @Valid @ModelAttribute("form") {{E}}Form form, BindingResult errors, Model model,
			RedirectAttributes flash) {
		{{E}}Item item = find(id);
		if (errors.hasErrors()) {
			model.addAttribute("item", {{E}}Views.Detail.of(item));
			return CozyViews.pageOrFragment("{{module}}/api/edit", "{{e}}Form");
		}
		if ({{E}}Views.unchanged(item, form)) {
			flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.info("바뀐 내용이 없습니다"));
			return CozyViews.redirect("/{{module}}/" + id);
		}
		this.facade.update(id, {{formArgs}});
		flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("{{Lo}} 수정했습니다"));
		return CozyViews.redirect("/{{module}}/" + id);
	}

	@PostMapping("/{id}/delete")
	String delete(@PathVariable long id, RedirectAttributes flash) {
		if (!this.facade.delete(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("{{Lo}} 삭제했습니다"));
		return CozyViews.redirect("/{{module}}");
	}

	private {{E}}Item find(long id) {
		return this.finder.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}
}
