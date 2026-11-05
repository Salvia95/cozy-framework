package io.salvia95.cozy.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * PRD §5: cozy-test는 cozy-rules를 쓰고, 다른 스타터는 optional로만 참조한다.
 * enforcer는 optional 여부를 볼 수 없어 이 테스트가 POM을 직접 읽어 검사한다.
 */
class PomBoundaryTest {

	private static final String COZY_GROUP = "io.salvia95.cozy";

	@Test
	void cozyModulesOtherThanRulesAreOptional() throws Exception {
		var factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(false);
		var pom = factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());

		var violations = new ArrayList<String>();
		var dependencies = pom.getElementsByTagName("dependency");
		for (int i = 0; i < dependencies.getLength(); i++) {
			var dependency = (Element) dependencies.item(i);
			if (!isProjectDependency(dependency)) {
				continue;
			}
			var groupId = text(dependency, "groupId");
			var artifactId = text(dependency, "artifactId");
			if (COZY_GROUP.equals(groupId) && !"cozy-rules".equals(artifactId)
					&& !"true".equals(text(dependency, "optional"))) {
				violations.add(artifactId);
			}
		}

		assertThat(violations)
			.as("cozy-test는 cozy-rules 외의 cozy 모듈을 <optional>true</optional>로만 참조해야 합니다: %s", violations)
			.isEmpty();
	}

	/** project/dependencies/dependency만 본다. dependencyManagement나 plugin 아래 것은 제외한다. */
	private static boolean isProjectDependency(Element dependency) {
		Node parent = dependency.getParentNode();
		return parent != null && "dependencies".equals(parent.getNodeName())
				&& parent.getParentNode() != null && "project".equals(parent.getParentNode().getNodeName());
	}

	private static String text(Element element, String tag) {
		List<String> values = new ArrayList<>();
		var children = element.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (tag.equals(children.item(i).getNodeName())) {
				values.add(children.item(i).getTextContent().trim());
			}
		}
		return values.isEmpty() ? null : values.get(0);
	}
}
