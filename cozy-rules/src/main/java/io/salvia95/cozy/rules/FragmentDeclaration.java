package io.salvia95.cozy.rules;

/**
 * fragment 매니페스트({@code cozy/fragments.yml})의 항목 하나 (R8).
 *
 * @param id 매니페스트 안의 이름 ({@code component/badge})
 * @param template 템플릿 이름 ({@code cozy/component/badge})
 * @param signature {@code badge(text, color)}
 * @param source 매니페스트 파일 위치 (메시지용)
 * @param line 항목이 시작하는 줄 (모르면 0)
 */
public record FragmentDeclaration(String id, String template, String signature, String source, int line) {
}
