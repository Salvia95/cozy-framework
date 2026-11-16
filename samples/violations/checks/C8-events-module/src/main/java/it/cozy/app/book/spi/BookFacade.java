package it.cozy.app.book.spi;

/** 도서 쓰기 */
public interface BookFacade {

	long add(String title);

	void markRented(long bookId);
}
