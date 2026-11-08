package io.salvia95.cozy.cli.internal.recipe;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import io.salvia95.cozy.cli.internal.CliException;

/**
 * 이미 있는 Mapper XML과 pom에 덧붙이는 편집기 (ADR-0033 §2). JDK의 XML 파서로 형식을 확인하고, 알아보는 형식이 아니면 추측하지 않고 실패한다.
 * 외부 DTD(MyBatis의 mybatis-3-mapper.dtd)는 읽지 않는다. 고친 결과는 다시 읽어 본다.
 */
final class XmlEdits {

	static final String COZY_GROUP = "io.salvia95.cozy";

	private XmlEdits() {
	}

	// ── Mapper XML ────────────────────────────────────────────

	/** Mapper XML 문 하나: {@code <select id="recent">} */
	record Statement(String kind, String id) {
	}

	/**
	 * Mapper XML의 문 목록. 루트가 {@code mapper}이고 {@code namespace}가 그 Mapper 인터페이스의 이름이어야 한다.
	 *
	 * @param namespace Mapper 인터페이스의 완전한 이름
	 */
	static List<Statement> statements(String file, String source, String namespace) {
		Element root = mapperRoot(file, source, namespace);
		List<Statement> statements = new ArrayList<>();
		for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element) {
				statements.add(new Statement(element.getTagName(), element.getAttribute("id")));
			}
		}
		return statements;
	}

	/**
	 * {@code </mapper>} 바로 앞에 문을 더한다.
	 *
	 * @param statement 들여쓴 문 ({@code <select id="…">…</select>}). 끝에 줄바꿈을 두지 않는다
	 */
	static String addStatement(String file, String source, String namespace, String statement) {
		mapperRoot(file, source, namespace);
		int close = source.lastIndexOf("</mapper>");
		if (close < 0 || !source.substring(close + "</mapper>".length()).isBlank()) {
			throw new CliException(file + "가 </mapper>로 끝나지 않습니다. 이 파일은 손으로 고치세요.");
		}
		String result = source.substring(0, close).stripTrailing() + "\n\n" + statement.stripTrailing() + "\n" + source.substring(close);
		mapperRoot(file, result, namespace);
		return result;
	}

	private static Element mapperRoot(String file, String source, String namespace) {
		Element root = read(file, source).getDocumentElement();
		if (!root.getTagName().equals("mapper")) {
			throw new CliException(file + "의 루트가 <mapper>가 아닙니다. MyBatis Mapper XML만 고칩니다.");
		}
		if (!root.getAttribute("namespace").equals(namespace)) {
			throw new CliException(file + "의 namespace(" + root.getAttribute("namespace") + ")가 " + namespace
					+ "가 아닙니다. namespace는 Mapper 인터페이스의 완전한 이름이어야 합니다.");
		}
		return root;
	}

	// ── pom ───────────────────────────────────────────────────

	/** {@code <dependency>…</dependency>} 하나 (주석 안의 것도 찾는다. 그러면 DOM과 수가 달라져 실패한다) */
	private static final Pattern DEPENDENCY = Pattern.compile("<dependency>(?:(?!</dependency>).)*?</dependency>", Pattern.DOTALL);

	/**
	 * {@code cozy-starter-sqlite} 의존 블록 바로 뒤에 {@code cozy-starter-events}를 더한다. 이미 있으면 그대로 돌려준다.
	 */
	static String addEventsStarter(String file, String source) {
		Document pom = read(file, source);
		if (cozyDependencies(pom, "cozy-starter-events") > 0) {
			return source;
		}
		int sqlite = cozyDependencies(pom, "cozy-starter-sqlite");
		if (sqlite == 0) {
			throw new CliException("이벤트에는 저장(persistence)이 필요합니다. " + file + "의 <dependencies>에 cozy-starter-sqlite가 없습니다. "
					+ "cozy-starter-sqlite를 먼저 더하세요(아키타입 persistence=true와 같은 의존).");
		}
		List<int[]> blocks = new ArrayList<>();
		Matcher m = DEPENDENCY.matcher(source);
		while (m.find()) {
			if (m.group().matches("(?s).*<artifactId>\\s*cozy-starter-sqlite\\s*</artifactId>.*")) {
				blocks.add(new int[] { m.start(), m.end() });
			}
		}
		if (sqlite != 1 || blocks.size() != 1) {
			throw new CliException(file + "에서 cozy-starter-sqlite 의존 블록을 하나로 찾지 못했습니다. <dependency>를 손으로 더하세요: "
					+ COZY_GROUP + ":cozy-starter-events");
		}
		int start = blocks.getFirst()[0];
		int end = blocks.getFirst()[1];
		String indent = source.substring(source.lastIndexOf('\n', start - 1) + 1, start);
		if (!indent.isBlank()) {
			indent = "";
		}
		String text = source.substring(start, end);
		String added;
		if (text.contains("\n")) {
			Matcher child = Pattern.compile("\n([ \t]*)<").matcher(text);
			String inner = child.find() ? child.group(1) : indent + "  ";
			added = "\n" + indent + "<!-- 모듈 사이 이벤트 (cozy enable events) -->\n" + indent + "<dependency>\n" + inner + "<groupId>"
					+ COZY_GROUP + "</groupId>\n" + inner + "<artifactId>cozy-starter-events</artifactId>\n" + indent + "</dependency>";
		}
		else {
			added = "\n" + indent + "<dependency><groupId>" + COZY_GROUP + "</groupId><artifactId>cozy-starter-events</artifactId></dependency>";
		}
		String result = source.substring(0, end) + added + source.substring(end);
		if (cozyDependencies(read(file, result), "cozy-starter-events") != 1) {
			throw new CliException(file + "에 cozy-starter-events를 더하지 못했습니다. <dependency>를 손으로 더하세요.");
		}
		return result;
	}

	/** {@code project/dependencies}의 cozy 의존 수 */
	private static int cozyDependencies(Document pom, String artifactId) {
		Element root = pom.getDocumentElement();
		int count = 0;
		for (Element dependencies : children(root, "dependencies")) {
			for (Element dependency : children(dependencies, "dependency")) {
				if (text(dependency, "groupId").equals(COZY_GROUP) && text(dependency, "artifactId").equals(artifactId)) {
					count++;
				}
			}
		}
		return count;
	}

	private static List<Element> children(Element parent, String name) {
		List<Element> result = new ArrayList<>();
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element && element.getTagName().equals(name)) {
				result.add(element);
			}
		}
		return result;
	}

	private static String text(Element parent, String name) {
		List<Element> found = children(parent, name);
		return found.isEmpty() ? "" : found.getFirst().getTextContent().strip();
	}

	// ── 공통 ──────────────────────────────────────────────────

	/** XML을 읽는다. 외부 DTD와 외부 엔티티는 읽지 않는다(폐쇄망, 테스트에서 외부 URL을 부르지 않는다) */
	static Document read(String file, String source) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setExpandEntityReferences(false);
			factory.setNamespaceAware(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
				@Override
				public void error(org.xml.sax.SAXParseException ex) throws SAXException {
					throw ex;
				}
			});
			builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
			return builder.parse(new InputSource(new StringReader(source)));
		}
		catch (SAXException ex) {
			throw new CliException(file + "를 읽을 수 없습니다(XML 오류: " + ex.getMessage() + "). XML 오류를 먼저 고치세요.");
		}
		catch (ParserConfigurationException | IOException ex) {
			throw new CliException(file + "를 읽을 수 없습니다: " + ex.getMessage());
		}
	}
}
