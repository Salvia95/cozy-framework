package io.salvia95.cozy.runtime.update;

import java.util.Optional;

/**
 * 자체 업데이트 상태 (PRD §9.4). layout 배너가 이 값을 그린다. 문서: docs/conventions/distribution.md
 */
public interface CozyUpdates {

	/** 설치할 수 있는 새 버전. 업데이트가 꺼져 있거나 최신이면 빈 값. */
	Optional<Offer> offer();

	/** 직전 업데이트의 결과. 업데이트 뒤 처음 기동한 실행에서만 값이 있다. */
	Optional<Outcome> lastOutcome();

	/**
	 * @param version 새 버전
	 * @param required 현재 버전이 매니페스트의 minimumVersion보다 낮다
	 * @param notes 매니페스트의 notes
	 * @param installable 이 실행에서 바로 설치할 수 있다. 설치본(app-image)이 아니거나 앱 폴더에 쓸 수 없으면 false
	 * @param reason installable이 false인 이유
	 */
	record Offer(String version, boolean required, String notes, boolean installable, String reason) {
	}

	/**
	 * @param success 새 버전으로 바뀌었다. false면 이전 버전으로 되돌렸다.
	 * @param message 화면에 보일 문장
	 */
	record Outcome(boolean success, String message) {
	}
}
