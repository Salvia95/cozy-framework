package it.cozy.app.rental.spi;

/** 대여 쓰기 */
public interface RentalFacade {

	long rent(long bookId);

	void noteNewBook(long bookId);
}
