package it.cozy.app.dashboard.api.dto;

import java.util.List;

import it.cozy.app.activity.spi.dto.ActivityItem;

/** 대시보드 화면 DTO (cozy add screen). 컨트롤러가 spi DTO를 이 record로 바꿔 모델 view로 넘긴다. */
public record DashboardView(long memos, List<ActivityItem> activities) {
}
