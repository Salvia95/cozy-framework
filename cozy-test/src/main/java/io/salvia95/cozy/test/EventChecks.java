package io.salvia95.cozy.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.util.ClassUtils;

import io.salvia95.cozy.events.CozyEvent;
import io.salvia95.cozy.events.CozyEventMapper;

/**
 * 검사 C6 (PRD §8.2, §8.4). cozy-starter-events가 클래스패스에 있을 때만 불린다.
 *
 * <ul>
 * <li>모든 {@code @ApplicationModuleListener}의 파라미터는 {@link CozyEvent}를 구현한다.</li>
 * <li>모든 CozyEvent 구현에 {@code src/test/resources/events/{클래스명}/v1.json}부터 빠짐없는 버전별 샘플이 있다.</li>
 * <li>모든 샘플이 현재 클래스로 역직렬화되고, 샘플의 필드가 모두 현재 클래스에 같은 값으로 남아 있다(삭제·이름 변경·타입 변경
 * 금지). 각 샘플의 schemaVersion은 파일 버전과 같다.</li>
 * <li>가장 높은 버전의 샘플은 현재 클래스의 모든 필드를 가진다(필드를 추가했으면 새 버전 샘플이 필요하다).</li>
 * <li>샘플 폴더마다 같은 이름의 CozyEvent 구현이 있다(클래스 이름 변경 금지).</li>
 * </ul>
 */
final class EventChecks {

	static final Path SAMPLES = Path.of("src/test/resources/events");

	private static final Pattern SAMPLE = Pattern.compile("v(\\d+)\\.json");

	/** 숫자는 노드 종류(int, long)와 상관없이 값으로 비교한다 */
	private static final java.util.Comparator<JsonNode> NUMERIC_EQUALITY = (a, b) -> {
		if (a.isNumber() && b.isNumber()) {
			return a.decimalValue().compareTo(b.decimalValue());
		}
		return a.equals(b) ? 0 : 1;
	};

	private EventChecks() {
	}

	static boolean available() {
		return ClassUtils.isPresent("io.salvia95.cozy.events.CozyEvent", EventChecks.class.getClassLoader());
	}

	static List<String> listenerProblems(String basePackage) {
		List<String> problems = new ArrayList<>();
		for (Class<?> type : mainClasses(basePackage, null)) {
			for (Method method : type.getDeclaredMethods()) {
				if (!AnnotatedElementUtils.hasAnnotation(method, ApplicationModuleListener.class)) {
					continue;
				}
				Class<?>[] params = method.getParameterTypes();
				if (params.length != 1 || !CozyEvent.class.isAssignableFrom(params[0])) {
					problems.add("[C6] " + type.getName() + "#" + method.getName() + " — @ApplicationModuleListener의 "
							+ "파라미터는 CozyEvent를 구현한 이벤트 하나여야 합니다. 이벤트 record에 implements CozyEvent를 "
							+ "추가하세요 (docs/conventions/events.md).");
				}
			}
		}
		return problems;
	}

	static List<String> sampleProblems(String basePackage, Path samples) {
		List<String> problems = new ArrayList<>();
		Set<String> eventNames = new LinkedHashSet<>();
		for (Class<?> type : mainClasses(basePackage, CozyEvent.class)) {
			if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
				continue;
			}
			eventNames.add(type.getSimpleName());
			checkEvent(type, samples.resolve(type.getSimpleName()), problems);
		}
		if (Files.isDirectory(samples)) {
			try (Stream<Path> dirs = Files.list(samples)) {
				dirs.filter(Files::isDirectory)
					.map((d) -> d.getFileName().toString())
					.filter((name) -> !eventNames.contains(name))
					.forEach((name) -> problems.add("[C6] " + samples.resolve(name) + " — CozyEvent 구현 " + name
							+ "가 없습니다. 이벤트 클래스의 이름과 FQN은 바꾸지 않습니다. 필요하면 새 이벤트를 만드세요."));
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}
		return problems;
	}

