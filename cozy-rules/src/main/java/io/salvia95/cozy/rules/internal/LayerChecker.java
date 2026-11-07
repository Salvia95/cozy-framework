package io.salvia95.cozy.rules.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.salvia95.cozy.rules.JavaType;
import io.salvia95.cozy.rules.Layer;
import io.salvia95.cozy.rules.ModulePackage;
import io.salvia95.cozy.rules.Severity;
import io.salvia95.cozy.rules.Violation;

/**
 * 검사 C8 (ADR-0029). 모듈 안의 배치와 이름, 레이어 방향. 어긴 곳마다 위치와 고치는 방법을 한 줄로 보고한다. 예외 목록은 두지 않는다
 * (실패로 닫는다). 같은 메시지는 한 번만 보고하고, 메시지 순서로 정렬한다.
 */
public final class LayerChecker {

	private static final Set<String> MODULE_FOLDERS = Set.of("api", "spi", "internal");

	private static final Set<String> JDBC_TYPES = Set.of("org.springframework.jdbc.core.JdbcTemplate",
			"org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate",
			"org.springframework.jdbc.core.simple.JdbcClient", "org.apache.ibatis.session.SqlSession");

	private final List<ModulePackage> modules;

	private final Map<String, Violation> found = new TreeMap<>();

	private LayerChecker(List<ModulePackage> modules) {
		this.modules = modules;
	}

	public static List<Violation> check(List<ModulePackage> modules, List<JavaType> types,
			Set<String> namedInterfacePackages) {
		LayerChecker checker = new LayerChecker(List.copyOf(modules));
		List<JavaType> checked = types.stream()
			.filter((t) -> !t.simpleName().equals("package-info") && !t.name().contains("$$"))
			.toList();
		checker.namedInterfaces(checked, namedInterfacePackages);
		for (JavaType type : checked) {
			ModulePackage module = checker.moduleOf(type.packageName());
			if (module == null) {
				continue;
			}
			if (type.isTopLevel()) {
				checker.placement(module, type);
			}
			for (JavaType.Ref dependency : type.dependencies()) {
				checker.dependency(module, type, dependency);
			}
		}
		return List.copyOf(checker.found.values());
	}

	/**
	 * spi와 그 하위 패키지(dto, event)는 각각 {@code @NamedInterface("spi")}를 선언해야 다른 모듈에 공개된다. Modulith는 하위
	 * 패키지로 넓혀 주지 않으므로, 빠지면 C1이 "non-exposed type"으로 실패한다. 그 전에 고칠 곳을 알려 준다.
	 */
	private void namedInterfaces(List<JavaType> types, Set<String> declared) {
		Set<String> packages = new TreeSet<>();
		for (JavaType type : types) {
			ModulePackage module = moduleOf(type.packageName());
			if (module != null && in(type.packageName(), module.of(Layer.SPI))) {
				packages.add(type.packageName());
			}
		}
		for (String pkg : packages) {
			if (!declared.contains(pkg)) {
				report(pkg, null, "spi 패키지마다 package-info.java에 @NamedInterface(\"spi\")를 둡니다. Spring Modulith는 "
						+ "하위 패키지(dto, event)로 넓혀 주지 않습니다. 빠지면 다른 모듈이 이 타입을 쓸 수 없습니다(C1 non-exposed type).");
			}
		}
	}

	// ── 배치와 이름 ───────────────────────────────────────────────

