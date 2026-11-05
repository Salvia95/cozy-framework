package io.salvia95.cozy.events.internal;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 이벤트 기본 설정 (PRD §8.1). 앱 설정이 있으면 그것이 먼저다.
 *
 * <ul>
 * <li>재시작하면 미완료 이벤트를 다시 발행한다.</li>
 * <li>비동기 리스너 실행기는 동시 2개. SQLite 쓰기 경합을 줄인다.</li>
 * </ul>
 */
public class CozyEventsEnvironmentPostProcessor implements EnvironmentPostProcessor {

	static final Map<String, Object> DEFAULTS = Map.of(
			"spring.modulith.events.republish-outstanding-events-on-restart", "true",
			"spring.task.execution.pool.core-size", "2", "spring.task.execution.pool.max-size", "2",
			"spring.task.execution.thread-name-prefix", "cozy-event-");

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		environment.getPropertySources().addLast(new MapPropertySource("cozyEventsDefaults", DEFAULTS));
	}
}