	private static void checkEvent(Class<?> type, Path dir, List<String> problems) {
		Map<Integer, Path> versions = new TreeMap<>();
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				files.forEach((file) -> {
					Matcher m = SAMPLE.matcher(file.getFileName().toString());
					if (m.matches()) {
						versions.put(Integer.parseInt(m.group(1)), file);
					}
				});
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}
		String name = type.getSimpleName();
		if (versions.isEmpty()) {
			problems.add("[C6] " + type.getName() + " — 직렬화 샘플이 없습니다. " + dir + "/v1.json에 schemaVersion 1인 이벤트 "
					+ "JSON을 남기세요 (docs/conventions/events.md).");
			return;
		}
		int expected = 1;
		for (int version : versions.keySet()) {
			if (version != expected) {
				problems.add("[C6] " + dir + " — v" + expected + ".json이 없습니다. 버전마다 샘플을 남기고 지우지 않습니다.");
				return;
			}
			expected++;
		}
		JsonMapper mapper = CozyEventMapper.get();
		JsonNode latestCurrent = null;
		JsonNode latestSample = null;
		for (Map.Entry<Integer, Path> entry : versions.entrySet()) {
			String where = "[C6] " + entry.getValue();
			JsonNode sample;
			Object event;
			try {
				sample = mapper.readTree(Files.readString(entry.getValue()));
				event = mapper.treeToValue(sample, type);
			}
			catch (IOException | RuntimeException ex) {
				problems.add(where + " — 현재 " + name + "로 읽을 수 없습니다: " + ex.getMessage()
						+ ". 필드 타입을 바꾸지 말고, 바꿔야 하면 새 이벤트를 만드세요.");
				continue;
			}
			int schemaVersion = ((CozyEvent) event).schemaVersion();
			if (schemaVersion != entry.getKey()) {
				problems.add(where + " — 샘플의 schemaVersion이 " + schemaVersion + "입니다. 파일 이름과 같은 " + entry.getKey()
						+ "이어야 합니다.");
			}
			JsonNode current = mapper.valueToTree(event);
			for (Iterator<String> fields = sample.propertyNames().iterator(); fields.hasNext();) {
				String field = fields.next();
				if (!current.has(field)) {
					problems.add(where + " — 필드 '" + field + "'가 현재 " + name + "에 없습니다. 이벤트 필드는 지우거나 이름을 "
							+ "바꾸지 않습니다 (필드 추가만 허용).");
				}
				else if (!current.get(field).equals(NUMERIC_EQUALITY, sample.get(field))) {
					problems.add(where + " — 필드 '" + field + "'의 값이 " + sample.get(field) + "에서 " + current.get(field)
							+ "로 바뀌어 읽힙니다. 필드 타입을 바꾸지 않습니다.");
				}
			}
			latestCurrent = current;
			latestSample = sample;
		}
		if (latestCurrent != null) {
			int last = versions.size();
			for (Iterator<String> fields = latestCurrent.propertyNames().iterator(); fields.hasNext();) {
				String field = fields.next();
				if (!latestSample.has(field)) {
					problems.add("[C6] " + versions.get(last) + " — 현재 " + name + "의 필드 '" + field + "'가 샘플에 없습니다. "
							+ "필드를 추가했다면 schemaVersion을 " + (last + 1) + "로 올리고 v" + (last + 1) + ".json을 추가하세요.");
				}
			}
		}
	}

	/** 앱 본문(main) 클래스. 테스트 클래스는 제외한다 */
	private static List<Class<?>> mainClasses(String basePackage, Class<?> assignableTo) {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
			@Override
			protected boolean isCandidateComponent(
					org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
				return definition.getMetadata().isIndependent();
			}
		};
		scanner.addIncludeFilter((assignableTo != null) ? new AssignableTypeFilter(assignableTo) : (reader, factory) -> true);
		List<Class<?>> classes = new ArrayList<>();
		for (BeanDefinition definition : scanner.findCandidateComponents(basePackage)) {
			try {
				Class<?> type = ClassUtils.forName(definition.getBeanClassName(), EventChecks.class.getClassLoader());
				if (isMainClass(type)) {
					classes.add(type);
				}
			}
			catch (ClassNotFoundException | LinkageError ex) {
				// 읽을 수 없는 클래스는 다른 검사(C1)가 드러낸다
			}
		}
		return classes;
	}

	private static boolean isMainClass(Class<?> type) {
		CodeSource source = type.getProtectionDomain().getCodeSource();
		URL location = (source != null) ? source.getLocation() : null;
		return location == null || !MainClassesOnly.isTestLocation(location.toString());
	}
}