	private void placement(ModulePackage m, JavaType type) {
		String pkg = type.packageName();
		String name = type.simpleName();
		String where = type.name();
		if (pkg.equals(m.basePackage())) {
			report(where, null, "모듈 루트(" + m.basePackage() + ")에는 package-info.java만 둡니다. 컨트롤러는 " + m.of(Layer.API)
					+ ", 모듈 사이 계약은 " + m.of(Layer.SPI) + ", 구현은 " + m.sub("internal") + ".app·domain·infra·config로 옮기세요.");
			return;
		}
		String rest = pkg.substring(m.basePackage().length() + 1);
		String top = rest.split("\\.")[0];
		if (!MODULE_FOLDERS.contains(top)) {
			report(where, null, m.basePackage() + " 아래에는 api, spi, internal만 둡니다. 화면 DTO는 " + m.of(Layer.API_DTO)
					+ ", 모듈 사이 DTO는 " + m.of(Layer.SPI_DTO) + ", 구현은 " + m.sub("internal") + " 아래로 옮기세요.");
			return;
		}
		if (type.has(JavaType.Trait.COZY_EVENT) && !in(pkg, m.of(Layer.SPI_EVENT))) {
			report(where, null, "CozyEvent는 " + m.of(Layer.SPI_EVENT) + "에 둡니다. 이벤트는 모듈 사이 계약입니다.");
		}
		switch (top) {
			case "api" -> {
				if (pkg.equals(m.of(Layer.API))) {
					if (!type.has(JavaType.Trait.CONTROLLER)) {
						report(where, null, m.of(Layer.API) + "에는 컨트롤러만 둡니다. 폼·필터·화면 DTO는 " + m.of(Layer.API_DTO)
								+ ", 설정은 " + m.of(Layer.CONFIG) + "로 옮기세요.");
					}
				}
				else if (!in(pkg, m.of(Layer.API_DTO))) {
					report(where, null, m.of(Layer.API) + " 아래 하위 패키지는 dto만 둡니다.");
				}
			}
			case "spi" -> {
				if (pkg.equals(m.of(Layer.SPI))) {
					if (!type.isInterface() || !(name.endsWith("Facade") || name.endsWith("Finder"))) {
						report(where, null, m.of(Layer.SPI) + "에는 *Facade(쓰기)·*Finder(읽기) 인터페이스만 둡니다. DTO는 "
								+ m.of(Layer.SPI_DTO) + ", 이벤트는 " + m.of(Layer.SPI_EVENT) + ", 구현은 " + m.sub("internal.app")
								+ ".command·query로 옮기세요.");
					}
				}
				else if (!in(pkg, m.of(Layer.SPI_DTO)) && !in(pkg, m.of(Layer.SPI_EVENT))) {
					report(where, null, m.of(Layer.SPI) + " 아래 하위 패키지는 dto와 event만 둡니다.");
				}
				else if (type.has(JavaType.Trait.COMPONENT)) {
					report(where, null, "spi의 DTO·이벤트는 스프링 빈이 아닙니다. 구현은 " + m.sub("internal.app") + "에 둡니다.");
				}
			}
			default -> internalPlacement(m, type, pkg, name, where);
		}
	}

	private void internalPlacement(ModulePackage m, JavaType type, String pkg, String name, String where) {
		boolean command = in(pkg, m.of(Layer.APP_COMMAND));
		boolean query = in(pkg, m.of(Layer.APP_QUERY));
		boolean domain = in(pkg, m.of(Layer.DOMAIN));
		boolean infra = in(pkg, m.of(Layer.INFRA));
		boolean config = in(pkg, m.of(Layer.CONFIG));
		if (!command && !query && !domain && !infra && !config) {
			report(where, null, m.sub("internal") + " 아래에는 app.command, app.query, domain, infra, config만 둡니다. "
					+ "쓰기 구현은 app.command, 읽기 구현은 app.query로 옮기세요(app 루트에 공용 코드를 두지 않습니다).");
			return;
		}
		if (domain && (name.endsWith("Repository") || name.endsWith("Service") || name.endsWith("Mapper")
				|| name.endsWith("Impl"))) {
			report(where, null, "domain에는 엔티티·VO·도메인 규칙만 둡니다. Mapper·저장소는 " + m.of(Layer.INFRA)
					+ ", 서비스는 " + m.of(Layer.APP_COMMAND) + "로 옮기세요.");
		}
		if (infra && (name.endsWith("Query") || name.endsWith("Projection") || name.endsWith("Facade")
				|| name.endsWith("Finder") || name.endsWith("Service"))) {
			report(where, null, "infra에는 Mapper·행·저장소 구현·외부 연동만 둡니다. " + name + "는 " + m.sub("internal.app")
					+ "의 command나 query에 둡니다.");
		}
		if (command || query) {
			appPlacement(m, type, name, command, where);
		}
	}

