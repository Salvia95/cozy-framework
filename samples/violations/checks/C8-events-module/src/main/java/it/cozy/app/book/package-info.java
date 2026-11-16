/** 도서: 들이고, 빌려 간 도서를 표시한다. 대여(rental)의 이벤트를 받지만 대여 모듈에는 의존하지 않는다 */
@ApplicationModule(allowedDependencies = "events :: spi")
package it.cozy.app.book;

import org.springframework.modulith.ApplicationModule;
