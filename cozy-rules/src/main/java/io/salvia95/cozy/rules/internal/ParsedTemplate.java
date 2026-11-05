package io.salvia95.cozy.rules.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.salvia95.cozy.rules.TemplateFile;
import io.salvia95.cozy.rules.internal.FragmentExpressions.Ref;

/** 템플릿 하나에서 읽은 fragment 선언, 참조, 선택자 대상. */
final class ParsedTemplate {

	final TemplateFile file;

	/** fragment 이름 → 선언 (같은 이름이 두 번이면 첫 번째) */
	final Map<String, FragmentDef> fragments = new LinkedHashMap<>();

	final List<FragmentDef> duplicateFragments = new ArrayList<>();

	final List<Reference> references = new ArrayList<>();

	/** 검사할 수 없는 구문 (R7) */
	final List<Problem> problems = new ArrayList<>();

	/** {@code th:include} 사용 (R7 경고) */
	final List<Problem> includes = new ArrayList<>();

	final Set<String> ids = new LinkedHashSet<>();

	final Set<String> classes = new LinkedHashSet<>();

	final Set<String> elements = new LinkedHashSet<>();

	ParsedTemplate(TemplateFile file) {
		this.file = file;
	}

	/**
	 * @param name fragment 이름
	 * @param params 파라미터 이름
	 * @param line 선언한 줄
	 */
	record FragmentDef(String name, List<String> params, int line) {

		String signature() {
			return this.name + "(" + String.join(", ", this.params) + ")";
		}
	}

	record Reference(Ref ref, int line) {
	}

	record Problem(String text, String why, int line) {
	}
}
