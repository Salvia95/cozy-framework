/** 도서: 대여의 이벤트(rental.spi.event)를 받으려면 rental에 의존해야 한다 */
@ApplicationModule(allowedDependencies = "rental :: spi")
package it.cozy.app.book;

import org.springframework.modulith.ApplicationModule;
