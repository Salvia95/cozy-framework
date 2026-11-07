/**
 * {{label}}
 *
 * <p>모듈 안의 구조: api(화면 진입점) · spi(다른 모듈에 공개하는 계약) · internal(구현). docs/conventions/structure.md
 * 다른 모듈의 spi를 쓰려면 cozy link {{module}} --uses 그 모듈.
 */
@ApplicationModule(allowedDependencies = {})
package {{pkg}};

import org.springframework.modulith.ApplicationModule;
