package io.salvia95.cozy.cli.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;

import com.sun.source.tree.AnnotationTree;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.salvia95.cozy.rules.ModuleModel;
import io.salvia95.cozy.rules.ModulePackage;
import io.salvia95.cozy.rules.Severity;
import io.salvia95.cozy.rules.Violation;

/**
 * cozy 명령이 다루는 앱 프로젝트: 폴더, pom, 메인 클래스, 모듈. 모듈은 Spring Modulith와 같이 메인 패키지 바로 아래 패키지다.
 * 이름은 {@code @ApplicationModule(id)}가 있으면 그것이다.
 */
public final class AppProject {

	static final String COZY_GROUP = "io.salvia95.cozy";

	private final Path dir;

	private final String cozyVersion;

	private final Set<String> cozyDependencies;

	private final JavaSources sources;

	private final List<Violation> problems = new ArrayList<>();

	private JavaSources.SourceType mainClass;

	private final Map<String, Module> modules = new TreeMap<>();

	/**
	 * 앱 모듈 하나.
	 *
	 * @param allowedDependencies 선언한 의존. 선언하지 않았으면(open) {@code null}
	 * @param namedInterfaces Named Interface 이름
	 * @param namedInterfacePackages {@code @NamedInterface}를 선언한 패키지
	 */
	public record Module(String name, String basePackage, List<String> allowedDependencies, Set<String> namedInterfaces,
			Set<String> namedInterfacePackages) {

		public ModulePackage modulePackage() {
			return new ModulePackage(this.name, this.basePackage);
		}
	}

	private AppProject(Path dir, String cozyVersion, Set<String> cozyDependencies) {
		this.dir = dir;
		this.cozyVersion = cozyVersion;
		this.cozyDependencies = cozyDependencies;
		this.sources = JavaSources.read(dir);
		this.sources.problems()
			.forEach((p) -> this.problems.add(new Violation(p.location(), p.line(), "CLI", Severity.ERROR, null, p.message())));
		findMainClass();
		if (this.mainClass != null) {
			findModules();
		}
	}

