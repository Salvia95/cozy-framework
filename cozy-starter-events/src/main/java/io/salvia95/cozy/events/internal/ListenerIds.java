package io.salvia95.cozy.events.internal;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/** 인박스의 리스너 이름: {@code 클래스 FQN#메서드명(파라미터 타입 FQN)} */
public final class ListenerIds {

	private ListenerIds() {
	}

	public static String of(Method method) {
		return method.getDeclaringClass().getName() + "#" + method.getName() + "("
				+ Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(",")) + ")";
	}
}
