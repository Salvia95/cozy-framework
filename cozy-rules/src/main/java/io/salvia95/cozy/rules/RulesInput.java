package io.salvia95.cozy.rules;

import java.util.List;
import java.util.Set;

/**
 * 검사 입력.
 *
 * @param templates 앱과 프레임워크의 템플릿. 같은 경로가 둘 다 있으면 참조는 앱 파일로 해석한다
 * @param modules Java 모듈 구조
 * @param manifest 프레임워크와 앱 매니페스트의 모든 항목
 * @param allowSameLayer {@code cozy.rules.allow-same-layer}: 같은 계층 fragment 참조 허용 (R4)
 * @param icons {@code cozy/icon/tabler :: icon('이름')}에 쓸 수 있는 아이콘 이름 (R13)
 * @param filledIcons {@code cozy/icon/tabler :: iconFilled('이름')}에 쓸 수 있는 아이콘 이름 (R13)
 */
public record RulesInput(List<TemplateFile> templates, ModuleModel modules, List<FragmentDeclaration> manifest,
		boolean allowSameLayer, Set<String> icons, Set<String> filledIcons) {

	public RulesInput {
		templates = List.copyOf(templates);
		manifest = List.copyOf(manifest);
		icons = Set.copyOf(icons);
		filledIcons = Set.copyOf(filledIcons);
	}

	/** 아이콘 목록 없이. 템플릿이 아이콘 이름을 글자로 쓰면 R13으로 실패한다(실패로 닫는다). */
	public RulesInput(List<TemplateFile> templates, ModuleModel modules, List<FragmentDeclaration> manifest,
			boolean allowSameLayer) {
		this(templates, modules, manifest, allowSameLayer, Set.of(), Set.of());
	}
}
