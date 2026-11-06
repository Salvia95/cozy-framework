package ${package}.home.spi;

import java.util.List;

import ${package}.home.spi.dto.NoteActivityItem;
import ${package}.home.spi.dto.NoteItem;

/** 홈 읽기. */
public interface HomeFinder {

	/** 제목에 q가 들어간 메모, 최근 것부터. q가 비면 전부. */
	List<NoteItem> search(String q);

	/** 최근 활동, 최근 것부터 */
	List<NoteActivityItem> recentActivity();
}
