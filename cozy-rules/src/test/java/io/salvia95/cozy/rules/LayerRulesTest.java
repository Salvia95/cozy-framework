package io.salvia95.cozy.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 검사 C8의 판단과 자리 표. 생성기(cozy-cli)와 검사기(cozy-test)가 같은 {@link Role} 표를 쓰므로, 표대로 만든
 * 타입은 C8을 통과해야 한다.
 */
class LayerRulesTest {

	static final String APP = "it.cozy.app";

	static final ModulePackage MEMO = new ModulePackage("memo", APP + ".memo");

	static final ModulePackage WIKI = new ModulePackage("wiki", APP + ".wiki");

	static List<String> check(JavaType... types) {
		return LayerRules.check(List.of(MEMO, WIKI), List.of(types), Set.of(APP + ".memo.spi", APP + ".memo.spi.dto",
				APP + ".memo.spi.event", APP + ".wiki.spi", APP + ".wiki.spi.dto", APP + ".wiki.spi.event"))
			.stream()
			.map(Violation::format)
			.toList();
	}

	@Nested
	class RoleTable {

		@ParameterizedTest
		@EnumSource(Role.class)
		void everyRoleInItsPlacePasses(Role role) {
			List<JavaType> types = new ArrayList<>();
			types.add(Types.of(role, MEMO, "Memo"));
			if (role.contract() != null) {
				types.add(Types.of(role.contract(), MEMO, "Memo"));
			}
			Set<String> spi = Set.of(Layer.SPI.packageIn(MEMO.basePackage()), Layer.SPI_DTO.packageIn(MEMO.basePackage()),
					Layer.SPI_EVENT.packageIn(MEMO.basePackage()));
			assertThat(LayerRules.check(List.of(MEMO), types, spi)).isEmpty();
		}

		@Test
		void implementationsNameTheirContract() {
			assertThat(Role.FACADE_IMPL.contract()).isEqualTo(Role.FACADE);
			assertThat(Role.SERVICE_IMPL.contract()).isEqualTo(Role.SERVICE);
			assertThat(Role.FINDER_IMPL.contract()).isEqualTo(Role.FINDER);
			assertThat(Role.QUERY_IMPL.contract()).isEqualTo(Role.QUERY);
			assertThat(Role.PROJECTION_IMPL.contract()).isEqualTo(Role.PROJECTION);
			assertThat(Role.CONTROLLER.contract()).isNull();
		}

		@Test
		void namesAndPackages() {
			assertThat(Role.FACADE_IMPL.typeName("Memo")).isEqualTo("MemoFacadeImpl");
			assertThat(Role.FACADE_IMPL.qualifiedName(MEMO.basePackage(), "Memo"))
				.isEqualTo("it.cozy.app.memo.internal.app.command.MemoFacadeImpl");
			assertThat(Role.EVENT.typeName("MemoCreated")).isEqualTo("MemoCreated");
			assertThat(Role.EVENT.layer()).isEqualTo(Layer.SPI_EVENT);
		}

		@Test
		void everyLayerHasARole() {
			EnumSet<Layer> used = EnumSet.noneOf(Layer.class);
			for (Role role : Role.values()) {
				used.add(role.layer());
			}
			assertThat(used).containsExactlyInAnyOrder(Layer.values());
		}
	}

	@Nested
	class Layers {

		@Test
		void locatesTheDeepestLayer() {
			assertThat(Layer.of(MEMO.basePackage(), "it.cozy.app.memo.api")).contains(Layer.API);
			assertThat(Layer.of(MEMO.basePackage(), "it.cozy.app.memo.api.dto")).contains(Layer.API_DTO);
			assertThat(Layer.of(MEMO.basePackage(), "it.cozy.app.memo.api.dto.form")).contains(Layer.API_DTO);
			assertThat(Layer.of(MEMO.basePackage(), "it.cozy.app.memo.internal.app.command")).contains(Layer.APP_COMMAND);
		}

		@Test
		void moduleRootAndUnknownFoldersHaveNoLayer() {
			assertThat(Layer.of(MEMO.basePackage(), "it.cozy.app.memo")).isEmpty();
			assertThat(Layer.of(MEMO.basePackage(), "it.cozy.app.memo.internal")).isEmpty();
			assertThat(Layer.of(MEMO.basePackage(), "it.cozy.app.memo.service")).isEmpty();
			assertThat(Layer.of(MEMO.basePackage(), "it.cozy.app.memoir.api")).isEmpty();
		}

		@Test
		void packageOfLayer() {
			assertThat(Layer.APP_QUERY.packageIn(MEMO.basePackage())).isEqualTo("it.cozy.app.memo.internal.app.query");
		}
	}

