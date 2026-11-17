package io.salvia95.cozy.rules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 검사 C12: 동작 명세와 만든 JS. 모듈의 화면 안 동작은 동작 명세 {@code behaviors/{모듈}.yml}이 원본이고, cozy 명령이 그 명세에서
 * {@code static/{모듈}/{모듈}.js}를 만든다. 만든 파일의 머리에는 명세와 본문의 SHA-256을 적는다({@value #STAMP}). 둘 중 하나라도 지금
 * 내용과 다르면 오류다. 고치는 명령은 {@code cozy sync behaviors}다.
 *
 * <ul>
 * <li>명세가 있는데 만든 파일이 없다</li>
 * <li>명세를 고친 뒤 다시 만들지 않았다(명세의 해시가 다르다)</li>
 * <li>만든 파일을 손으로 고쳤다(본문의 해시가 다르다)</li>
 * <li>만든 파일만 있고 명세가 없다</li>
 * <li>명세가 있는 모듈의 JS가 손으로 쓴 파일이다(머리가 없다). 둘을 함께 둘 수 없다</li>
 * <li>{@code behaviors/}에 모듈 이름의 {@code .yml}이 아닌 파일이 있다</li>
 * </ul>
 *
 * <p>해시는 BOM을 빼고 줄바꿈을 {@code \n}으로 맞춘 내용으로 잰다. Windows에서 줄바꿈을 바꿔 체크아웃해도 결과가 같다. 명세를 읽거나 JS를 만드는
 * 일은 cozy 명령이 하고, 이 검사는 해시만 비교한다(JDK만 쓴다).
 */
public final class BehaviorRules {

	public static final String RULE = "C12";

	/** 만든 파일의 표시. 뒤에 {@code spec=<해시> body=<해시>}가 온다 */
	public static final String STAMP = "cozy:behaviors";

	public static final String FIX = "cozy sync behaviors";

	/** 명세 폴더 ({@code src/main/resources} 기준) */
	public static final String SPEC_DIR = "behaviors";

	private static final Pattern STAMP_LINE = Pattern
		.compile(Pattern.quote(STAMP) + " spec=([0-9a-f]{64}) body=([0-9a-f]{64}) \\*/\n");

	private static final Pattern MODULE = Pattern.compile("[a-z][a-z0-9]*");

	private static final Pattern REGISTRATION = Pattern.compile("Alpine\\.data\\(");

	private BehaviorRules() {
	}

	/** 명세 파일의 경로 ({@code src/main/resources} 기준) */
	public static String specPath(String module) {
		return SPEC_DIR + "/" + module + ".yml";
	}

	/** 만든 JS의 경로 ({@code src/main/resources} 기준) */
	public static String scriptPath(String module) {
		return "static/" + module + "/" + module + ".js";
	}

	/** BOM을 빼고 줄바꿈을 {@code \n}으로 맞춘다 */
	public static String normalize(String text) {
		String body = text.startsWith("﻿") ? text.substring(1) : text;
		return body.replace("\r\n", "\n").replace('\r', '\n');
	}

	public static String sha256(String text) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(normalize(text).getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * 만든 파일의 표시 줄. 머리 주석의 마지막 줄로 쓰고, 그 뒤가 본문이다.
	 *
	 * @param spec 명세 파일의 내용
	 * @param body 표시 줄 뒤의 내용
	 */
	public static String stampLine(String spec, String body) {
		return STAMP + " spec=" + sha256(spec) + " body=" + sha256(body) + " */\n";
	}

	/** 표시가 있는 파일(cozy가 만든 파일)인가 */
	public static boolean isGenerated(String script) {
		return STAMP_LINE.matcher(normalize(script)).find();
	}

	/**
	 * 앱 하나.
	 *
	 * @param resources 앱의 {@code src/main/resources}
	 * @param displayPrefix 위치 앞에 붙일 경로 ({@code src/main/resources/})
	 * @return 모듈 이름 순서
	 */
	public static List<Violation> check(Path resources, String displayPrefix) {
		List<Violation> violations = new ArrayList<>();
		TreeSet<String> modules = new TreeSet<>();
		Path specs = resources.resolve(SPEC_DIR);
		try {
			if (Files.isDirectory(specs)) {
				try (Stream<Path> files = Files.list(specs)) {
					for (Path file : files.sorted().toList()) {
						String name = file.getFileName().toString();
						String module = name.endsWith(".yml") ? name.substring(0, name.length() - 4) : null;
						if (!Files.isRegularFile(file) || module == null || !MODULE.matcher(module).matches()) {
							violations.add(violation(displayPrefix + SPEC_DIR + "/" + name, name,
									"동작 명세 폴더에는 모듈 이름의 .yml(" + specPath("memo") + ")만 둡니다. 이 파일을 옮기거나 지웁니다", null));
							continue;
						}
						modules.add(module);
					}
				}
			}
			Path statics = resources.resolve("static");
			if (Files.isDirectory(statics)) {
				try (Stream<Path> dirs = Files.list(statics)) {
					for (Path dir : dirs.filter(Files::isDirectory).toList()) {
						String module = dir.getFileName().toString();
						Path script = dir.resolve(module + ".js");
						if (Files.isRegularFile(script) && isGenerated(read(script))) {
							modules.add(module);
						}
					}
				}
			}
			for (String module : modules) {
				check(resources, displayPrefix, module, violations);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("[C12] " + resources + "를 읽지 못했습니다", ex);
		}
		return violations;
	}

	private static void check(Path resources, String prefix, String module, List<Violation> violations) throws IOException {
		Path specFile = resources.resolve(specPath(module));
		Path scriptFile = resources.resolve(scriptPath(module));
		String specLocation = prefix + specPath(module);
		String scriptLocation = prefix + scriptPath(module);
		if (!Files.isRegularFile(specFile)) {
			violations.add(violation(scriptLocation, module, "동작 명세(" + specPath(module) + ")에서 만든 파일인데 명세가 없습니다. "
					+ "명세를 되살리거나, 이 파일을 지우려면 고치는 명령을 실행합니다", FIX));
			return;
		}
		if (!Files.isRegularFile(scriptFile)) {
			violations.add(violation(specLocation, module, "동작 명세에서 만든 파일(" + scriptPath(module) + ")이 없습니다", FIX));
			return;
		}
		String spec = read(specFile);
		String script = normalize(read(scriptFile));
		Matcher stamp = STAMP_LINE.matcher(script);
		if (!stamp.find()) {
			violations.add(violation(scriptLocation, module,
					"동작 명세가 있는 모듈의 JS를 손으로 썼습니다. 명세에서 만든 파일과 손으로 쓴 파일을 함께 둘 수 없습니다. 손으로 쓴 컴포넌트를 "
							+ "cozy add behavior로 명세에 옮긴 뒤 고치는 명령으로 다시 만듭니다",
					null));
			return;
		}
		if (!stamp.group(1).equals(sha256(spec))) {
			violations.add(violation(specLocation, module,
					"동작 명세를 고친 뒤 " + scriptPath(module) + "를 다시 만들지 않았습니다", FIX));
		}
		if (!stamp.group(2).equals(sha256(script.substring(stamp.end())))) {
			violations.add(violation(scriptLocation, module,
					"동작 명세에서 만든 파일을 손으로 고쳤습니다. 동작은 cozy add behavior로 명세를 고쳐 바꿉니다. 고친 내용은 다시 만들면 사라집니다",
					FIX));
			return;
		}
		Matcher registration = REGISTRATION.matcher(script);
		int registered = 0;
		while (registration.find()) {
			registered++;
		}
		if (ComponentContract.parse(script).size() != registered) {
			violations.add(violation(scriptLocation, module,
					"이전 판의 cozy가 만든 파일이라 컴포넌트가 내놓는 이름(" + ComponentContract.MARKER + " 줄)이 없습니다. 템플릿이 부르는 이름을 확인할 수 없습니다",
					FIX));
		}
	}

	private static Violation violation(String location, String reference, String message, String command) {
		return new Violation(location, 0, RULE, Severity.ERROR, reference, message, command);
	}

	private static String read(Path file) throws IOException {
		return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
	}
}
