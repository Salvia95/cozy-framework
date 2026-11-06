package it.cozy.app.memo.api;

import org.springframework.stereotype.Controller;

import it.cozy.app.memo.internal.infra.MemoStore;

@Controller
class MemoController {

	private final MemoStore store = new MemoStore();
}
