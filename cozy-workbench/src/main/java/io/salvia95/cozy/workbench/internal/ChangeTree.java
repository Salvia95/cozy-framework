package io.salvia95.cozy.workbench.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 변경 파일을 모듈과 계층으로 묶는다. 경로로만 판단한다(앱 구조는 {@code docs/conventions/structure.md}).
 *
 * <table>
 * <caption>계층</caption>
 * <tr><th>계층</th><th>경로</th></tr>
 * <tr><td>공개 API</td><td>{@code {모듈}/spi/**}, {@code templates/{모듈}/spi/**}, 공개 프래그먼트 매니페스트</td></tr>
 * <tr><td>내부 구현</td><td>{@code {모듈}/internal/**}의 Java</td></tr>
 * <tr><td>화면</td><td>{@code {모듈}/api/**}, 그 밖의 {@code templates/{모듈}/**}</td></tr>
 * <tr><td>SQL</td><td>{@code mapper/{모듈}/**}, {@code db/migration/V…__{모듈}_….sql}</td></tr>
 * <tr><td>화면 명세</td><td>{@code specs/{모듈}-….yml}</td></tr>
 * <tr><td>테스트</td><td>{@code src/test/**}</td></tr>
 * <tr><td>설정</td><td>모듈의 {@code package-info.java}, 그 밖의 파일({@code pom.xml} 등)</td></tr>
 * </table>
 *
 * 모듈에 딸리지 않은 파일은 "앱 공통"에 둔다.
 */
final class ChangeTree {

	static final List<String> LAYERS = List.of("공개 API", "내부 구현", "화면", "SQL", "화면 명세", "테스트", "설정");

	/** 모듈에 딸리지 않은 파일의 묶음 이름 */
	static final String APP = "앱 공통";

	private static final Pattern MIGRATION = Pattern.compile("src/main/resources/db/migration/V\\d+__([a-z][a-z0-9]*)_.*\\.sql");

	private static final Pattern EVENT_SAMPLE = Pattern.compile("src/test/resources/events/([A-Za-z0-9]+)/.*");

	private ChangeTree() {
	}

	/**
	 * @param name 파일 이름
	 * @param folder 모듈 안의 폴더 (모듈과 계층이 이미 보이므로 앞부분을 뺀 것). 없으면 빈 글자
	 */
	record File(WorkbenchEngine.Change change, String name, String folder) {
	}

	record Layer(String label, List<File> files) {
	}

	/**
	 * 모듈 하나.
	 *
	 * @param module 모듈 이름. 앱 공통이면 빈 글자
	 */
	record Module(String module, String label, List<Layer> layers) {

		/** 구분별 수 ({@code 생성 3 · 수정 1}) */
		public String summary() {
			Map<String, Integer> counts = new LinkedHashMap<>();
			for (Layer layer : this.layers) {
				for (File file : layer.files()) {
					counts.merge(file.change().label(), 1, Integer::sum);
				}
			}
			List<String> parts = new ArrayList<>();
			for (String label : List.of("생성", "수정", "삭제", "변경 없음")) {
				if (counts.containsKey(label)) {
					parts.add(label + " " + counts.get(label));
				}
			}
			return String.join(" · ", parts);
		}
	}

	/**
	 * @param changes 계획의 파일 (순서대로)
	 * @param basePackage 앱의 기본 패키지 ({@code io.salvia95.cozy.reference})
	 * @param eventModules 이벤트 이름 → 모듈. 이벤트 샘플({@code src/test/resources/events/{이벤트}/})을 그 모듈에 둔다
	 * @return 모듈 (처음 나온 순서, 앱 공통은 끝)
	 */
	static List<Module> of(List<WorkbenchEngine.Change> changes, String basePackage, Map<String, String> eventModules) {
		String base = basePackage.replace('.', '/') + "/";
		Map<String, String> events = new LinkedHashMap<>(eventModules);
		Pattern eventFile = Pattern.compile(Pattern.quote("src/main/java/" + base) + "([a-z][a-z0-9]*)/spi/event/([A-Za-z0-9]+)\\.java");
		for (WorkbenchEngine.Change change : changes) {
			Matcher m = eventFile.matcher(change.file());
			if (m.matches()) {
				events.putIfAbsent(m.group(2), m.group(1));
			}
		}
		Map<String, Map<String, List<File>>> grouped = new LinkedHashMap<>();
		for (WorkbenchEngine.Change change : changes) {
			String[] place = place(change.file(), base, events);
			String file = change.file();
			String name = file.substring(file.lastIndexOf('/') + 1);
			grouped.computeIfAbsent(place[0], (k) -> new LinkedHashMap<>())
				.computeIfAbsent(place[1], (k) -> new ArrayList<>())
				.add(new File(change, name, place[2]));
		}
		List<Module> modules = new ArrayList<>();
		List<String> order = new ArrayList<>(grouped.keySet());
		if (order.remove("")) {
			order.add("");
		}
		for (String module : order) {
			Map<String, List<File>> byLayer = grouped.get(module);
			List<Layer> layers = new ArrayList<>();
			for (String layer : LAYERS) {
				if (byLayer.containsKey(layer)) {
					layers.add(new Layer(layer, List.copyOf(byLayer.get(layer))));
				}
			}
			modules.add(new Module(module, module.isEmpty() ? APP : module + " 모듈", List.copyOf(layers)));
		}
		return List.copyOf(modules);
	}

	/** @return 모듈(없으면 빈 글자), 계층, 모듈 안의 폴더 */
	static String[] place(String file, String base, Map<String, String> events) {
		String folder = file.contains("/") ? file.substring(0, file.lastIndexOf('/')) : "";
		String main = "src/main/java/" + base;
		String test = "src/test/java/" + base;
		String resources = "src/main/resources/";
		if (file.startsWith(main)) {
			String rest = file.substring(main.length());
			int slash = rest.indexOf('/');
			if (slash < 0) {
				return new String[] { "", "설정", "" };
			}
			String module = rest.substring(0, slash);
			String inner = rest.substring(slash + 1);
			String innerFolder = inner.contains("/") ? inner.substring(0, inner.lastIndexOf('/')) : "";
			String layer = !inner.contains("/") ? "설정"
					: inner.startsWith("spi/") ? "공개 API" : inner.startsWith("api/") ? "화면" : "내부 구현";
			return new String[] { module, layer, innerFolder };
		}
		if (file.startsWith(test)) {
			String rest = file.substring(test.length());
			int slash = rest.indexOf('/');
			return (slash < 0) ? new String[] { "", "테스트", folder }
					: new String[] { rest.substring(0, slash), "테스트", trimmed(folder, test + rest.substring(0, slash)) };
		}
		Matcher sample = EVENT_SAMPLE.matcher(file);
		if (sample.matches()) {
			return new String[] { events.getOrDefault(sample.group(1), ""), "테스트", trimmed(folder, "src/test/resources") };
		}
		if (file.startsWith("src/test/")) {
			return new String[] { "", "테스트", folder };
		}
		Matcher migration = MIGRATION.matcher(file);
		if (migration.matches()) {
			return new String[] { migration.group(1), "SQL", trimmed(folder, "src/main/resources") };
		}
		String[] segments = file.startsWith(resources) ? file.substring(resources.length()).split("/") : new String[0];
		if (segments.length >= 3 && segments[0].equals("templates")) {
			return new String[] { segments[1], segments[2].equals("spi") ? "공개 API" : "화면", trimmed(folder, "src/main/resources") };
		}
		if (segments.length >= 3 && segments[0].equals("mapper")) {
			return new String[] { segments[1], "SQL", trimmed(folder, "src/main/resources") };
		}
		if (segments.length == 2 && segments[0].equals("specs") && segments[1].matches("[a-z][a-z0-9]*-.+\\.yml")) {
			return new String[] { segments[1].substring(0, segments[1].indexOf('-')), "화면 명세", "specs" };
		}
		if (file.equals("src/main/resources/cozy/fragments.yml")) {
			return new String[] { "", "공개 API", "src/main/resources/cozy" };
		}
		return new String[] { "", "설정", folder };
	}

	private static String trimmed(String folder, String prefix) {
		if (folder.equals(prefix)) {
			return "";
		}
		return folder.startsWith(prefix + "/") ? folder.substring(prefix.length() + 1) : folder;
	}
}
