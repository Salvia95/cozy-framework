/**
 * 활동 기록: 다른 모듈의 이벤트를 받아 최근 활동을 남긴다. 리스너는 internal.app.command, 다른 모듈이 쓰는
 * 조회는 spi.ActivityFinder, 공개 fragment는 templates/activity/spi/. 이벤트는 이벤트 계약 모듈(events)에서 받으므로 memo에는
 * 의존하지 않는다.
 */
@ApplicationModule(allowedDependencies = "events :: spi")
package io.salvia95.cozy.reference.activity;

import org.springframework.modulith.ApplicationModule;
