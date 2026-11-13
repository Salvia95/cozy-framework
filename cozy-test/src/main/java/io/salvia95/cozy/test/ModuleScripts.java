package io.salvia95.cozy.test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.SourceFile;

/**
 * C10: 모듈의 Alpine 컴포넌트 파일({@code static/{모듈}/{모듈}.js})이 JavaScript 문법에 맞는가. 레이아웃은 모든 모듈의 파일을 한
 * 묶음으로 싣으므로, 한 파일의 문법 오류는 브라우저가 묶음 전체를 실행하지 않게 해 모든 모듈의 컴포넌트를 멈춘다.
 *
 * <p>Closure Compiler의 파서로 읽기만 한다(최신 문법까지). 타입이나 선언은 보지 않는다.
 */
final class ModuleScripts {

	private ModuleScripts() {
	}

	/** @param staticRoot 앱의 {@code src/main/resources/static} */
	static List<String> problems(Path staticRoot) {
		List<String> problems = new ArrayList<>();
		if (!Files.isDirectory(staticRoot)) {
			return problems;
		}
		try (Stream<Path> modules = Files.list(staticRoot)) {
			for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
				String name = module.getFileName().toString();
				Path file = module.resolve(name + ".js");
				if (name.matches("[a-z][a-z0-9]*") && Files.isRegularFile(file)) {
					String where = "src/main/resources/static/" + name + "/" + name + ".js";
					for (JSError error : parse(where, Files.readString(file, StandardCharsets.UTF_8))) {
						problems.add("[C10] " + where + ":" + error.lineno() + " — " + error.description()
								+ ". 레이아웃이 모든 모듈의 파일을 한 묶음으로 싣으므로 이 오류는 모든 모듈의 컴포넌트를 멈춥니다.");
					}
				}
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return problems;
	}

	private static List<JSError> parse(String name, String code) {
		com.google.javascript.jscomp.Compiler compiler = new com.google.javascript.jscomp.Compiler(
				new PrintStream(OutputStream.nullOutputStream()));
		CompilerOptions options = new CompilerOptions();
		options.setLanguageIn(CompilerOptions.LanguageMode.UNSTABLE);
		options.setStrictModeInput(false);
		compiler.init(List.of(), List.of(SourceFile.fromCode(name, code)), options);
		compiler.parse();
		return List.copyOf(compiler.getErrors());
	}
}
