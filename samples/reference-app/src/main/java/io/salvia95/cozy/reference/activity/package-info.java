/**
 * 활동 기록: 다른 모듈의 이벤트를 받아 최근 활동을 남긴다 (PRD §6.7). 공개 영역은 activity.api와 templates/activity/api/.
 */
@ApplicationModule(allowedDependencies = "memo::api")
package io.salvia95.cozy.reference.activity;

import org.springframework.modulith.ApplicationModule;
