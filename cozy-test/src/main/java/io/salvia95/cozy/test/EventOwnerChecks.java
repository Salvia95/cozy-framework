package io.salvia95.cozy.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import io.salvia95.cozy.rules.ModulePackage;

/**
 * 검사 C11: 이벤트는 주인 모듈의 {@code internal.app.command}(Facade 구현, 리스너)에서만 만든다. 이벤트를 만드는 것은 생성자와
 * static 메서드({@code of})를 부르는 것이다. 받는 쪽은 제한하지 않는다.
 *
 * <p>주인은 이벤트 계약 모듈({@code events.spi.{주인 모듈}})이면 폴더 이름, 옛 자리({@code {모듈}.spi.event})면 그 모듈이다. 폴더가
 * 정해지지 않은 이벤트는 C8이 알린다. 테스트 코드는 보지 않는다.
 */
final class EventOwnerChecks {

	private static final String COZY_EVENT = "io.salvia95.cozy.events.CozyEvent";

	private static final String EVENTS = "events";

	private EventOwnerChecks() {
	}

	static List<String> problems(ApplicationModules modules, String basePackage) {
		List<ModulePackage> list = modules.stream()
			.map((ApplicationModule m) -> new ModulePackage(m.getIdentifier().toString(), m.getBasePackage().getName()))
			.toList();
		return problems(list, new ClassFileImporter().withImportOption(MainClassesOnly.INSTANCE).importPackages(basePackage));
	}

	static List<String> problems(List<ModulePackage> modules, JavaClasses classes) {
		Set<String> found = new TreeSet<>();
		for (JavaClass event : classes) {
			if (event.isInterface() || event.getAllRawInterfaces().stream().noneMatch((i) -> i.getName().equals(COZY_EVENT))) {
				continue;
			}
			ModulePackage module = moduleOf(modules, event.getPackageName());
			String owner = (module == null) ? null : owner(module, event.getPackageName());
			if (owner == null) {
				continue;
			}
			ModulePackage ownerModule = modules.stream().filter((m) -> folder(m).equals(owner)).findFirst().orElse(null);
			String allowed = (ownerModule == null) ? null : ownerModule.basePackage() + ".internal.app.command";
			for (JavaAccess<?> call : creations(event)) {
				JavaClass origin = top(call.getOriginOwner());
				if (origin.equals(event)) {
					continue;
				}
				String from = origin.getPackageName();
				if (allowed == null || !(from.equals(allowed) || from.startsWith(allowed + "."))) {
					found.add("[C11] " + origin.getName() + " → " + event.getName() + " — 이벤트는 주인 모듈(" + owner
							+ ")의 internal.app.command에서만 만듭니다(Facade 구현, 리스너). 다른 모듈이 알릴 일이면 그 모듈의 이벤트를 "
							+ "새로 만드세요.");
				}
			}
		}
		return new ArrayList<>(found);
	}

	/** 이벤트를 만드는 호출: 생성자, static 메서드 */
	private static List<JavaAccess<?>> creations(JavaClass event) {
		List<JavaAccess<?>> calls = new ArrayList<>();
		event.getConstructors().forEach((constructor) -> calls.addAll(constructor.getCallsOfSelf()));
		for (JavaCodeUnit method : event.getMethods()) {
			if (method.getModifiers().contains(JavaModifier.STATIC)) {
				calls.addAll(method.getAccessesToSelf());
			}
		}
		return calls;
	}

	/** 이벤트의 주인 모듈 폴더 이름. events 모듈이면 바로 아래 폴더, 아니면 그 모듈. 정해지지 않으면 null (C8이 알린다) */
	private static String owner(ModulePackage module, String pkg) {
		if (!folder(module).equals(EVENTS)) {
			return folder(module);
		}
		String spi = module.basePackage() + ".spi.";
		if (!pkg.startsWith(spi)) {
			return null;
		}
		String rest = pkg.substring(spi.length());
		return rest.contains(".") ? null : rest;
	}

	private static String folder(ModulePackage module) {
		String base = module.basePackage();
		return base.substring(base.lastIndexOf('.') + 1);
	}

	private static ModulePackage moduleOf(List<ModulePackage> modules, String pkg) {
		ModulePackage found = null;
		for (ModulePackage module : modules) {
			String base = module.basePackage();
			if ((pkg.equals(base) || pkg.startsWith(base + ".")) && (found == null || base.length() > found.basePackage().length())) {
				found = module;
			}
		}
		return found;
	}

	private static JavaClass top(JavaClass type) {
		JavaClass current = type;
		while (current.getEnclosingClass().isPresent()) {
			current = current.getEnclosingClass().get();
		}
		return current;
	}
}
