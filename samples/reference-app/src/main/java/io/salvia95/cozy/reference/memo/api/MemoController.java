package io.salvia95.cozy.reference.memo.api;

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

import io.salvia95.cozy.reference.memo.api.dto.MemoFilter;
import io.salvia95.cozy.reference.memo.api.dto.MemoForm;
import io.salvia95.cozy.reference.memo.api.dto.MemoViews;
import io.salvia95.cozy.reference.memo.spi.MemoFacade;
import io.salvia95.cozy.reference.memo.spi.MemoFinder;
import io.salvia95.cozy.reference.memo.spi.dto.MemoItem;
import io.salvia95.cozy.reference.memo.spi.dto.MemoPage;
import io.salvia95.cozy.ui.CozyFlash;
import io.salvia95.cozy.ui.CozyPage;
import io.salvia95.cozy.ui.CozyViews;

/**
 * 메모 화면. 쓰기는 MemoFacade, 읽기는 MemoFinder(spi)로 하고 spi DTO를 화면 DTO(api.dto)로 바꾼다.
 * 뷰는 templates/memo/api/의 화면이다.
 */
@Controller
@RequestMapping("/memo")
class MemoController {

	static final int PAGE_SIZE = 10;

	private final MemoFacade memos;

	private final MemoFinder finder;

	MemoController(MemoFacade memos, MemoFinder finder) {
		this.memos = memos;
		this.finder = finder;
	}

	/** 전체 페이지, 또는 htmx 필터·페이지 이동이면 결과 fragment만 */
	@GetMapping
	String list(@ModelAttribute("filter") MemoFilter filter, Model model) {
		int number = (filter.getPage() == null || filter.getPage() < 1) ? 1 : filter.getPage();
		MemoPage found = this.finder.search(filter.getQ(), filter.isPinned(), (long) (number - 1) * PAGE_SIZE, PAGE_SIZE);
		CozyPage page = CozyPage.of(number, PAGE_SIZE, found.total());
		model.addAttribute("result",
				new MemoViews.ListResult(found.items().stream().map(MemoViews.Row::of).toList(), page));
		return CozyViews.pageOrFragment("memo/api/list", "result");
	}

	@GetMapping("/new")
	String newForm(Model model) {
		model.addAttribute("form", new MemoForm());
		return "memo/api/form";
	}

	@PostMapping
	String create(@Valid @ModelAttribute("form") MemoForm form, BindingResult errors, RedirectAttributes flash) {
		if (errors.hasErrors()) {
			return CozyViews.pageOrFragment("memo/api/form", "memoForm");
		}
		long id = this.memos.create(form.getTitle(), form.getBody(), form.isPinned());
		flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("메모를 만들었습니다"));
		return CozyViews.redirect("/memo/" + id);
	}

	/** 상세(읽기 전용). 수정은 /edit 화면, 삭제는 확인 창을 거친다 */
	@GetMapping("/{id}")
	String detail(@PathVariable long id, Model model) {
		model.addAttribute("memo", MemoViews.Detail.of(find(id)));
		return "memo/api/detail";
	}

	@GetMapping("/{id}/edit")
	String edit(@PathVariable long id, Model model) {
		MemoItem memo = find(id);
		model.addAttribute("memo", MemoViews.Detail.of(memo));
		model.addAttribute("form", MemoViews.formOf(memo));
		return "memo/api/edit";
	}

	@PostMapping("/{id}")
	String update(@PathVariable long id, @Valid @ModelAttribute("form") MemoForm form, BindingResult errors,
			Model model, RedirectAttributes flash) {
		MemoItem memo = find(id);
		if (errors.hasErrors()) {
			model.addAttribute("memo", MemoViews.Detail.of(memo));
			return CozyViews.pageOrFragment("memo/api/edit", "memoForm");
		}
		if (MemoViews.unchanged(memo, form)) {
			flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.info("바뀐 내용이 없습니다"));
			return CozyViews.redirect("/memo/" + id);
		}
		this.memos.update(id, form.getTitle(), form.getBody(), form.isPinned());
		flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("메모를 수정했습니다"));
		return CozyViews.redirect("/memo/" + id);
	}

	@PostMapping("/{id}/delete")
	String delete(@PathVariable long id, RedirectAttributes flash) {
		if (!this.memos.delete(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		flash.addFlashAttribute(CozyFlash.ATTRIBUTE, CozyFlash.success("메모를 삭제했습니다"));
		return CozyViews.redirect("/memo");
	}

	private MemoItem find(long id) {
		return this.finder.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}
}
