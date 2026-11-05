package io.salvia95.cozy.test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.Resource;

import io.salvia95.cozy.rules.FragmentDeclaration;
import io.salvia95.cozy.ui.catalog.FragmentManifest;

/** 매니페스트를 규칙 입력으로 바꾼다. cozy-ui가 클래스패스에 있을 때만 불린다. */
final class Manifests {

	private Manifests() {
	}

	static List<FragmentDeclaration> read(Path appManifest) {
		List<FragmentDeclaration> declarations = new ArrayList<>();
		for (Resource resource : CozyProject.resources(FragmentManifest.LOCATION)) {
			if (CozyProject.isAppResource(resource)) {
				continue;
			}
			try (InputStream in = resource.getInputStream()) {
				add(declarations, FragmentManifest.parse(in, "[cozy-ui] cozy/fragments.yml"));
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}
		if (Files.isRegularFile(appManifest)) {
			byte[] bytes = CozyProject.read(appManifest).getBytes(StandardCharsets.UTF_8);
			add(declarations, FragmentManifest.parse(new ByteArrayInputStream(bytes), "src/main/resources/cozy/fragments.yml"));
		}
		return declarations;
	}

	private static void add(List<FragmentDeclaration> declarations, List<FragmentManifest.Entry> entries) {
		for (FragmentManifest.Entry entry : entries) {
			declarations.add(new FragmentDeclaration(entry.id(), entry.template(), entry.signature(), entry.source(),
					entry.line()));
		}
	}
}
