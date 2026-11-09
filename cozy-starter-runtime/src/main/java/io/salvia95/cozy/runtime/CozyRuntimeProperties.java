package io.salvia95.cozy.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code cozy.*} 런타임 설정. 문서: docs/conventions/runtime.md
 *
 * @param app 앱 식별
 * @param dataDir 데이터 루트 재정의. 비우면 OS 기본 위치를 쓴다.
 * @param runtime 실행 동작
 * @param update 자체 업데이트
 */
@ConfigurationProperties("cozy")
public record CozyRuntimeProperties(App app, @Nullable Path dataDir, @DefaultValue Runtime runtime,
		@DefaultValue Update update) {

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
	 * @param openBrowser 기동이 끝나면 브라우저를 열고, 진단 파일을 만들면 폴더를 연다. 테스트에서는 false.
	 * @param tray Windows 시스템 트레이 아이콘(열기, 종료). 트레이를 쓸 수 없는 환경에서는 저절로 꺼진다.
	 * @param training AOT 캐시 훈련 실행. 기동 뒤 {@code trainingPaths}를 한 번씩 요청하고 종료한다(-Pdist가 쓴다).
	 * @param trainingPaths 훈련 실행에서 요청할 경로
	 */
	public record Runtime(@DefaultValue("true") boolean openBrowser, @DefaultValue("true") boolean tray,
			@DefaultValue("false") boolean training,
			@DefaultValue({ "/", "/cozy/dev/catalog" }) List<String> trainingPaths) {
	}

	/**
	 * @param manifestUrl 업데이트 매니페스트 주소({@code file:} 또는 {@code http(s):}). 비우면 업데이트 기능이 꺼진다.
	 * @param checkInterval 매니페스트 확인 주기. 기동할 때 한 번 확인하고 이 간격으로 다시 확인한다.
	 * @param readyTimeout 새 버전이 이 시간 안에 준비 신호를 남기지 않으면 이전 버전으로 되돌린다.
	 */
	public record Update(@Nullable String manifestUrl, @DefaultValue("6h") Duration checkInterval,
			@DefaultValue("60s") Duration readyTimeout) {
	}
}
