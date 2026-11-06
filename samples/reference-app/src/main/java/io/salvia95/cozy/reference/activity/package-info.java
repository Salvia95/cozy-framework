/**
 * 활동 기록: 다른 모듈의 이벤트를 받아 최근 활동을 남긴다 (PRD §6.7). 리스너는 internal.app.command, 다른 모듈이 쓰는
 * 조회는 spi.ActivityFinder, 공개 fragment는 templates/activity/spi/.
 */
@ApplicationModule(allowedDependencies = "memo :: spi")
package io.salvia95.cozy.reference.activity;

import org.springframework.modulith.ApplicationModule;
