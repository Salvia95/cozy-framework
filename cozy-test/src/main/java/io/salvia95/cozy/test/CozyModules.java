package io.salvia95.cozy.test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleInformation;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.NamedInterface;

import io.salvia95.cozy.rules.ModuleModel;

/** Spring Modulith 모듈 구조를 cozy-rules의 {@link ModuleModel}로 바꾼다 (PRD §6.5 검사 2). */
public final class CozyModules {

	private static final Map<Class<?>, ApplicationModules> CACHE = new ConcurrentHashMap<>();

	private CozyModules() {
	}

	/** 앱 클래스의 모듈. 같은 JVM 안에서는 한 번만 분석한다 */
	public static ApplicationModules of(Class<?> applicationClass) {
		return CACHE.computeIfAbsent(applicationClass, (type) -> ApplicationModules.of(type, MainClassesOnly.INSTANCE));
	}

	public static ModuleModel model(ApplicationModules modules) {
		Map<String, ModuleModel.Module> result = new LinkedHashMap<>();
		for (ApplicationModule module : modules) {
			String name = module.getIdentifier().toString();
			List<String> declared = ApplicationModuleInformation.of(module.getBasePackage()).getDeclaredDependencies();
			boolean open = declared.size() == 1 && org.springframework.modulith.ApplicationModule.OPEN_TOKEN.equals(declared.get(0));
			Set<String> namedInterfaces = module.getNamedInterfaces()
				.stream()
				.filter((ni) -> !ni.isUnnamed())
				.map(NamedInterface::getName)
				.collect(Collectors.toSet());
			result.put(name, new ModuleModel.Module(name, open ? null : declared, namedInterfaces));
		}
		return new ModuleModel(result);
	}
}
