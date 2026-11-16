/** 도서: 들이고, 빌려 간 도서를 표시한다. 대여의 이벤트(rental.spi.event)를 받으려고 대여 모듈에 의존한다 */
@ApplicationModule(allowedDependencies = { "rental :: spi" })
package it.cozy.app.book;

import org.springframework.modulith.ApplicationModule;
