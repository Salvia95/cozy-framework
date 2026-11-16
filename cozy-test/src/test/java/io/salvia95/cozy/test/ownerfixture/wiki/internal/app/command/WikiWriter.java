package io.salvia95.cozy.test.ownerfixture.wiki.internal.app.command;

import io.salvia95.cozy.test.ownerfixture.events.spi.memo.MemoCreated;

/** 다른 모듈(memo)의 이벤트를 만든다 */
public class WikiWriter {

	public Object create() {
		return MemoCreated.of(3, "남의 이벤트");
	}
}
