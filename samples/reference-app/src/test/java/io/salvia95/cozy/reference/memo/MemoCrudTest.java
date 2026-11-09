package io.salvia95.cozy.reference.memo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import io.salvia95.cozy.reference.memo.spi.MemoFacade;
import io.salvia95.cozy.reference.memo.spi.MemoFinder;
import io.salvia95.cozy.reference.memo.spi.dto.MemoItem;

/**
 * reference-app memo CRUD가 SQLite + MyBatis로 동작한다.
 */
@SpringBootTest(properties = "cozy.data-dir=target/test-data/memo-crud-${random.uuid}")
class MemoCrudTest {

	@Autowired
	MemoFacade memos;

	@Autowired
	MemoFinder finder;

	@Test
	void createReadUpdateDelete() {
		Instant before = Instant.now();
		long first = this.memos.create("첫 메모", "본문", false);
		long second = this.memos.create("둘째 메모", null, false);

		MemoItem created = this.finder.find(first).orElseThrow();
		assertThat(created.title()).isEqualTo("첫 메모");
		assertThat(created.body()).isEqualTo("본문");
		assertThat(created.pinned()).isFalse();
		assertThat(created.createdAt()).isAfterOrEqualTo(before).isEqualTo(created.updatedAt());
		assertThat(this.finder.find(second).orElseThrow().body()).isEmpty();

		assertThat(this.memos.update(first, "첫 메모(고정)", "바뀐 본문", true)).isTrue();
		MemoItem updated = this.finder.find(first).orElseThrow();
		assertThat(updated.pinned()).isTrue();
		assertThat(updated.body()).isEqualTo("바뀐 본문");
		assertThat(updated.updatedAt()).isAfterOrEqualTo(updated.createdAt());

		assertThat(this.finder.list()).extracting(MemoItem::id).containsSubsequence(first, second);

		assertThat(this.memos.delete(second)).isTrue();
		assertThat(this.finder.find(second)).isEmpty();
		assertThat(this.memos.delete(second)).isFalse();
		assertThat(this.memos.update(second, "없음", "", false)).isFalse();
	}

	@Test
	void emptyTitleIsRejectedByTheDatabase() {
		assertThatThrownBy(() -> this.memos.create("", "본문", false)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> this.memos.create(null, "본문", false)).isInstanceOf(DataIntegrityViolationException.class);
	}
}
