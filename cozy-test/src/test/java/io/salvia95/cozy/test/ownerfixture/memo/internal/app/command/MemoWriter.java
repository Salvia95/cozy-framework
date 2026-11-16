package io.salvia95.cozy.test.ownerfixture.memo.internal.app.command;

import io.salvia95.cozy.test.ownerfixture.events.spi.memo.MemoCreated;
import io.salvia95.cozy.test.ownerfixture.wiki.spi.event.WikiEdited;

/** 주인 모듈의 app.command: 자기 이벤트는 만들 수 있고, wiki의 이벤트는 만들 수 없다 */
public class MemoWriter {

	public Object create() {
		return MemoCreated.of(1, "제목");
	}

	public Object editWiki() {
		return WikiEdited.of(2);
	}
}
