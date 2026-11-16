/** 대여: 도서를 읽어(book :: spi) 빌려 주고, 새 도서 이벤트를 받는다 */
@ApplicationModule(allowedDependencies = { "events :: spi", "book :: spi" })
package it.cozy.app.rental;

import org.springframework.modulith.ApplicationModule;
