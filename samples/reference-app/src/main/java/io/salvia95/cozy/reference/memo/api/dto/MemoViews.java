package io.salvia95.cozy.reference.memo.api.dto;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import io.salvia95.cozy.reference.memo.spi.dto.MemoItem;
import io.salvia95.cozy.ui.CozyPage;

/** 메모 화면 DTO. 템플릿에는 spi DTO를 화면에 맞게 바꾼 이 record들을 넘긴다 (PRD §10). */
public final class MemoViews {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.KOREA)
		.withZone(ZoneId.systemDefault());

	private MemoViews() {
	}

	/** 목록 한 행 */
	public record Row(long id, String title, boolean pinned, String updatedAt) {

		public static Row of(MemoItem memo) {
			return new Row(memo.id(), memo.title(), memo.pinned(), format(memo.updatedAt()));
		}
	}

	/** 목록 결과 (fragment "result") */
	public record ListResult(List<Row> rows, CozyPage page) {
	}

	/** 상세 화면 */
	public record Detail(long id, String title, List<String> bodyLines, boolean pinned, String createdAt,
			String updatedAt) {

		public static Detail of(MemoItem memo) {
			return new Detail(memo.id(), memo.title(), memo.body().lines().toList(), memo.pinned(),
					format(memo.createdAt()), format(memo.updatedAt()));
		}
	}

	public static MemoForm formOf(MemoItem memo) {
		MemoForm form = new MemoForm();
		form.setTitle(memo.title());
		form.setBody(memo.body());
		form.setPinned(memo.pinned());
		return form;
	}

	static String format(Instant instant) {
		return TIME.format(instant);
	}
}
