package io.salvia95.cozy.reference.dashboard.api.dto;

import java.util.List;

import io.salvia95.cozy.reference.activity.spi.dto.ActivityItem;
import io.salvia95.cozy.reference.memo.spi.dto.MemoSummary;

/**
 * 대시보드 화면 DTO.
 *
 * @param memo 메모 요약
 * @param activities 최근 활동
 */
public record DashboardView(MemoSummary memo, List<ActivityItem> activities) {
}
