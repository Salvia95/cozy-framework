package io.salvia95.cozy.test;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

/**
 * 앱의 메인 클래스만 읽는다. 테스트 클래스 폴더는 빼고, jar는 모두 넣는다.
 *
 * <p>ArchUnit의 {@code DoNotIncludeTests}는 경로 어디에든 {@code /target/test-classes/}가 있으면 뺀다. 그래서 프로젝트가
 * 다른 빌드의 test-classes 아래에 있으면(아키타입 통합 테스트) 메인 클래스까지 빠진다. 여기서는 경로의 마지막 클래스 폴더로
 * 판단한다.
 */
final class MainClassesOnly implements ImportOption {

	static final MainClassesOnly INSTANCE = new MainClassesOnly();

	@Override
	public boolean includes(Location location) {
		return !isTestLocation(location.asURI().toString());
	}

	static boolean isTestLocation(String location) {
		String path = location.replace('\\', '/');
		if (path.startsWith("jar:") || path.endsWith(".jar")) {
			return false;
		}
		int test = Math.max(path.lastIndexOf("/target/test-classes/"), path.lastIndexOf("/build/classes/java/test/"));
		int main = Math.max(path.lastIndexOf("/target/classes/"), path.lastIndexOf("/build/classes/java/main/"));
		return test > main;
	}
}
