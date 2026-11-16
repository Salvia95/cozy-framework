package io.salvia95.cozy.test.ownerfixture.wiki.internal.app.command;

import io.salvia95.cozy.test.ownerfixture.events.spi.memo.MemoCreated;

/** 받는 쪽은 이벤트를 읽기만 한다 */
public class MemoCreatedListener {

	public String on(MemoCreated event) {
		return event.title();
	}
}
