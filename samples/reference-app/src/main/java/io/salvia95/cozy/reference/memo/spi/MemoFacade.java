package io.salvia95.cozy.reference.memo.spi;

/** 메모 쓰기. 메모를 만들면 {@link io.salvia95.cozy.reference.memo.spi.event.MemoCreated}를 발행한다. */
public interface MemoFacade {

	/** @return 새 메모의 id */
	long create(String title, String body, boolean pinned);

	/** @return 고쳤으면 true, 없는 메모면 false */
	boolean update(long id, String title, String body, boolean pinned);

	/** @return 지웠으면 true, 없는 메모면 false */
	boolean delete(long id);
}
