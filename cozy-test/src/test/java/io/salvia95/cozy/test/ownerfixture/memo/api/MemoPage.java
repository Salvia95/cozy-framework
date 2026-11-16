package io.salvia95.cozy.test.ownerfixture.memo.api;

import java.util.UUID;

import io.salvia95.cozy.test.ownerfixture.events.spi.memo.MemoCreated;

/** 주인 모듈이라도 app.command 밖에서는 이벤트를 만들지 않는다 */
public class MemoPage {

	public Object publish() {
		return new MemoCreated(UUID.randomUUID(), 1, 1, "제목");
	}
}
