package io.salvia95.cozy.test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import io.salvia95.cozy.rules.JavaType;
import io.salvia95.cozy.rules.LayerRules;
import io.salvia95.cozy.rules.ModulePackage;
import io.salvia95.cozy.rules.Violation;

/**
 * 검사 C8: 모듈 안의 패키지와 레이어 규칙 (ADR-0029). 판단은 cozy-rules의 {@link LayerRules}가 하고, 여기서는 앱의 컴파일된 클래스를
 * ArchUnit으로 읽어 {@link JavaType}으로 바꾼다. 생성기(cozy-cli)도 같은 규칙과 자리 표({@code Role})를 쓴다 (ADR-0032 §4).
 */
final class LayerChecks {

	private static final Set<String> LISTENER_ANNOTATIONS = Set.of(
			"org.springframework.modulith.events.ApplicationModuleListener",
			"org.springframework.context.event.EventListener",
			"org.springframework.transaction.event.TransactionalEventListener");

	private static final String COZY_EVENT = "io.salvia95.cozy.events.CozyEvent";

	private static final String NAMED_INTERFACE = "org.springframework.modulith.NamedInterface";

	private LayerChecks() {
	}

	static List<String> problems(ApplicationModules modules, String basePackage) {
		List<ModulePackage> list = modules.stream()
			.map((ApplicationModule m) -> new ModulePackage(m.getIdentifier().toString(), m.getBasePackage().getName()))
			.toList();
		JavaClasses classes = new ClassFileImporter().withImportOption(MainClassesOnly.INSTANCE).importPackages(basePackage);
		List<JavaType> types = new ArrayList<>();
		Set<String> packages = new HashSet<>();
		for (JavaClass type : classes) {
			types.add(toType(type));
			packages.add(type.getPackageName());
		}
		return LayerRules.check(list, types, namedInterfaces(classes, packages)).stream().map(Violation::format).toList();
	}

	/** {@code package-info.java}에 {@code @NamedInterface}를 선언한 패키지 */
	private static Set<String> namedInterfaces(JavaClasses classes, Set<String> packages) {
		Set<String> declared = new HashSet<>();
		for (String pkg : packages) {
			boolean named = classes.containPackage(pkg) && classes.getPackage(pkg)
				.tryGetPackageInfo()
				.map((info) -> info.isAnnotatedWith(NAMED_INTERFACE))
				.orElse(false);
			if (named) {
				declared.add(pkg);
			}
		}
		return declared;
	}

	private static JavaType toType(JavaClass type) {
		Set<JavaType.Trait> traits = EnumSet.noneOf(JavaType.Trait.class);
		if (type.isMetaAnnotatedWith("org.springframework.stereotype.Controller")) {
			traits.add(JavaType.Trait.CONTROLLER);
		}
		if (type.isMetaAnnotatedWith("org.springframework.stereotype.Component")) {
			traits.add(JavaType.Trait.COMPONENT);
		}
		if (type.getAllRawInterfaces().stream().anyMatch((i) -> i.getName().equals(COZY_EVENT))) {
			traits.add(JavaType.Trait.COZY_EVENT);
		}
		if (type.getMethods()
			.stream()
			.anyMatch((method) -> LISTENER_ANNOTATIONS.stream().anyMatch(method::isMetaAnnotatedWith))) {
			traits.add(JavaType.Trait.LISTENER);
		}
		List<JavaType.Ref> interfaces = type.getRawInterfaces().stream().map(LayerChecks::ref).toList();
		List<JavaType.Ref> dependencies = new ArrayList<>();
		for (Dependency dependency : type.getDirectDependenciesFromSelf()) {
			dependencies.add(ref(dependency.getTargetClass().getBaseComponentType()));
		}
		return new JavaType(type.getName(), top(type).getName(), type.isInterface(), traits, interfaces, dependencies);
	}

	private static JavaType.Ref ref(JavaClass type) {
		return new JavaType.Ref(type.getName(), top(type).getName(), type.isInterface());
	}

	private static JavaClass top(JavaClass type) {
		JavaClass current = type;
		while (current.getEnclosingClass().isPresent()) {
			current = current.getEnclosingClass().get();
		}
		return current;
	}
}
