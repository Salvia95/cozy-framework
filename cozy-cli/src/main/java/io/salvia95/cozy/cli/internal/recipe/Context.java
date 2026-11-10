package io.salvia95.cozy.cli.internal.recipe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.salvia95.cozy.cli.internal.AppProject;
import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.FrameworkJar;
import io.salvia95.cozy.cli.internal.JavaSources;
import io.salvia95.cozy.rules.Layer;
import io.salvia95.cozy.rules.Role;

/** 레시피 하나가 쓰는 앱 정보와 계획 */
public final class Context {

	static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

	private static final Pattern MIGRATION = Pattern.compile("V(\\d{12})__.+\\.sql");

	private final AppProject project;

	private final Plan plan;

	private final Supplier<FrameworkJar> framework;

	private final LocalDateTime now;

	private FrameworkJar loaded;

	/**
	 * @param framework cozy-ui jar (아이콘 이름 확인에만 쓴다. 필요할 때 읽는다)
	 * @param now 마이그레이션 이름의 시각
	 */
	public Context(AppProject project, Supplier<FrameworkJar> framework, LocalDateTime now) {
		this.project = project;
		this.plan = new Plan(project.dir());
		this.framework = framework;
		this.now = now;
		if (project.mainClass().isEmpty()) {
			throw new CliException("@SpringBootApplication 클래스를 찾지 못해 모듈 위치를 정할 수 없습니다. cozy check로 이유를 보세요.");
		}
	}

	public AppProject project() {
		return this.project;
	}

	/**
	 * 명령이 고치지 못해 손으로 할 일.
	 *
	 * @param file 고칠 파일
	 * @param what 무엇을 할지
	 * @param snippet 넣을 내용
	 */
	public record Manual(String file, String what, String snippet) {
	}

	private final List<Manual> manual = new ArrayList<>();

	public void manual(String file, String what, String snippet) {
		this.manual.add(new Manual(file, what, snippet));
	}

	public List<Manual> manual() {
		return List.copyOf(this.manual);
	}

	public Plan plan() {
		return this.plan;
	}

	public String basePackage() {
		return this.project.basePackage();
	}

	public String modulePackage(String module) {
		return basePackage() + "." + module;
	}

	/** 모듈. 없으면 실패한다 */
	public AppProject.Module module(String name) {
		AppProject.Module module = this.project.modules().get(name);
		if (module == null) {
			throw new CliException("모듈 '" + name + "'가 없습니다. 먼저 cozy add module " + name + "로 만드세요. 있는 모듈: "
					+ String.join(", ", this.project.modules().keySet()));
		}
		return module;
	}

	/** 모듈이 있는가 (이 계획에서 만든 것 포함) */
	public boolean hasModule(String name) {
		return this.project.modules().containsKey(name) || this.plan.exists(packageInfo(modulePackage(name)));
	}

	public String javaFile(String pkg, String type) {
		return "src/main/java/" + pkg.replace('.', '/') + "/" + type + ".java";
	}

	public String testFile(String pkg, String type) {
		return "src/test/java/" + pkg.replace('.', '/') + "/" + type + ".java";
	}

	public String packageInfo(String pkg) {
		return javaFile(pkg, "package-info");
	}

	/** 역할의 파일 경로 */
	public String roleFile(String module, Role role, String base) {
		return javaFile(role.layer().packageIn(modulePackage(module)), role.typeName(base));
	}

	public String rolePackage(String module, Layer layer) {
		return layer.packageIn(modulePackage(module));
	}

	/** spi, spi.dto, spi.event 패키지의 package-info가 없으면 만든다 */
	public void ensureNamedInterface(String module, Layer layer) {
		String pkg = rolePackage(module, layer);
		String file = packageInfo(pkg);
		if (this.plan.exists(file)) {
			return;
		}
		String what = switch (layer) {
			case SPI -> module + "의 모듈 사이 계약 (Named Interface spi): 쓰기 *Facade, 읽기 *Finder. 시그니처에는 원시 값과 spi.dto 타입만 쓴다.";
			case SPI_DTO -> module + "의 모듈 사이 DTO (Named Interface spi). spi의 하위 패키지는 각각 같은 Named Interface를 선언해야 공개된다.";
			case SPI_EVENT -> module + "의 모듈 사이 이벤트 (Named Interface spi). spi의 하위 패키지는 각각 같은 Named Interface를 선언해야 공개된다.";
			default -> throw new IllegalArgumentException(layer.name());
		};
		this.plan.create(file, new Template().put("what", what).put("pkg", pkg).render("named-interface.java"));
	}

