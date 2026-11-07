package io.salvia95.cozy.rules;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 검사 C8이 보는 타입 하나. 바이트코드(cozy-test, ArchUnit)나 소스(cozy-cli)에서 읽어 만든다. cozy-rules는 어디서 읽었는지 모른다.
 *
 * @param name 전체 이름. 중첩 타입은 {@code $}로 잇는다 ({@code it.cozy.app.memo.api.dto.MemoViews$Row})
 * @param topLevelName 감싸는 최상위 타입의 전체 이름. 최상위 타입이면 {@code name}과 같다
 * @param isInterface 인터페이스(애너테이션 포함)인가
 * @param traits 판단에 쓰는 성질
 * @param interfaces 직접 구현하는 인터페이스
 * @param dependencies 이 타입이 쓰는 타입 (배열은 원소 타입). 같은 타입이 여러 번 있어도 된다
 */
public record JavaType(String name, String topLevelName, boolean isInterface, Set<Trait> traits, List<Ref> interfaces,
		List<Ref> dependencies) {

	public JavaType {
		traits = traits.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(traits));
		interfaces = List.copyOf(interfaces);
		dependencies = List.copyOf(dependencies);
	}

	/** 판단에 쓰는 성질 */
	public enum Trait {

		/** {@code @Controller}(메타 애너테이션 포함) */
		CONTROLLER,

		/** {@code @Component}(메타 애너테이션 포함). 스프링 빈 */
		COMPONENT,

		/** CozyEvent를 구현한다(상위 인터페이스 포함) */
		COZY_EVENT,

		/** {@code @ApplicationModuleListener}, {@code @EventListener}, {@code @TransactionalEventListener} 메서드가 있다 */
		LISTENER

	}

	/**
	 * 다른 타입을 가리킨다.
	 *
	 * @param name 전체 이름
	 * @param topLevelName 감싸는 최상위 타입의 전체 이름
	 * @param isInterface 인터페이스인가
	 */
	public record Ref(String name, String topLevelName, boolean isInterface) {

		public String packageName() {
			return packageOf(this.name);
		}

		public String simpleName() {
			return simpleNameOf(this.name);
		}
	}

	public String packageName() {
		return packageOf(this.name);
	}

	public String simpleName() {
		return simpleNameOf(this.name);
	}

	public boolean isTopLevel() {
		return this.name.equals(this.topLevelName);
	}

	public boolean has(Trait trait) {
		return this.traits.contains(trait);
	}

	static String packageOf(String name) {
		int dot = name.lastIndexOf('.');
		return (dot < 0) ? "" : name.substring(0, dot);
	}

	static String simpleNameOf(String name) {
		String local = name.substring(name.lastIndexOf('.') + 1);
		return local.substring(local.lastIndexOf('$') + 1);
	}
}