	/** app의 클래스는 항상 인터페이스 + Impl, 또는 이벤트 리스너 */
	private void appPlacement(ModulePackage m, JavaType type, String name, boolean command, String where) {
		String layer = command ? "app.command" : "app.query";
		if (type.isInterface()) {
			boolean ok = command ? name.endsWith("Service") : (name.endsWith("Query") || name.endsWith("Projection"));
			if (!ok) {
				report(where, null, layer + "의 인터페이스 이름은 " + (command ? "*Service" : "*Query(쓰기가 확인용으로 쓰는 단순 조회)"
						+ " 또는 *Projection(집계 조회)") + "입니다. 모듈 사이 계약(*Facade, *Finder)은 " + m.of(Layer.SPI) + "에 둡니다.");
			}
			return;
		}
		if (command && name.endsWith("Listener") && type.has(JavaType.Trait.LISTENER)) {
			return;
		}
		if (!name.endsWith("Impl")) {
			report(where, null, layer + "의 클래스는 인터페이스 + Impl 쌍으로 둡니다(" + name + " → " + name + "와 " + name
					+ "Impl). 이벤트 리스너는 *Listener로 app.command에 둡니다. 데이터는 " + m.of(Layer.SPI_DTO) + "나 "
					+ m.of(Layer.DOMAIN) + "에 둡니다.");
			return;
		}
		String contract = name.substring(0, name.length() - "Impl".length());
		JavaType.Ref implemented = type.interfaces()
			.stream()
			.filter((i) -> i.simpleName().equals(contract))
			.findFirst()
			.orElse(null);
		if (implemented == null) {
			report(where, null, name + "는 같은 이름의 인터페이스 " + contract + "를 구현해야 합니다.");
			return;
		}
		boolean facade = contract.endsWith("Facade");
		boolean finder = contract.endsWith("Finder");
		boolean service = contract.endsWith("Service");
		boolean read = contract.endsWith("Query") || contract.endsWith("Projection");
		if ((facade || service) && !command) {
			report(where, null, name + "는 쓰기 구현이라 " + m.of(Layer.APP_COMMAND) + "에 둡니다.");
		}
		if ((finder || read) && command) {
			report(where, null, name + "는 읽기 구현이라 " + m.of(Layer.APP_QUERY) + "에 둡니다.");
		}
		if ((facade || finder) && !in(implemented.packageName(), m.of(Layer.SPI))) {
			report(where, null, name + "가 구현하는 " + contract + "는 같은 모듈의 " + m.of(Layer.SPI) + " 인터페이스여야 합니다.");
		}
	}

	// ── 의존 방향 ───────────────────────────────────────────────

