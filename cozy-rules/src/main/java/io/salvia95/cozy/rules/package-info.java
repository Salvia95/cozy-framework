/**
 * 템플릿 경계·fragment 검사 규칙 R1~R11 (PRD §6.2, §7).
 *
 * <p>입력은 {@link io.salvia95.cozy.rules.RulesInput}(템플릿 파일, 모듈 모델, fragment 매니페스트), 출력은
 * {@link io.salvia95.cozy.rules.Violation} 목록이다. {@link io.salvia95.cozy.rules.TemplateRules#check}는 예외를
 * 던지지 않고 결과만 돌려준다.
 *
 * <p>이 모듈은 JDK와 AttoParser에만 의존한다. Spring이나 다른 cozy 모듈을 참조하지 않는다. 문서:
 * docs/conventions/rules.md
 */
package io.salvia95.cozy.rules;
