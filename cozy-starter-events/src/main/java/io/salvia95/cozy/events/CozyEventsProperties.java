package io.salvia95.cozy.events;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code cozy.events.*} (PRD §8.1, §8.3).
 *
 * @param retention 완료된 발행 기록과 인박스 행을 보관하는 기간
 * @param cleanupInterval 보관 기간이 지난 행을 지우는 주기 (기동 1분 뒤 처음 실행)
 */
@ConfigurationProperties("cozy.events")
public record CozyEventsProperties(@DefaultValue("30d") Duration retention,
		@DefaultValue("6h") Duration cleanupInterval) {
}
