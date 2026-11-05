package io.salvia95.cozy.rules;

import java.util.List;

/**
 * 검사 입력 (PRD §6.2).
 *
 * @param templates 앱과 프레임워크의 템플릿. 같은 경로가 둘 다 있으면 참조는 앱 파일로 해석한다
 * @param modules Java 모듈 구조
 * @param manifest 프레임워크와 앱 매니페스트의 모든 항목
 * @param allowSameLayer {@code cozy.rules.allow-same-layer}: 같은 계층 fragment 참조 허용 (R4)
 */
public record RulesInput(List<TemplateFile> templates, ModuleModel modules, List<FragmentDeclaration> manifest,
		boolean allowSameLayer) {

	public RulesInput {
		templates = List.copyOf(templates);
		manifest = List.copyOf(manifest);
	}
}
