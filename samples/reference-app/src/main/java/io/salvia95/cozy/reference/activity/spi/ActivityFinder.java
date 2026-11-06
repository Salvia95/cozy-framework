package io.salvia95.cozy.reference.activity.spi;

import java.util.List;

import io.salvia95.cozy.reference.activity.spi.dto.ActivityItem;

/** 최근 활동 읽기. */
public interface ActivityFinder {

	/** 최근 것부터 {@code limit}개 */
	List<ActivityItem> recent(int limit);
}
