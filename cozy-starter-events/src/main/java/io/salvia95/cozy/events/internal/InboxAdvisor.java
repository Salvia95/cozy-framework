package io.salvia95.cozy.events.internal;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.salvia95.cozy.events.CozyEvent;

/**
 * 인박스. {@code CozyEvent}를 받는 모든 {@code @ApplicationModuleListener}에 적용한다.
 *
 * <p>리스너 트랜잭션 안에서 먼저 {@code cozy_inbox}에 (이벤트 id, 리스너 id)를 넣는다. 이미 있으면 본문을 건너뛰고 정상 완료로
 * 처리한다. 본문이 예외를 던지면 트랜잭션이 롤백되어 인박스 행도 사라지고, 발행 기록은 미완료로 남아 재시도된다.
 *
 * <p>트랜잭션 어드바이저보다 안쪽에서 돌아야 한다. cozy-starter-events는 트랜잭션 어드바이저 순서를
 * {@code LOWEST_PRECEDENCE - 1000}으로 두고 이 어드바이저는 {@code LOWEST_PRECEDENCE}로 둔다. 실행 중 트랜잭션이 없으면
 * 실패로 닫는다.
 */
public class InboxAdvisor extends AbstractPointcutAdvisor {

	private static final Log logger = LogFactory.getLog(InboxAdvisor.class);

	private final Supplier<JdbcClient> jdbc;

	private final Clock clock;

	public InboxAdvisor(Supplier<JdbcClient> jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
		setOrder(Ordered.LOWEST_PRECEDENCE);
	}

	/** 인박스를 적용할 리스너인가 */
	public static boolean appliesTo(Method method) {
		return AnnotatedElementUtils.hasAnnotation(method, ApplicationModuleListener.class)
				&& method.getParameterCount() == 1 && CozyEvent.class.isAssignableFrom(method.getParameterTypes()[0]);
	}

	@Override
	public Pointcut getPointcut() {
		return new StaticMethodMatcherPointcut() {
			@Override
			public boolean matches(Method method, Class<?> targetClass) {
				return appliesTo(method);
			}
		};
	}

	@Override
	public MethodInterceptor getAdvice() {
		return this::invoke;
	}

	private Object invoke(MethodInvocation invocation) throws Throwable {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("[cozy] 인박스는 리스너 트랜잭션 안에서 실행되어야 합니다 ("
					+ invocation.getMethod() + "). 앱에서 @EnableTransactionManagement를 직접 선언했다면 지우세요.");
		}
		CozyEvent event = (CozyEvent) invocation.getArguments()[0];
		String listenerId = ListenerIds.of(invocation.getMethod());
		int inserted = this.jdbc.get()
			.sql("INSERT INTO cozy_inbox (event_id, listener_id, processed_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING")
			.params(event.eventId().toString(), listenerId, EventTimes.format(Instant.now(this.clock)))
			.update();
		if (inserted == 0) {
			logger.info("[cozy] 이미 처리한 이벤트라 건너뜁니다: " + event.getClass().getSimpleName() + " " + event.eventId()
					+ " → " + listenerId);
			return null;
		}
		return invocation.proceed();
	}
}
