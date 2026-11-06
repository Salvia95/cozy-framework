package ${package}.home.view;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import ${package}.home.internal.Note;
import ${package}.home.internal.NoteActivity;

/** 홈 화면 DTO. 템플릿에는 도메인 객체나 Mapper 결과 대신 이 record들을 넘긴다. */
public final class HomeViews {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.KOREA)
		.withZone(ZoneId.systemDefault());

	private HomeViews() {
	}

	/** 목록 한 행 */
	public record Row(long id, String title, String createdAt) {

		public static Row of(Note note) {
			return new Row(note.id(), note.title(), format(note.createdAt()));
		}
	}

	/** 목록 결과 (fragment "result") */
	public record ListResult(List<Row> rows) {
	}

	/** 최근 활동 (화면 아래 카드) */
	public record Activity(List<ActivityRow> items) {
	}

	/** 최근 활동 한 줄 */
	public record ActivityRow(String message, String at) {

		public static ActivityRow of(NoteActivity activity) {
			return new ActivityRow(activity.message(), format(activity.at()));
		}
	}

	static String format(Instant instant) {
		return TIME.format(instant);
	}
}
