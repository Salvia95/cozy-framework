package io.salvia95.cozy.workbench.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import io.salvia95.cozy.ui.catalog.ScreenSpec;
import io.salvia95.cozy.ui.catalog.ScreenSpecs;

/**
 * 화면 스펙을 앱 프로젝트의 소스 폴더({@code src/main/resources/specs})에서 읽는다. 물을 때마다 다시 읽으므로, 워크벤치에서 고친 값이 다시 빌드하지
 * 않아도 미리보기에 보인다.
 *
 * <p>
 * 프로젝트 폴더가 없거나 스펙을 읽지 못하면 기동할 때 클래스패스에서 읽은 스펙을 준다. 읽지 못한 이유는 {@link #problem()}에 남는다.
 */
public class SourceScreenSpecs implements ScreenSpecs {

	/** 프로젝트 기준 스펙 폴더 */
	public static final String FOLDER = "src/main/resources/specs";

	private final WorkbenchEngine engine;

	private final List<ScreenSpec> fromClasspath;

	private volatile String problem;

	public SourceScreenSpecs(WorkbenchEngine engine, List<ScreenSpec> fromClasspath) {
		this.engine = engine;
		this.fromClasspath = List.copyOf(fromClasspath);
	}

	@Override
	public List<ScreenSpec> all() {
		Path folder = this.engine.projectDir().map((dir) -> dir.resolve(FOLDER)).orElse(null);
		if (folder == null || !Files.isDirectory(folder)) {
			this.problem = null;
			return this.fromClasspath;
		}
		List<ScreenSpec> specs = new ArrayList<>();
		try (Stream<Path> files = Files.list(folder)) {
			for (Path file : files.filter((f) -> f.getFileName().toString().endsWith(".yml")).sorted().toList()) {
				String name = file.getFileName().toString();
				try (InputStream in = Files.newInputStream(file)) {
					specs.add(ScreenSpec.parse(in, name, "specs/" + name));
				}
			}
		}
		catch (IOException | RuntimeException ex) {
			this.problem = ex.getMessage();
			return this.fromClasspath;
		}
		this.problem = null;
		specs.sort(Comparator.comparing(ScreenSpec::screen));
		return List.copyOf(specs);
	}

	/** 소스 폴더의 스펙을 읽지 못한 이유. 읽었으면 {@code null} */
	public String problem() {
		return this.problem;
	}
}
