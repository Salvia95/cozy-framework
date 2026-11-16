package io.salvia95.cozy.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import io.salvia95.cozy.rules.ModulePackage;

/**
 * C11: 이벤트는 주인 모듈의 internal.app.command에서만 만든다. 주인은 events 모듈이면 하위 폴더 이름, 옛 자리(spi.event)면 그
 * 모듈이다. 받는 쪽은 제한하지 않는다.
 */
class EventOwnerChecksTest {

	static final String BASE = "io.salvia95.cozy.test.ownerfixture";

	static final List<ModulePackage> MODULES = List.of(new ModulePackage("memo", BASE + ".memo"),
			new ModulePackage("wiki", BASE + ".wiki"), new ModulePackage("events", BASE + ".events"),
			new ModulePackage("other", BASE + ".other"));

	@Test
	void onlyTheOwnersCommandSideCreatesAnEvent() {
		JavaClasses classes = new ClassFileImporter().importPackages(BASE);
		assertThat(EventOwnerChecks.problems(MODULES, classes)).containsExactly(
				"[C11] " + BASE + ".memo.api.MemoPage → " + BASE + ".events.spi.memo.MemoCreated — 이벤트는 주인 모듈(memo)의 "
						+ "internal.app.command에서만 만듭니다(Facade 구현, 리스너). 다른 모듈이 알릴 일이면 그 모듈의 이벤트를 새로 만드세요.",
				"[C11] " + BASE + ".memo.internal.app.command.MemoWriter → " + BASE + ".wiki.spi.event.WikiEdited — 이벤트는 주인 "
						+ "모듈(wiki)의 internal.app.command에서만 만듭니다(Facade 구현, 리스너). 다른 모듈이 알릴 일이면 그 모듈의 이벤트를 "
						+ "새로 만드세요.",
				"[C11] " + BASE + ".wiki.internal.app.command.WikiWriter → " + BASE + ".events.spi.memo.MemoCreated — 이벤트는 주인 "
						+ "모듈(memo)의 internal.app.command에서만 만듭니다(Facade 구현, 리스너). 다른 모듈이 알릴 일이면 그 모듈의 이벤트를 "
						+ "새로 만드세요.");
	}

	@Test
	void eventNamesAreUniqueBecauseSampleFoldersUseThem() throws Exception {
		List<Class<?>> events = List.of(Class.forName(BASE + ".events.spi.memo.MemoCreated"),
				Class.forName(BASE + ".other.spi.event.MemoCreated"), Class.forName(BASE + ".wiki.spi.event.WikiEdited"));
		assertThat(EventChecks.nameProblems(events)).containsExactly("[C6] " + BASE
				+ ".other.spi.event.MemoCreated — 이벤트 이름 MemoCreated가 " + BASE + ".events.spi.memo.MemoCreated와 겹칩니다. "
				+ "직렬화 샘플 폴더(src/test/resources/events/MemoCreated)가 단순 이름이므로 앱 안에서 하나여야 합니다.");
	}
}
