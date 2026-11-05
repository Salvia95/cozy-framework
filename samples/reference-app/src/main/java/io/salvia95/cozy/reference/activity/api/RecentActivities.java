package io.salvia95.cozy.reference.activity.api;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.salvia95.cozy.reference.activity.internal.ActivityMapper;

/** 다른 모듈이 쓰는 최근 활동 조회. */
@Service
public class RecentActivities {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.KOREA)
		.withZone(ZoneId.systemDefault());

	private final ActivityMapper mapper;

	RecentActivities(ActivityMapper mapper) {
		this.mapper = mapper;
	}

	@Transactional(readOnly = true)
	public List<ActivityItem> recent(int limit) {
		return this.mapper.findRecent(limit)
			.stream()
			.map((row) -> new ActivityItem(row.message(), TIME.format(row.createdAt())))
			.toList();
	}
}