	/**
	 * 시작 폴더에서 위로 올라가며 {@code pom.xml}과 {@code src/main/java}가 함께 있는 폴더를 찾는다.
	 *
	 * @throws CliException 찾지 못했을 때
	 */
	public static AppProject find(Path start) {
		Path current = start.toAbsolutePath().normalize();
		while (current != null) {
			if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("src/main/java"))) {
				return open(current);
			}
			current = current.getParent();
		}
		throw new CliException(start + "와 그 위 폴더에서 앱 프로젝트(pom.xml과 src/main/java가 있는 폴더)를 찾지 못했습니다. "
				+ "앱 폴더에서 실행하거나 --project로 알려 주세요.");
	}

	static AppProject open(Path dir) {
		Document pom = Xml.read(dir.resolve("pom.xml"));
		Element project = pom.getDocumentElement();
		String version = null;
		Element parent = child(project, "parent");
		if (parent != null && COZY_GROUP.equals(text(parent, "groupId")) && "cozy-parent".equals(text(parent, "artifactId"))) {
			version = text(parent, "version");
		}
		Set<String> cozy = new LinkedHashSet<>();
		Element dependencies = child(project, "dependencies");
		if (dependencies != null) {
			NodeList list = dependencies.getChildNodes();
			for (int i = 0; i < list.getLength(); i++) {
				if (list.item(i) instanceof Element dependency && COZY_GROUP.equals(text(dependency, "groupId"))) {
					cozy.add(text(dependency, "artifactId"));
				}
			}
		}
		return new AppProject(dir, version, cozy);
	}

	public Path dir() {
		return this.dir;
	}

	/** cozy-parent의 버전. 상속하지 않으면 {@code null} */
	public String cozyVersion() {
		return this.cozyVersion;
	}

	public boolean uses(String cozyArtifact) {
		return this.cozyDependencies.contains(cozyArtifact);
	}

	public JavaSources sources() {
		return this.sources;
	}

	/** 읽지 못한 것. 추측하지 않고 오류로 알린다 */
	public List<Violation> problems() {
		return List.copyOf(this.problems);
	}

	public Optional<JavaSources.SourceType> mainClass() {
		return Optional.ofNullable(this.mainClass);
	}

	public String basePackage() {
		return (this.mainClass != null) ? this.mainClass.pkg() : "";
	}

	public Map<String, Module> modules() {
		return this.modules;
	}

	public List<ModulePackage> modulePackages() {
		return this.modules.values().stream().map(Module::modulePackage).toList();
	}

	public ModuleModel moduleModel() {
		Map<String, ModuleModel.Module> map = new LinkedHashMap<>();
		this.modules.values()
			.forEach((m) -> map.put(m.name(), new ModuleModel.Module(m.name(), m.allowedDependencies(), m.namedInterfaces())));
		return new ModuleModel(map);
	}

	/** 패키지가 속한 모듈 */
	public Optional<Module> moduleOf(String pkg) {
		Module found = null;
		for (Module module : this.modules.values()) {
			if ((pkg.equals(module.basePackage()) || pkg.startsWith(module.basePackage() + "."))
					&& (found == null || module.basePackage().length() > found.basePackage().length())) {
				found = module;
			}
		}
		return Optional.ofNullable(found);
	}

	// ── 읽기 ──────────────────────────────────────────────────

	private void findMainClass() {
		List<JavaSources.SourceType> found = this.sources.types()
			.values()
			.stream()
			.filter((t) -> t.tree()
				.getModifiers()
				.getAnnotations()
				.stream()
				.anyMatch((a) -> this.sources.refersTo(t.unit(), a, Set.of(JavaSources.SPRING_BOOT_APPLICATION))))
			.toList();
		if (found.size() == 1) {
			this.mainClass = found.getFirst();
		}
		else if (found.isEmpty()) {
			this.problems.add(new Violation("src/main/java", 0, "CLI", Severity.ERROR, null,
					"@SpringBootApplication 클래스를 찾지 못했습니다. 모듈은 메인 클래스의 패키지 바로 아래 패키지입니다."));
		}
		else {
			this.problems.add(new Violation("src/main/java", 0, "CLI", Severity.ERROR, null, "@SpringBootApplication 클래스가 "
					+ found.size() + "개입니다(" + String.join(", ", found.stream().map(JavaSources.SourceType::name).toList())
					+ "). 하나만 둡니다."));
		}
	}

	private void findModules() {
		String base = basePackage();
		Set<String> segments = new LinkedHashSet<>();
		for (String pkg : this.sources.packages()) {
			if (pkg.startsWith(base + ".")) {
				segments.add(pkg.substring(base.length() + 1).split("\\.")[0]);
			}
		}
		for (String segment : segments) {
			String pkg = base + "." + segment;
			String name = segment;
			List<String> allowed = null;
			JavaSources.PackageInfo info = this.sources.packageInfos().get(pkg);
			if (info != null) {
				Optional<AnnotationTree> annotation = this.sources.annotation(info.unit(), info.annotations(),
						JavaSources.APPLICATION_MODULE);
				if (annotation.isPresent()) {
					Optional<List<String>> id = JavaSources.stringValues(annotation.get(), "id");
					if (id.isPresent() && !id.get().isEmpty() && !id.get().getFirst().isBlank()) {
						name = id.get().getFirst();
					}
					if (JavaSources.hasAttribute(annotation.get(), "allowedDependencies")) {
						Optional<List<String>> values = JavaSources.stringValues(annotation.get(), "allowedDependencies");
						if (values.isPresent()) {
							allowed = values.get();
						}
						else {
							allowed = List.of();
							this.problems.add(new Violation(info.unit().relative(), info.unit().line(annotation.get()), "CLI",
									Severity.ERROR, null, "allowedDependencies를 읽을 수 없습니다. 글자 그대로의 문자열로 적으세요 "
											+ "(예: allowedDependencies = { \"memo :: spi\" }). 상수나 식은 cozy 명령이 판단하지 못합니다."));
						}
					}
				}
			}
			Set<String> named = new LinkedHashSet<>();
			Set<String> namedPackages = new LinkedHashSet<>();
			for (JavaSources.PackageInfo p : this.sources.packageInfos().values()) {
				if (!p.pkg().equals(pkg) && !p.pkg().startsWith(pkg + ".")) {
					continue;
				}
				Optional<AnnotationTree> ni = this.sources.annotation(p.unit(), p.annotations(), JavaSources.NAMED_INTERFACE);
				if (ni.isPresent()) {
					namedPackages.add(p.pkg());
					List<String> values = new ArrayList<>(JavaSources.stringValues(ni.get(), "value").orElse(List.of()));
					values.addAll(JavaSources.stringValues(ni.get(), "name").orElse(List.of()));
					if (values.isEmpty()) {
						values.add(p.pkg().substring(p.pkg().lastIndexOf('.') + 1));
					}
					named.addAll(values);
				}
			}
			this.modules.put(name, new Module(name, pkg, allowed, named, namedPackages));
		}
	}

	// ── XML ───────────────────────────────────────────────────

	static Element child(Element parent, String tag) {
		NodeList list = parent.getChildNodes();
		for (int i = 0; i < list.getLength(); i++) {
			Node node = list.item(i);
			if (node instanceof Element e && tag.equals(e.getTagName())) {
				return e;
			}
		}
		return null;
	}

	static String text(Element parent, String tag) {
		Element e = child(parent, tag);
		return (e != null) ? e.getTextContent().trim() : null;
	}

	static final class Xml {

		private Xml() {
		}

		static Document read(Path file) {
			try {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				factory.setNamespaceAware(false);
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				return factory.newDocumentBuilder().parse(file.toFile());
			}
			catch (Exception ex) {
				throw new CliException(file + "를 읽지 못했습니다: " + ex.getMessage());
			}
		}
	}
}
