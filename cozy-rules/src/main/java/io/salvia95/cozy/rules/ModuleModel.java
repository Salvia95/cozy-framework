package io.salvia95.cozy.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java 모듈 구조. 템플릿 경계의 허용 의존은 여기서만 읽는다 (PRD §7). cozy-test가 Spring Modulith의
 * {@code ApplicationModules}에서 만든다.
 *
 * @param modules 모듈 이름 → 모듈
 */
public record ModuleModel(Map<String, Module> modules) {

	public ModuleModel {
		modules = Map.copyOf(modules);
	}

	public static ModuleModel of(Module... modules) {
		Map<String, Module> map = new LinkedHashMap<>();
		for (Module module : modules) {
			map.put(module.name(), module);
		}
		return new ModuleModel(map);
	}

	public boolean contains(String name) {
		return this.modules.containsKey(name);
	}

	/**
	 * @param name 모듈 이름 (Modulith 기본 규칙 또는 {@code @ApplicationModule(id)})
	 * @param allowedDependencies {@code @ApplicationModule(allowedDependencies)}의 값 그대로 ({@code memo},
	 * {@code memo::api}). 선언하지 않았으면 {@code null}
	 * @param namedInterfaces 모듈의 Named Interface 이름
	 */
	public record Module(String name, List<String> allowedDependencies, Set<String> namedInterfaces) {

		public Module {
			allowedDependencies = (allowedDependencies != null) ? List.copyOf(allowedDependencies) : null;
			namedInterfaces = Set.copyOf(namedInterfaces);
		}

		/** 허용 의존을 선언했는가 */
		public boolean declaresDependencies() {
			return this.allowedDependencies != null;
		}

		/** 이 모듈의 템플릿이 {@code target} 모듈의 {@code api/} 템플릿을 참조해도 되는가 */
		public boolean mayUseTemplatesOf(String target) {
			if (this.allowedDependencies == null) {
				return false;
			}
			return this.allowedDependencies.stream()
				.map((d) -> d.replaceAll("\\s+", ""))
				.anyMatch((d) -> d.equals(target) || d.equals(target + "::api"));
		}
	}
}