	/** samples/violations/checks/C8-layers와 같은 위반. 메시지가 바뀌면 그 픽스처도 깨진다 */
	@Nested
	class Violations {

		@Test
		void moduleRootHoldsOnlyPackageInfo() {
			assertThat(check(Types.cls(APP + ".memo.MemoService").build())).singleElement()
				.asString()
				.startsWith("[C8] it.cozy.app.memo.MemoService — 모듈 루트(it.cozy.app.memo)에는 package-info.java만 둡니다");
		}

		@Test
		void spiDoesNotExposeInternalTypes() {
			JavaType finder = Types.iface(APP + ".memo.spi.MemoFinder").dependsOn(Types.cls(APP + ".memo.internal.domain.Memo"))
				.build();
			assertThat(check(finder, Types.cls(APP + ".memo.internal.domain.Memo").build())).singleElement()
				.asString()
				.startsWith("[C8] it.cozy.app.memo.spi.MemoFinder → it.cozy.app.memo.internal.domain.Memo — spi는 internal·api 타입을 쓰지 않습니다");
		}

		@Test
		void spiSubpackagesNeedNamedInterface() {
			List<Violation> result = LayerRules.check(List.of(MEMO), List.of(Types.record(APP + ".memo.spi.dto.MemoItem").build()),
					Set.of(APP + ".memo.spi"));
			assertThat(result).singleElement()
				.extracting(Violation::format)
				.asString()
				.startsWith("[C8] it.cozy.app.memo.spi.dto — spi 패키지마다 package-info.java에 @NamedInterface(\"spi\")를 둡니다");
		}

		@Test
		void apiDoesNotUseInternal() {
			JavaType controller = Types.cls(APP + ".memo.api.MemoController")
				.with(JavaType.Trait.CONTROLLER)
				.dependsOn(Types.cls(APP + ".memo.internal.infra.MemoStore"))
				.build();
			assertThat(check(controller, Types.cls(APP + ".memo.internal.infra.MemoStore").build())).singleElement()
				.asString()
				.startsWith("[C8] it.cozy.app.memo.api.MemoController → it.cozy.app.memo.internal.infra.MemoStore — api는 internal을 쓰지 않습니다");
		}

		@Test
		void appClassesComeInInterfaceAndImplPairs() {
			assertThat(check(Types.cls(APP + ".memo.internal.app.command.MemoWriter").build())).singleElement()
				.asString()
				.startsWith("[C8] it.cozy.app.memo.internal.app.command.MemoWriter — app.command의 클래스는 인터페이스 + Impl 쌍으로 둡니다");
		}

		@Test
		void queryDoesNotDependOnCommand() {
			JavaType writer = Types.iface(APP + ".memo.internal.app.command.MemoService").build();
			JavaType finderImpl = Types.cls(APP + ".memo.internal.app.query.MemoFinderImpl")
				.implementing(Types.iface(APP + ".memo.spi.MemoFinder"))
				.dependsOn(Types.iface(APP + ".memo.internal.app.command.MemoService"))
				.build();
			assertThat(check(writer, finderImpl, Types.iface(APP + ".memo.spi.MemoFinder").build())).singleElement()
				.asString()
				.startsWith("[C8] it.cozy.app.memo.internal.app.query.MemoFinderImpl → it.cozy.app.memo.internal.app.command.MemoService — app.query는 app.command에 의존하지 않습니다");
		}

		@Test
		void domainHasNoRepositories() {
			assertThat(check(Types.iface(APP + ".memo.internal.domain.MemoRepository").build())).singleElement()
				.asString()
				.startsWith("[C8] it.cozy.app.memo.internal.domain.MemoRepository — domain에는 엔티티·VO·도메인 규칙만 둡니다");
		}

		@Test
		void otherModulesDoNotCallFacades() {
			JavaType listener = Types.cls(APP + ".wiki.internal.app.command.MemoCreatedListener")
				.with(JavaType.Trait.LISTENER)
				.dependsOn(Types.iface(APP + ".memo.spi.MemoFacade"))
				.build();
			assertThat(check(listener, Types.iface(APP + ".memo.spi.MemoFacade").build())).singleElement()
				.asString()
				.contains("쓰기 Facade는 컨트롤러(api)와 소유 모듈의 app.command만 부릅니다");
		}

