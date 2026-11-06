package io.salvia95.cozy.events.autoconfigure;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.config.EventPublicationAutoConfiguration;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import io.salvia95.cozy.events.CozyEventsProperties;
import io.salvia95.cozy.events.internal.CozyEventSerializer;
import io.salvia95.cozy.events.internal.EventDiagnostics;
import io.salvia95.cozy.events.internal.EventRetentionCleaner;
import io.salvia95.cozy.events.internal.InboxAdvisor;
import io.salvia95.cozy.events.internal.SqliteEventPublicationRepository;
import io.salvia95.cozy.sqlite.CozyMigrationLocation;
import io.salvia95.cozy.sqlite.autoconfigure.CozySqliteAutoConfiguration;

/**
 * 이벤트 발행 기록과 인박스 (PRD §8): SQLite 저장소, 전용 직렬화, 인박스, 보관 정리, 마이그레이션 위치.
 *
 * <p>트랜잭션 어드바이저를 {@link #TRANSACTION_ADVISOR_ORDER}로 두어 인박스 어드바이저가 리스너 트랜잭션 안쪽에서 돌게 한다
 * (ADR-0023). Boot의 기본 트랜잭션 설정은 이것이 있으면 물러난다.
 */
@AutoConfiguration(after = CozySqliteAutoConfiguration.class,
		before = { EventPublicationAutoConfiguration.class, TransactionAutoConfiguration.class })
@EnableConfigurationProperties(CozyEventsProperties.class)
@EnableTransactionManagement(order = CozyEventsAutoConfiguration.TRANSACTION_ADVISOR_ORDER)
public class CozyEventsAutoConfiguration {

	/** 트랜잭션 어드바이저 순서. 인박스 어드바이저(LOWEST_PRECEDENCE)보다 바깥이다 */
	public static final int TRANSACTION_ADVISOR_ORDER = Ordered.LOWEST_PRECEDENCE - 1000;

	@Bean
	EventSerializer cozyEventSerializer() {
		return new CozyEventSerializer();
	}

	@Bean
	SqliteEventPublicationRepository cozyEventPublicationRepository(JdbcTemplate jdbcTemplate,
			EventSerializer serializer, Environment environment) {
		String mode = environment.getProperty("spring.modulith.events.completion-mode", "update");
		if (!mode.equalsIgnoreCase("update")) {
			throw new IllegalStateException("[cozy] spring.modulith.events.completion-mode는 update만 지원합니다 (지금: " + mode
					+ "). 완료 기록은 cozy.events.retention이 지나면 정리 작업이 지웁니다.");
		}
		return new SqliteEventPublicationRepository(JdbcClient.create(jdbcTemplate), serializer,
				CozyEventsAutoConfiguration.class.getClassLoader());
	}

	@Bean
	CozyMigrationLocation cozyEventsMigrations() {
		return new CozyMigrationLocation("classpath:db/cozy/events");
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	static InboxAdvisor cozyInboxAdvisor(ObjectProvider<JdbcTemplate> jdbcTemplate, ObjectProvider<Clock> clock) {
		return new InboxAdvisor(() -> JdbcClient.create(jdbcTemplate.getObject()), clock.getIfAvailable(Clock::systemUTC));
	}

	/** 진단 파일의 events.txt: 상태별 발행 기록 수, 미완료 기록 (PRD §9.5) */
	@Bean
	EventDiagnostics cozyEventDiagnostics(JdbcTemplate jdbcTemplate) {
		return new EventDiagnostics(JdbcClient.create(jdbcTemplate));
	}

	@Bean
	EventRetentionCleaner cozyEventRetentionCleaner(EventPublicationRepository repository, JdbcTemplate jdbcTemplate,
			PlatformTransactionManager transactionManager, CozyEventsProperties properties,
			ObjectProvider<Clock> clock) {
		return new EventRetentionCleaner(repository, JdbcClient.create(jdbcTemplate),
				new TransactionTemplate(transactionManager), properties, clock.getIfAvailable(Clock::systemUTC));
	}
}
