package it.cozy.app.memo.internal.app.command;

import org.springframework.stereotype.Service;

import it.cozy.app.memo.spi.MemoFacade;

@Service
class MemoFacadeImpl implements MemoFacade {

	@Override
	public long create(String title) {
		// cozy:slot memo-create — 메모를 저장하고 id를 돌려주세요
		throw new UnsupportedOperationException("cozy:slot memo-create");
	}
}
