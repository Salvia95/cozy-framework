package io.salvia95.cozy.test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * 검사 C8: 모듈 안의 패키지와 레이어 규칙 (ADR-0029). Spring Modulith(C1)는 모듈 사이의 경계만 보므로 모듈 안의 방향은 여기서
 * 따로 본다.
 *
 * <pre>
 * {모듈}/
 * ├── api/                 HTTP 진입점: 컨트롤러. api/dto는 폼·필터·화면 DTO
 * ├── spi/                 모듈 사이 계약 (@NamedInterface("spi")): *Facade(쓰기), *Finder(읽기) 인터페이스
 * │   ├── dto/             모듈 사이 DTO
 * │   └── event/           CozyEvent
 * └── internal/            밀폐된 구현
 *     ├── app/command/     *FacadeImpl, *Service + *ServiceImpl, *Listener
 *     ├── app/query/       *FinderImpl, *Query + *QueryImpl, *Projection + *ProjectionImpl
 *     ├── domain/          엔티티·VO·도메인 규칙 (리포지토리·서비스 없음)
 *     ├── infra/           MyBatis Mapper, 행, 저장소 구현, 외부 연동
 *     └── config/          모듈 전용 설정·빈
 * </pre>
 *
 * 어긴 곳마다 위치와 고치는 방법을 한 줄로 보고한다. 예외 목록은 두지 않는다(실패로 닫는다).
 */
final class LayerChecks {

	private static final Set<String> MODULE_FOLDERS = Set.of("api", "spi", "internal");

	private static final Set<String> JDBC_TYPES = Set.of("org.springframework.jdbc.core.JdbcTemplate",
			"org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate",
			"org.springframework.jdbc.core.simple.JdbcClient", "org.apache.ibatis.session.SqlSession");

	private static final Set<String> LISTENER_ANNOTATIONS = Set.of(
			"org.springframework.modulith.events.ApplicationModuleListener",
			"org.springframework.context.event.EventListener",
			"org.springframework.transaction.event.TransactionalEventListener");

	private static final String COZY_EVENT = "io.salvia95.cozy.events.CozyEvent";

	private LayerChecks() {
	}

	/** 모듈 하나: 이름과 기준 패키지 */
	record Module(String name, String pkg) {

		String sub(String path) {
			return this.pkg + "." + path;
		}
	}

	static List<String> problems(ApplicationModules modules, String basePackage) {
		List<Module> list = modules.stream()
			.map((ApplicationModule m) -> new Module(m.getIdentifier().toString(), m.getBasePackage().getName()))
			.toList();
		JavaClasses classes = new ClassFileImporter().withImportOption(MainClassesOnly.INSTANCE).importPackages(basePackage);
		Set<String> problems = new TreeSet<>();
		namedInterfaces(problems, list, classes);
		for (JavaClass type : classes) {
			if (type.getSimpleName().equals("package-info") || type.getName().contains("$$")) {
				continue;
			}
			Module module = moduleOf(list, type);
			if (module == null) {
				continue;
			}
			if (type.isTopLevelClass()) {
				placement(problems, module, type);
			}
			for (Dependency dependency : type.getDirectDependenciesFromSelf()) {
				dependency(problems, list, module, type, dependency);
			}
		}
		return List.copyOf(problems);
	}

	/**
	 * spi와 그 하위 패키지(dto, event)는 각각 {@code @NamedInterface("spi")}를 선언해야 다른 모듈에 공개된다. Modulith는 하위
	 * 패키지로 넓혀 주지 않으므로, 빠지면 C1이 "non-exposed type"으로 실패한다. 그 전에 고칠 곳을 알려 준다.
	 */
	private static void namedInterfaces(Set<String> problems, List<Module> modules, JavaClasses classes) {
		Set<String> packages = new TreeSet<>();
		for (JavaClass type : classes) {
			Module module = moduleOf(modules, type);
			if (module != null && in(type.getPackageName(), module.sub("spi")) && !type.getSimpleName().equals("package-info")) {
				packages.add(type.getPackageName());
			}
		}
		for (String pkg : packages) {
			boolean declared = classes.containPackage(pkg) && classes.getPackage(pkg)
				.tryGetPackageInfo()
				.map((info) -> info.isAnnotatedWith("org.springframework.modulith.NamedInterface"))
				.orElse(false);
			if (!declared) {
				problems.add("[C8] " + pkg + " — spi 패키지마다 package-info.java에 @NamedInterface(\"spi\")를 둡니다. Spring Modulith는 "
						+ "하위 패키지(dto, event)로 넓혀 주지 않습니다. 빠지면 다른 모듈이 이 타입을 쓸 수 없습니다(C1 non-exposed type).");
			}
		}
	}