	private void dependency(ModulePackage m, JavaType origin, JavaType.Ref target) {
		String to = target.packageName();
		String from = origin.packageName();
		String edgeFrom = origin.topLevelName();
		String edgeTo = target.topLevelName();
		if (JDBC_TYPES.contains(target.name()) || to.equals("java.sql")) {
			if (in(from, m.of(Layer.APP_COMMAND))) {
				report(edgeFrom, edgeTo, "app.command는 JDBC·SqlSession을 직접 쓰지 않습니다. SQL은 " + m.of(Layer.INFRA)
						+ "의 Mapper가 실행합니다.");
			}
			return;
		}
		ModulePackage other = moduleOf(to);
		if (other == null || edgeFrom.equals(edgeTo)) {
			return;
		}
		boolean same = other.equals(m);
		if (in(from, m.of(Layer.API)) && in(to, other.sub("internal"))) {
			report(edgeFrom, edgeTo, "api는 internal을 쓰지 않습니다. 컨트롤러는 " + other.of(Layer.SPI) + "의 Facade·Finder와 DTO만 씁니다.");
		}
		if (in(from, m.of(Layer.SPI)) && (in(to, other.sub("internal")) || in(to, other.of(Layer.API)))) {
			report(edgeFrom, edgeTo, "spi는 internal·api 타입을 쓰지 않습니다. 시그니처에는 원시 값과 spi.dto 타입만 둡니다(도메인 모델을 "
					+ "모듈 밖에 내보내지 않습니다).");
		}
		if (in(from, m.sub("internal")) && in(to, other.of(Layer.API))) {
			report(edgeFrom, edgeTo, "internal은 api(컨트롤러·화면 DTO)를 쓰지 않습니다. 화면 DTO로 바꾸는 일은 컨트롤러가 합니다.");
		}
		if (in(from, m.of(Layer.DOMAIN))) {
			if (same && (in(to, m.sub("internal.app")) || in(to, m.of(Layer.INFRA)) || in(to, m.of(Layer.CONFIG)))) {
				report(edgeFrom, edgeTo, "domain은 app·infra·config에 의존하지 않습니다. 도메인 규칙은 다른 레이어를 모릅니다.");
			}
			if (in(to, other.of(Layer.SPI)) || in(to, other.of(Layer.API))) {
				report(edgeFrom, edgeTo, "domain은 spi·api 타입을 쓰지 않습니다. 변환은 " + m.sub("internal.app") + "가 합니다.");
			}
		}
		if (in(from, m.of(Layer.INFRA))) {
			if (same && in(to, m.sub("internal.app"))) {
				report(edgeFrom, edgeTo, "infra는 app에 의존하지 않습니다(app → infra 방향만).");
			}
			if (!same && in(to, other.of(Layer.SPI))) {
				report(edgeFrom, edgeTo, "infra는 다른 모듈의 spi를 쓰지 않습니다. 모듈 사이 조합은 " + m.sub("internal.app") + "가 합니다.");
			}
		}
		if (same && in(from, m.of(Layer.APP_COMMAND)) && in(to, m.of(Layer.APP_QUERY))
				&& !(target.isInterface() && target.simpleName().endsWith("Query"))) {
			report(edgeFrom, edgeTo, "app.command는 app.query의 *Query 인터페이스만 씁니다(*Finder·*Projection·*Impl 금지).");
		}
		if (same && in(from, m.of(Layer.APP_QUERY)) && in(to, m.of(Layer.APP_COMMAND))) {
			report(edgeFrom, edgeTo, "app.query는 app.command에 의존하지 않습니다(읽기가 쓰기를 부르지 않습니다).");
		}
		if (target.isInterface() && target.simpleName().endsWith("Facade") && to.equals(other.of(Layer.SPI))) {
			boolean caller = in(from, m.of(Layer.API)) || (same && in(from, m.of(Layer.APP_COMMAND)));
			if (!caller) {
				report(edgeFrom, edgeTo, "쓰기 Facade는 컨트롤러(api)와 소유 모듈의 app.command만 부릅니다. 다른 모듈의 쓰기는 이벤트("
						+ other.of(Layer.SPI_EVENT) + ")로 알리고 그 모듈이 직접 하게 하세요.");
			}
		}
	}

	// ── 도움 ──────────────────────────────────────────────────

	private void report(String location, String reference, String message) {
		Violation violation = new Violation(location, 0, "C8", Severity.ERROR, reference, message);
		this.found.putIfAbsent(violation.format(), violation);
	}

	private ModulePackage moduleOf(String pkg) {
		ModulePackage found = null;
		for (ModulePackage module : this.modules) {
			if (in(pkg, module.basePackage())
					&& (found == null || module.basePackage().length() > found.basePackage().length())) {
				found = module;
			}
		}
		return found;
	}

	private static boolean in(String pkg, String root) {
		return pkg.equals(root) || pkg.startsWith(root + ".");
	}
}
