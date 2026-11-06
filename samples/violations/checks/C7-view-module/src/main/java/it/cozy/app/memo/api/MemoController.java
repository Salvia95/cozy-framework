package it.cozy.app.memo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class MemoController {

	@GetMapping("/memo")
	String list() {
		return "wiki/list";
	}
}