	// ── 배치와 이름 ───────────────────────────────────────────────

	private static void placement(Set<String> problems, Module m, JavaClass type) {
		String pkg = type.getPackageName();
		String name = type.getSimpleName();
		String where = "[C8] " + type.getName() + " — ";
		if (pkg.equals(m.pkg())) {
			problems.add(where + "모듈 루트(" + m.pkg() + ")에는 package-info.java만 둡니다. 컨트롤러는 " + m.sub("api")
					+ ", 모듈 사이 계약은 " + m.sub("spi") + ", 구현은 " + m.sub("internal") + ".app·domain·infra·config로 옮기세요.");
			return;
		}
		String rest = pkg.substring(m.pkg().length() + 1);
		String top = rest.split("\\.")[0];
		if (!MODULE_FOLDERS.contains(top)) {
			problems.add(where + m.pkg() + " 아래에는 api, spi, internal만 둡니다. 화면 DTO는 " + m.sub("api.dto")
					+ ", 모듈 사이 DTO는 " + m.sub("spi.dto") + ", 구현은 " + m.sub("internal") + " 아래로 옮기세요.");
			return;
		}
		if (implementsCozyEvent(type) && !in(pkg, m.sub("spi.event"))) {
			problems.add(where + "CozyEvent는 " + m.sub("spi.event") + "에 둡니다. 이벤트는 모듈 사이 계약입니다.");
		}
		switch (top) {
			case "api" -> {
				if (pkg.equals(m.sub("api"))) {
					if (!type.isMetaAnnotatedWith("org.springframework.stereotype.Controller")) {
						problems.add(where + m.sub("api") + "에는 컨트롤러만 둡니다. 폼·필터·화면 DTO는 " + m.sub("api.dto")
								+ ", 설정은 " + m.sub("internal.config") + "로 옮기세요.");
					}
				}
				else if (!in(pkg, m.sub("api.dto"))) {
					problems.add(where + m.sub("api") + " 아래 하위 패키지는 dto만 둡니다.");
				}
			}
			case "spi" -> {
				if (pkg.equals(m.sub("spi"))) {
					if (!type.isInterface() || !(name.endsWith("Facade") || name.endsWith("Finder"))) {
						problems.add(where + m.sub("spi") + "에는 *Facade(쓰기)·*Finder(읽기) 인터페이스만 둡니다. DTO는 "
								+ m.sub("spi.dto") + ", 이벤트는 " + m.sub("spi.event") + ", 구현은 " + m.sub("internal.app")
								+ ".command·query로 옮기세요.");
					}
				}
				else if (!in(pkg, m.sub("spi.dto")) && !in(pkg, m.sub("spi.event"))) {
					problems.add(where + m.sub("spi") + " 아래 하위 패키지는 dto와 event만 둡니다.");
				}
				else if (type.isMetaAnnotatedWith("org.springframework.stereotype.Component")) {
					problems.add(where + "spi의 DTO·이벤트는 스프링 빈이 아닙니다. 구현은 " + m.sub("internal.app") + "에 둡니다.");
				}
			}
			default -> internalPlacement(problems, m, type, pkg, name, where);
		}
	}

