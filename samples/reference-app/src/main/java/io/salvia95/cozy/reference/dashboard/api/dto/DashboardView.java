package io.salvia95.cozy.reference.dashboard.view;

import java.util.List;

import io.salvia95.cozy.reference.activity.api.ActivityItem;
import io.salvia95.cozy.reference.memo.api.MemoSummary;

/** 대시보드 화면 DTO. */
public record DashboardView(MemoSummary memo, List<ActivityItem> activities) {
}