	/** 앱의 cozy 의존. 레시피가 쓰는 스타터가 없으면 실패한다 */
	public void requireStarter(String artifact, String why) {
		boolean present = this.project.uses(artifact) || (artifact.equals("cozy-starter-sqlite")
				&& this.project.uses("cozy-starter-events"));
		if (!present) {
			throw new CliException(why + "에는 " + artifact + "가 필요합니다. pom.xml에 의존을 더하거나 아키타입 옵션("
					+ (artifact.equals("cozy-starter-events") ? "events=true" : "persistence=true") + ")으로 만든 앱에서 실행하세요.");
		}
	}

	/** Tabler Icons 외곽선 아이콘 이름인가 (cozy-ui jar로 확인) */
	public void requireIcon(String icon) {
		if (this.loaded == null) {
			this.loaded = this.framework.get();
		}
		Set<String> icons = this.loaded.icons();
		if (!icons.contains(icon)) {
			throw new CliException("아이콘 '" + icon + "'가 Tabler Icons에 없습니다. 이름은 /cozy/dev/catalog/icons(dev 실행)나 "
					+ "tabler.io/icons에서 찾습니다.");
		}
	}

	/** 다음 마이그레이션 시각: 지금 시각과 마지막 마이그레이션 + 1분 중 늦은 쪽 */
	public String nextMigrationTime() {
		LocalDateTime candidate = this.now.withSecond(0).withNano(0);
		Optional<LocalDateTime> last = migrations().stream()
			.map((name) -> MIGRATION.matcher(name))
			.filter(Matcher::matches)
			.map((m) -> LocalDateTime.parse(m.group(1), MINUTE))
			.max(Comparator.naturalOrder());
		if (last.isPresent() && !candidate.isAfter(last.get())) {
			candidate = last.get().plusMinutes(1);
		}
		return candidate.format(MINUTE);
	}

	/** 이름이 {@code V*__{suffix}.sql}인 마이그레이션이 이미 있으면 그 파일 */
	public Optional<String> existingMigration(String suffix) {
		return migrations().stream()
			.filter((name) -> name.matches("V\\d{12}__" + Pattern.quote(suffix) + "\\.sql"))
			.findFirst()
			.map((name) -> "src/main/resources/db/migration/" + name);
	}

	private List<String> migrations() {
		Path dir = this.project.dir().resolve("src/main/resources/db/migration");
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.map((p) -> p.getFileName().toString()).sorted().toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** 앱 소스에 있는 내비게이션 항목 수 (순서 값을 정한다) */
	public int navItemCount() {
		int count = 0;
		for (JavaSources.Unit unit : this.project.sources().units()) {
			try {
				String text = unit.tree().getSourceFile().getCharContent(true).toString();
				count += text.split("new CozyNavItem\\(", -1).length - 1;
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}
		return count;
	}

	/** 모듈이 쓸 수 있는 Finder: 자기 spi와 선언한 의존 모듈의 spi */
	public List<String> usableFinders(String module) {
		AppProject.Module m = this.project.modules().get(module);
		List<String> modules = new java.util.ArrayList<>();
		modules.add(module);
		if (m != null && m.allowedDependencies() != null) {
			m.allowedDependencies().forEach((d) -> modules.add(d.replaceAll("\\s*::.*", "").strip()));
		}
		return this.project.sources()
			.types()
			.values()
			.stream()
			.filter((t) -> t.isInterface() && t.simpleName().endsWith("Finder"))
			.filter((t) -> modules.stream().anyMatch((x) -> t.pkg().equals(rolePackage(x, Layer.SPI))))
			.map(JavaSources.SourceType::simpleName)
			.toList();
	}
}
