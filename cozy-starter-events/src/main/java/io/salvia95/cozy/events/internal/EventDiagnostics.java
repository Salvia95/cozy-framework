package io.salvia95.cozy.events.internal;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;

import io.salvia95.cozy.runtime.CozyDiagnosticsContributor;

/**
 * 진단 파일의 {@code events.txt}: 미완료 이벤트 개수, 상태별 개수, 오래된 미완료 기록.
 *
 * <p>이벤트 내용({@code serialized_event})은 업무 데이터가 들어 있을 수 있어 넣지 않는다. 종류와 리스너만 적는다.
 */
public class EventDiagnostics implements CozyDiagnosticsContributor {

	static final int OLDEST_LIMIT = 20;

	private final JdbcClient jdbc;

	public EventDiagnostics(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String fileName() {
		return "events.txt";
	}

	@Override
	public String content() {
		String table = SqliteEventPublicationRepository.TABLE;
		long incomplete = this.jdbc.sql("SELECT count(*) FROM " + table + " WHERE completion_date IS NULL")
			.query(Long.class)
			.single();
		StringBuilder text = new StringBuilder();
		text.append("미완료 이벤트: ").append(incomplete).append("\n\n");
		text.append("== 상태별 ==\n");
		List<Map<String, Object>> byStatus = this.jdbc
			.sql("SELECT coalesce(status, '(없음)') AS status, count(*) AS n FROM " + table
					+ " GROUP BY status ORDER BY status")
			.query()
			.listOfRows();
		byStatus.forEach((row) -> text.append(row.get("status")).append(": ").append(row.get("n")).append('\n'));
		text.append("\n== 오래된 미완료 (최대 ").append(OLDEST_LIMIT).append("개) ==\n");
		text.append("발행 시각 | 상태 | 시도 | 이벤트 종류 | 리스너\n");
		this.jdbc
			.sql("SELECT publication_date, status, completion_attempts, event_type, listener_id FROM " + table
					+ " WHERE completion_date IS NULL ORDER BY publication_date LIMIT " + OLDEST_LIMIT)
			.query()
			.listOfRows()
			.forEach((row) -> text.append(row.get("publication_date")).append(" | ").append(row.get("status"))
				.append(" | ").append(row.get("completion_attempts")).append(" | ").append(row.get("event_type"))
				.append(" | ").append(row.get("listener_id")).append('\n'));
		return text.toString();
	}
}
