package io.salvia95.cozy.runtime;

import java.util.Optional;

/**
 * 비밀 값은 이 인터페이스로만 읽는다 (PRD §9.1).
 *
 * <p>기본 구현은 {@code {dataDir}/config/secrets.properties}를 읽는다. OS 암호화 저장소 등으로 바꿀 때는
 * 이 타입의 빈을 등록하면 기본 구현이 물러난다(PRD §13 미결). 값은 로그와 진단 파일에 절대 쓰지 않는다.
 */
public interface CozySecretStore {

	/** 키에 해당하는 값. 없으면 빈 값. */
	Optional<String> find(String key);

	/** 키에 해당하는 값. 없으면 키 이름만 담은 메시지로 실패한다. */
	default String require(String key) {
		return find(key).orElseThrow(() -> new IllegalStateException(
				"[cozy] 비밀 값 '" + key + "'가 없습니다. {dataDir}/config/secrets.properties에 추가하세요."));
	}
}
