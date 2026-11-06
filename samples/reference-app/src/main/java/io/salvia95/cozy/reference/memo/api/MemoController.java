package io.salvia95.cozy.reference.memo;

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

import io.salvia95.cozy.reference.memo.MemoService.MemoSearch;
import io.salvia95.cozy.reference.memo.view.MemoFilter;
import io.salvia95.cozy.reference.memo.view.MemoForm;
import io.salvia95.cozy.reference.memo.view.MemoViews;
import io.salvia95.cozy.ui.CozyFlash;
import io.salvia95.cozy.ui.CozyPage;
import io.salvia95.cozy.ui.CozyViews;

/**
 * 메모 화면 (PRD §6.7). 뷰 이름은 모듈 이름(memo/)으로 시작한다.
 */
@Controller
@RequestMapping("/memo")
class MemoController {

	static final int PAGE_SIZE = 10;

	private final MemoService memos;

	MemoController(MemoService memos) {
		this.memos = memos;
	}

	/** 전체 페이지, 또는 htmx 필터·페이지 이동이면 결과 fragment만 */
	@GetMapping
	String list(@ModelAttribute("filter") MemoFilter filter, Model model) {
		int number = (filter.getPage() == null || filter.getPage() < 1) ? 1 : filter.getPage();
		MemoSearch found = this.memos.search(filter.getQ(), filter.isPinned(), (long) (number - 1) * PAGE_SIZE, PAGE_SIZE);
		CozyPage page = CozyPage.of(number, PAGE_SIZE, found.total());
		model.addAttribute("result",
				new MemoViews.ListResult(found.items().stream().map(MemoViews.Row::of).toList(), page));
		return CozyViews.pageOrFragment("memo/list", "result");
	}

	@GetMapping("/new")
	String newForm(Model model) {
		model.addAttribute("form", new MemoForm());
		return "memo/form";
	}

	@PostMapping
	String create(@Valid @ModelAttribute("form") MemoForm form, BindingResult errors, RedirectAttributes flash) {
		if (errors.hasErrors()) {
			return CozyViews.pageOrFragment("memo/form", "form");
		}
		long id = this.memos.create(form.getTitle(), form.getBody(), form.isPinned());
		flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("메모를 저장했습니다"));
		return CozyViews.redirect("/memo/" + id);
	}

	@GetMapping("/{id}")
	String detail(@PathVariable long id, Model model) {
		Memo memo = find(id);
		model.addAttribute("memo", MemoViews.Detail.of(memo));
		model.addAttribute("form", MemoViews.formOf(memo));
		return "memo/detail";
	}

	@PostMapping("/{id}")
	String update(@PathVariable long id, @Valid @ModelAttribute("form") MemoForm form, BindingResult errors,
			Model model, RedirectAttributes flash) {
		Memo memo = find(id);
		if (errors.hasErrors()) {
			model.addAttribute("memo", MemoViews.Detail.of(memo));
			return CozyViews.pageOrFragment("memo/detail", "form");
		}
		this.memos.update(id, form.getTitle(), form.getBody(), form.isPinned());
		flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("메모를 고쳤습니다"));
		return CozyViews.redirect("/memo/" + id);
	}

	@PostMapping("/{id}/delete")
	String delete(@PathVariable long id, RedirectAttributes flash) {
		if (!this.memos.delete(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.info("메모를 지웠습니다"));
		return CozyViews.redirect("/memo");
	}

	private Memo find(long id) {
		return this.memos.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}
}