	private static void internalPlacement(Set<String> problems, Module m, JavaClass type, String pkg, String name,
			String where) {
		boolean command = in(pkg, m.sub("internal.app.command"));
		boolean query = in(pkg, m.sub("internal.app.query"));
		boolean domain = in(pkg, m.sub("internal.domain"));
		boolean infra = in(pkg, m.sub("internal.infra"));
		boolean config = in(pkg, m.sub("internal.config"));
		if (!command && !query && !domain && !infra && !config) {
			problems.add(where + m.sub("internal") + " 아래에는 app.command, app.query, domain, infra, config만 둡니다. "
					+ "쓰기 구현은 app.command, 읽기 구현은 app.query로 옮기세요(app 루트에 공용 코드를 두지 않습니다).");
			return;
		}
		if (domain && (name.endsWith("Repository") || name.endsWith("Service") || name.endsWith("Mapper")
				|| name.endsWith("Impl"))) {
			problems.add(where + "domain에는 엔티티·VO·도메인 규칙만 둡니다. Mapper·저장소는 " + m.sub("internal.infra")
					+ ", 서비스는 " + m.sub("internal.app.command") + "로 옮기세요.");
		}
		if (infra && (name.endsWith("Query") || name.endsWith("Projection") || name.endsWith("Facade")
				|| name.endsWith("Finder") || name.endsWith("Service"))) {
			problems.add(where + "infra에는 Mapper·행·저장소 구현·외부 연동만 둡니다. " + name + "는 "
					+ m.sub("internal.app") + "의 command나 query에 둡니다.");
		}
		if (command || query) {
			appPlacement(problems, m, type, name, command, where);
		}
	}

	/** app의 클래스는 항상 인터페이스 + Impl, 또는 이벤트 리스너 */
	private static void appPlacement(Set<String> problems, Module m, JavaClass type, String name, boolean command,
			String where) {
		String layer = command ? "app.command" : "app.query";
		if (type.isInterface()) {
			boolean ok = command ? name.endsWith("Service") : (name.endsWith("Query") || name.endsWith("Projection"));
			if (!ok) {
				problems.add(where + layer + "의 인터페이스 이름은 " + (command ? "*Service" : "*Query(쓰기가 확인용으로 쓰는 단순 조회)"
						+ " 또는 *Projection(집계 조회)") + "입니다. 모듈 사이 계약(*Facade, *Finder)은 " + m.sub("spi") + "에 둡니다.");
			}
			return;
		}
		if (command && name.endsWith("Listener") && hasListenerMethod(type)) {
			return;
		}
		if (!name.endsWith("Impl")) {
			problems.add(where + layer + "의 클래스는 인터페이스 + Impl 쌍으로 둡니다(" + name + " → " + name + "와 "
					+ name + "Impl). 이벤트 리스너는 *Listener로 app.command에 둡니다. 데이터는 " + m.sub("spi.dto") + "나 "
					+ m.sub("internal.domain") + "에 둡니다.");
			return;
		}
		String contract = name.substring(0, name.length() - "Impl".length());
		JavaClass implemented = type.getRawInterfaces()
			.stream()
			.filter((i) -> i.getSimpleName().equals(contract))
			.findFirst()
			.orElse(null);
		if (implemented == null) {
			problems.add(where + name + "는 같은 이름의 인터페이스 " + contract + "를 구현해야 합니다.");
			return;
		}
		boolean facade = contract.endsWith("Facade");
		boolean finder = contract.endsWith("Finder");
		boolean service = contract.endsWith("Service");
		boolean read = contract.endsWith("Query") || contract.endsWith("Projection");
		if ((facade || service) && !command) {
			problems.add(where + name + "는 쓰기 구현이라 " + m.sub("internal.app.command") + "에 둡니다.");
		}
		if ((finder || read) && command) {
			problems.add(where + name + "는 읽기 구현이라 " + m.sub("internal.app.query") + "에 둡니다.");
		}
		if ((facade || finder) && !in(implemented.getPackageName(), m.sub("spi"))) {
			problems.add(where + name + "가 구현하는 " + contract + "는 같은 모듈의 " + m.sub("spi") + " 인터페이스여야 합니다.");
		}
	}

	// ── 의존 방향 ───────────────────────────────────────────────

