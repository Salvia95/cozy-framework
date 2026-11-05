/**
 * 대시보드: memo::api, activity::api의 fragment를 조합하고 polling-panel로 최근 활동을 갱신한다 (PRD §6.7).
 */
@ApplicationModule(allowedDependencies = { "memo::api", "activity::api" })
package io.salvia95.cozy.reference.dashboard;

import org.springframework.modulith.ApplicationModule;
