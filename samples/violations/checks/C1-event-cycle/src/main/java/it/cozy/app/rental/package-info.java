/** 대여: 도서를 읽고(book :: spi) 새 도서 이벤트(book.spi.event)를 받는다 */
@ApplicationModule(allowedDependencies = "book :: spi")
package it.cozy.app.rental;

import org.springframework.modulith.ApplicationModule;
