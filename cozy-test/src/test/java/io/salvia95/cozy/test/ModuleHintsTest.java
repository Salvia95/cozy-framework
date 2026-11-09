package io.salvia95.cozy.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** C1 메시지에서 고치는 명령 찾기: 다른 모듈의 spi를 쓰는데 선언이 빠진 경우만 cozy link */
class ModuleHintsTest {

	/** Modulith 2.1.2가 낸 그대로 (samples/violations/checks/C1-modulith) */
	static final String MESSAGE = """
			- Module 'a' depends on named interface(s) 'b :: spi' via it.cozy.app.a.internal.app.command.AServiceImpl -> it.cozy.app.b.spi.dto.BItem. Allowed targets: none.
			- Module 'a' depends on module 'b' via it.cozy.app.a.internal.app.command.AServiceImpl -> it.cozy.app.b.internal.infra.BHelper. Allowed targets: none.
			- Module 'a' depends on named interface(s) 'b :: spi' via it.cozy.app.a.api.AController -> it.cozy.app.b.spi.BFinder. Allowed targets: none.
			- Module 'c' depends on named interface(s) 'b :: spi' via it.cozy.app.c.api.CController -> it.cozy.app.b.spi.event.BCreated. Allowed targets: memo :: spi.
			""";

	@Test
	void spiTargetsGetALinkCommandOncePerPair() {
		assertThat(ModuleHints.commands(MESSAGE)).containsExactly("cozy link a --uses b", "cozy link c --uses b");
	}

	@Test
	void internalTargetsAndOtherMessagesGetNothing() {
		assertThat(ModuleHints.commands(MESSAGE.lines().skip(1).findFirst().orElseThrow())).isEmpty();
		assertThat(ModuleHints.commands("Cycle detected: Slice a -> Slice b -> Slice a")).isEmpty();
		assertThat(ModuleHints.commands("Module 'a' depends on named interface(s) 'b :: api' via x.A -> y.B. Allowed targets: none."))
			.isEmpty();
		assertThat(ModuleHints.commands(null)).isEmpty();
	}
}
