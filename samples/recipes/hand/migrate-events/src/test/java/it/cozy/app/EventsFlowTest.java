package it.cozy.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import it.cozy.app.book.spi.BookFacade;
import it.cozy.app.book.spi.BookFinder;
import it.cozy.app.rental.spi.RentalFacade;
import it.cozy.app.rental.spi.RentalFinder;

/** 두 모듈이 서로의 이벤트를 받는다: 도서를 들이면 대여가 알고, 빌려 가면 도서가 표시한다 */
@SpringBootTest(properties = { "cozy.data-dir=target/cozy-test/events-flow", "cozy.runtime.open-browser=false" })
class EventsFlowTest {

	@Autowired
	BookFacade books;

	@Autowired
	BookFinder bookFinder;

	@Autowired
	RentalFacade rentals;

	@Autowired
	RentalFinder rentalFinder;

	@Test
	void eventsFlowBothWays() throws InterruptedException {
		long bookId = this.books.add("자바 입문");
		assertThat(eventually(() -> this.rentalFinder.knowsBook(bookId))).as("rental이 BookAdded를 받았다").isTrue();

		this.rentals.rent(bookId);
		assertThat(eventually(() -> this.bookFinder.rented(bookId))).as("book이 RentalCreated를 받았다").isTrue();
	}

	/** 리스너는 커밋 뒤 비동기로 돈다 */
	static boolean eventually(BooleanSupplier condition) throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			if (condition.getAsBoolean()) {
				return true;
			}
			Thread.sleep(100);
		}
		return false;
	}
}
