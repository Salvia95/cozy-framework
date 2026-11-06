/**
 * 홈의 모듈 사이 계약 (Named Interface spi). 시그니처에는 원시 값과 spi.dto 타입만 쓴다. 하위 패키지(dto, event)도 각각
 * 같은 Named Interface를 선언한다.
 */
@NamedInterface("spi")
package ${package}.home.spi;

import org.springframework.modulith.NamedInterface;
