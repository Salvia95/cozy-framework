package io.salvia95.cozy.rules;

import java.util.List;
import java.util.Set;

import io.salvia95.cozy.rules.internal.LayerChecker;

/**
 * 검사 C8: 모듈 안의 배치와 이름, 레이어 방향 (ADR-0029). 자리는 {@link Layer}, 무엇을 어디에 어떤 이름으로 두는지는 {@link Role}에
 * 있다. 규칙과 메시지: docs/conventions/structure.md
 *
 * <p>입력은 어디서 읽었는지 모른다. cozy-test는 컴파일된 클래스(ArchUnit)에서, cozy-cli는 소스에서 {@link JavaType}을 만든다. 예외를
 * 던지지 않고 결과만 돌려준다.
 */
public final class LayerRules {

	private LayerRules() {
	}

	/**
	 * @param modules 앱 모듈. 어느 모듈에도 속하지 않는 타입(메인 클래스 등)은 보지 않는다
	 * @param types 검사할 타입. {@code package-info}와 이름에 {@code $$}가 있는 생성 타입은 건너뛴다
	 * @param namedInterfacePackages {@code package-info.java}에 {@code @NamedInterface}를 선언한 패키지
	 * @return 위반. 같은 메시지는 한 번만, 메시지 순서로
	 */
	public static List<Violation> check(List<ModulePackage> modules, List<JavaType> types,
			Set<String> namedInterfacePackages) {
		return LayerChecker.check(modules, types, namedInterfacePackages);
	}
}
