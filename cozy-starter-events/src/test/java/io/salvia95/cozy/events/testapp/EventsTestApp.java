package io.salvia95.cozy.events.testapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 이벤트 계약 테스트용 앱. 프로세스 강제 종료 테스트에서는 별도 JVM의 main으로 뜬다.
 *
 * <p>인자 {@code publish}: 기동 후 이벤트 하나를 발행한다. 시스템 속성 {@code cozy.test.slow=true}면 리스너가 오래 멈춘다.
 */
@SpringBootApplication
public class EventsTestApp {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(EventsTestApp.class);
		application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
		var context = application.run(args);
		if (args.length > 0 && args[0].equals("publish")) {
			context.getBean(io.salvia95.cozy.events.testapp.sample.SampleService.class).order("강제 종료", false);
			System.out.println("PUBLISHED");
		}
	}
}
