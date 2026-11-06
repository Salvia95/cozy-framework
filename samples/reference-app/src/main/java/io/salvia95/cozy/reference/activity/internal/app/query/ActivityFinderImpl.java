package io.salvia95.cozy.reference.activity.internal.app.query;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.salvia95.cozy.reference.activity.internal.infra.ActivityMapper;
import io.salvia95.cozy.reference.activity.spi.ActivityFinder;
import io.salvia95.cozy.reference.activity.spi.dto.ActivityItem;

/** 최근 활동 읽기. */
@Service
@Transactional(readOnly = true)
class ActivityFinderImpl implements ActivityFinder {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.KOREA)
		.withZone(ZoneId.systemDefault());

	private final ActivityMapper mapper;

	ActivityFinderImpl(ActivityMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public List<ActivityItem> recent(int limit) {
		return this.mapper.findRecent(limit)
			.stream()
			.map((row) -> new ActivityItem(row.message(), TIME.format(row.createdAt())))
			.toList();
	}
}