	private static void dependency(Set<String> problems, List<Module> modules, Module m, JavaClass origin,
			Dependency dependency) {
		JavaClass target = dependency.getTargetClass().getBaseComponentType();
		String to = target.getPackageName();
		String from = origin.getPackageName();
		String edge = "[C8] " + top(origin).getName() + " → " + top(target).getName() + " — ";
		if (JDBC_TYPES.contains(target.getName()) || to.equals("java.sql")) {
			if (in(from, m.sub("internal.app.command"))) {
				problems.add(edge + "app.command는 JDBC·SqlSession을 직접 쓰지 않습니다. SQL은 " + m.sub("internal.infra")
						+ "의 Mapper가 실행합니다.");
			}
			return;
		}
		Module other = moduleOf(modules, target);
		if (other == null || top(origin).equals(top(target))) {
			return;
		}
		boolean same = other.equals(m);
		if (in(from, m.sub("api")) && in(to, other.sub("internal"))) {
			problems.add(edge + "api는 internal을 쓰지 않습니다. 컨트롤러는 " + other.sub("spi")
					+ "의 Facade·Finder와 DTO만 씁니다.");
		}
		if (in(from, m.sub("spi")) && (in(to, other.sub("internal")) || in(to, other.sub("api")))) {
			problems.add(edge + "spi는 internal·api 타입을 쓰지 않습니다. 시그니처에는 원시 값과 spi.dto 타입만 둡니다(도메인 모델을 "
					+ "모듈 밖에 내보내지 않습니다).");
		}
		if (in(from, m.sub("internal")) && in(to, other.sub("api"))) {
			problems.add(edge + "internal은 api(컨트롤러·화면 DTO)를 쓰지 않습니다. 화면 DTO로 바꾸는 일은 컨트롤러가 합니다.");
		}
		if (in(from, m.sub("internal.domain"))) {
			if (same && (in(to, m.sub("internal.app")) || in(to, m.sub("internal.infra"))
					|| in(to, m.sub("internal.config")))) {
				problems.add(edge + "domain은 app·infra·config에 의존하지 않습니다. 도메인 규칙은 다른 레이어를 모릅니다.");
			}
			if (in(to, other.sub("spi")) || in(to, other.sub("api"))) {
				problems.add(edge + "domain은 spi·api 타입을 쓰지 않습니다. 변환은 " + m.sub("internal.app") + "가 합니다.");
			}
		}
		if (in(from, m.sub("internal.infra"))) {
			if (same && in(to, m.sub("internal.app"))) {
				problems.add(edge + "infra는 app에 의존하지 않습니다(app → infra 방향만).");
			}
			if (!same && in(to, other.sub("spi"))) {
				problems.add(edge + "infra는 다른 모듈의 spi를 쓰지 않습니다. 모듈 사이 조합은 " + m.sub("internal.app")
						+ "가 합니다.");
			}
		}
		if (same && in(from, m.sub("internal.app.command")) && in(to, m.sub("internal.app.query"))
				&& !(target.isInterface() && target.getSimpleName().endsWith("Query"))) {
			problems.add(edge + "app.command는 app.query의 *Query 인터페이스만 씁니다(*Finder·*Projection·*Impl 금지).");
		}
		if (same && in(from, m.sub("internal.app.query")) && in(to, m.sub("internal.app.command"))) {
			problems.add(edge + "app.query는 app.command에 의존하지 않습니다(읽기가 쓰기를 부르지 않습니다).");
		}
		if (target.isInterface() && target.getSimpleName().endsWith("Facade") && to.equals(other.sub("spi"))) {
			boolean caller = in(from, m.sub("api")) || (same && in(from, m.sub("internal.app.command")));
			if (!caller) {
				problems.add(edge + "쓰기 Facade는 컨트롤러(api)와 소유 모듈의 app.command만 부릅니다. 다른 모듈의 쓰기는 이벤트("
						+ other.sub("spi.event") + ")로 알리고 그 모듈이 직접 하게 하세요.");
			}
		}
	}

	// ── 도움 ──────────────────────────────────────────────────

	private static Module moduleOf(List<Module> modules, JavaClass type) {
		String pkg = type.getPackageName();
		Module found = null;
		for (Module module : modules) {
			if (in(pkg, module.pkg()) && (found == null || module.pkg().length() > found.pkg().length())) {
				found = module;
			}
		}
		return found;
	}

	private static boolean in(String pkg, String root) {
		return pkg.equals(root) || pkg.startsWith(root + ".");
	}

	private static JavaClass top(JavaClass type) {
		JavaClass current = type;
		while (current.getEnclosingClass().isPresent()) {
			current = current.getEnclosingClass().get();
		}
		return current;
	}

	private static boolean implementsCozyEvent(JavaClass type) {
		return type.getAllRawInterfaces().stream().anyMatch((i) -> i.getName().equals(COZY_EVENT));
	}

	private static boolean hasListenerMethod(JavaClass type) {
		return type.getMethods()
			.stream()
			.anyMatch((method) -> LISTENER_ANNOTATIONS.stream().anyMatch(method::isMetaAnnotatedWith));
	}
}
