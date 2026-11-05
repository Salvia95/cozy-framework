package io.salvia95.cozy.runtime;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code cozy.*} 런타임 설정 (PRD §9.1, §9.2). 문서: docs/conventions/runtime.md
 *
 * @param app 앱 식별
 * @param dataDir 데이터 루트 재정의. 비우면 OS 기본 위치를 쓴다.
 * @param runtime 실행 동작
 */
@ConfigurationProperties("cozy")
public record CozyRuntimeProperties(App app, @Nullable Path dataDir, @DefaultValue Runtime runtime) {

	/**
	 * @param id 필수. 영문 소문자·숫자·하이픈. 데이터 폴더 이름이 된다.
	 * @param name 화면 표시명. 비우면 id를 쓴다.
	 */
	public record App(String id, @Nullable String name) {

		public String displayName() {
			return (this.name == null || this.name.isBlank()) ? this.id : this.name;
		}
	}

	/**
	 * @param openBrowser 기동이 끝나면 브라우저를 연다. 테스트에서는 false.
	 */
	public record Runtime(@DefaultValue("true") boolean openBrowser) {
	}
}