		@Test
		void commandDoesNotUseJdbc() {
			JavaType impl = Types.cls(APP + ".memo.internal.app.command.MemoServiceImpl")
				.implementing(Types.iface(APP + ".memo.internal.app.command.MemoService"))
				.dependsOn(Types.cls("org.springframework.jdbc.core.JdbcTemplate"))
				.build();
			assertThat(check(impl, Types.iface(APP + ".memo.internal.app.command.MemoService").build())).singleElement()
				.asString()
				.contains("app.command는 JDBC·SqlSession을 직접 쓰지 않습니다");
		}

		@Test
		void eventsLiveInSpiEvent() {
			JavaType event = Types.record(APP + ".memo.spi.dto.MemoCreated").with(JavaType.Trait.COZY_EVENT).build();
			assertThat(check(event)).singleElement().asString().contains("CozyEvent는 it.cozy.app.memo.spi.event에 둡니다");
		}

		@Test
		void typesOutsideModulesAreIgnored() {
			assertThat(check(Types.cls(APP + ".App").build(), Types.cls("it.cozy.other.Thing").build())).isEmpty();
		}

		@Test
		void sameMessageIsReportedOnce() {
			JavaType controller = Types.cls(APP + ".memo.api.MemoController")
				.with(JavaType.Trait.CONTROLLER)
				.dependsOn(Types.cls(APP + ".memo.internal.infra.MemoStore"))
				.dependsOn(Types.cls(APP + ".memo.internal.infra.MemoStore"))
				.build();
			assertThat(check(controller, Types.cls(APP + ".memo.internal.infra.MemoStore").build())).hasSize(1);
		}
	}

	@Test
	void nestedTypesAreCheckedThroughTheirTopLevelType() {
		JavaType nested = Types.record(APP + ".memo.api.dto.MemoViews$Row", APP + ".memo.api.dto.MemoViews")
			.dependsOn(Types.cls(APP + ".memo.internal.domain.Memo"))
			.build();
		assertThat(check(nested, Types.cls(APP + ".memo.internal.domain.Memo").build())).singleElement()
			.asString()
			.startsWith("[C8] it.cozy.app.memo.api.dto.MemoViews → it.cozy.app.memo.internal.domain.Memo — api는 internal을 쓰지 않습니다");
	}

	/** 테스트용 JavaType 만들기 */
	static final class Types {

		private final String name;

		private final String topLevelName;

		private final boolean isInterface;

		private final Set<JavaType.Trait> traits = EnumSet.noneOf(JavaType.Trait.class);

		private final List<JavaType.Ref> interfaces = new ArrayList<>();

		private final List<JavaType.Ref> dependencies = new ArrayList<>();

		private Types(String name, String topLevelName, boolean isInterface) {
			this.name = name;
			this.topLevelName = topLevelName;
			this.isInterface = isInterface;
		}

		static Types cls(String name) {
			return new Types(name, name, false);
		}

		static Types record(String name) {
			return new Types(name, name, false);
		}

		static Types record(String name, String topLevelName) {
			return new Types(name, topLevelName, false);
		}

		static Types iface(String name) {
			return new Types(name, name, true);
		}

		/** 자리 표의 역할 하나를 표대로 만든 타입 */
		static JavaType of(Role role, ModulePackage module, String base) {
			Types types = (role.kind() == Role.Kind.INTERFACE) ? iface(role.qualifiedName(module.basePackage(), base))
					: cls(role.qualifiedName(module.basePackage(), base));
			switch (role) {
				case CONTROLLER -> types.with(JavaType.Trait.CONTROLLER, JavaType.Trait.COMPONENT);
				case LISTENER -> types.with(JavaType.Trait.LISTENER, JavaType.Trait.COMPONENT);
				case EVENT -> types.with(JavaType.Trait.COZY_EVENT);
				case CONFIGURATION, FACADE_IMPL, SERVICE_IMPL, FINDER_IMPL, QUERY_IMPL, PROJECTION_IMPL ->
					types.with(JavaType.Trait.COMPONENT);
				default -> {
				}
			}
			if (role.contract() != null) {
				types.implementing(iface(role.contract().qualifiedName(module.basePackage(), base)));
			}
			return types.build();
		}

		Types with(JavaType.Trait... more) {
			this.traits.addAll(List.of(more));
			return this;
		}

		Types implementing(Types type) {
			this.interfaces.add(type.ref());
			return this;
		}

		Types dependsOn(Types type) {
			this.dependencies.add(type.ref());
			return this;
		}

		JavaType.Ref ref() {
			return new JavaType.Ref(this.name, this.topLevelName, this.isInterface);
		}

		JavaType build() {
			return new JavaType(this.name, this.topLevelName, this.isInterface, this.traits, this.interfaces,
					this.dependencies);
		}
	}
}
