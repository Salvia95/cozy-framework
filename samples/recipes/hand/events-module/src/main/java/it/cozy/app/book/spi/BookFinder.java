package it.cozy.app.book.spi;

/** 도서 읽기 */
public interface BookFinder {

	boolean exists(long bookId);

	boolean rented(long bookId);
}
