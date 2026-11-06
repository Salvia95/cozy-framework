package it.cozy.app.memo.internal.app.query;

import it.cozy.app.memo.internal.app.command.MemoWriter;
import it.cozy.app.memo.internal.domain.Memo;
import it.cozy.app.memo.spi.MemoFinder;
import it.cozy.app.memo.spi.dto.MemoItem;

class MemoFinderImpl implements MemoFinder {

	private final MemoWriter writer = new MemoWriter();

	@Override
	public Memo find(long id) {
		return new Memo(id, "x");
	}

	@Override
	public MemoItem item(long id) {
		return new MemoItem(id, "x");
	}
}
