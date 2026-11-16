package it.cozy.app.rental.spi;

/** 대여 읽기 */
public interface RentalFinder {

	/** 새로 들인 도서로 알고 있는가 */
	boolean knowsBook(long bookId);
}
