package it.cozy.app.memo.spi;

import it.cozy.app.memo.internal.domain.Memo;
import it.cozy.app.memo.spi.dto.MemoItem;

public interface MemoFinder {

	Memo find(long id);

	MemoItem item(long id);
}
