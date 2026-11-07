package it.cozy.app.activity.spi;

import java.util.List;

import it.cozy.app.activity.spi.dto.ActivityItem;

/** 최근 활동 읽기. */
public interface ActivityFinder {

	/** 최근 것부터 {@code limit}개 */
	List<ActivityItem> recent(int limit);
}
