/**
 * 이벤트 계약 모듈. 이벤트 record만 주인 모듈 이름의 폴더(spi/{주인 모듈}/)에 둔다. 다른 앱 모듈에 의존하지 않으므로, 이벤트를 내는 모듈과
 * 받는 모듈이 서로 의존하지 않는다(docs/conventions/structure.md의 이벤트 계약 모듈).
 */
@ApplicationModule(allowedDependencies = {})
package {{pkg}};

import org.springframework.modulith.ApplicationModule;
