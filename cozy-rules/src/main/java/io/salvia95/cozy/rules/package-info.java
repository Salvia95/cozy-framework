/**
 * Cozy 앱의 규칙 판단.
 *
 * <ul>
 * <li>템플릿 경계·fragment 규칙 R1~R16: {@link io.salvia95.cozy.rules.TemplateRules#check}. 입력은
 * {@link io.salvia95.cozy.rules.RulesInput}(템플릿 파일, 모듈 모델, fragment 매니페스트)</li>
 * <li>모듈 안 구조 C8: {@link io.salvia95.cozy.rules.LayerRules#check}. 자리는 {@link io.salvia95.cozy.rules.Layer}, 자리 표는
 * {@link io.salvia95.cozy.rules.Role}. 입력은 {@link io.salvia95.cozy.rules.JavaType}</li>
 * <li>빈칸 C9: {@link io.salvia95.cozy.rules.SlotRules#check}</li>
 * <li>동작 명세와 만든 JS C12: {@link io.salvia95.cozy.rules.BehaviorRules#check}. 컴포넌트의 이름 줄은
 * {@link io.salvia95.cozy.rules.ComponentContract}</li>
 * <li>이어지지 않은 화면 안의 동작 C13: {@link io.salvia95.cozy.rules.BehaviorWiring#check}. 서버가 내는 화면 이벤트는
 * {@link io.salvia95.cozy.rules.ServerEvents}</li>
 * </ul>
 * 출력은 모두 {@link io.salvia95.cozy.rules.Violation} 목록이다. 예외를 던지지 않고 결과만 돌려준다. cozy-test(컴파일된 클래스)와
 * cozy-cli(소스)가 같은 판단을 쓴다.
 *
 * <p>이 모듈은 JDK와 AttoParser에만 의존한다. Spring이나 다른 cozy 모듈을 참조하지 않는다. 문서: docs/conventions/rules.md,
 * docs/conventions/structure.md, docs/conventions/testing.md
 */
package io.salvia95.cozy.rules;
