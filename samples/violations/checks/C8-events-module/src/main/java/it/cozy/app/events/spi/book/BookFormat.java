package it.cozy.app.events.spi.book;

/** events 모듈에 둔 도움 클래스: 이벤트 계약이 아니다 */
public final class BookFormat {

	private BookFormat() {
	}

	public static String title(String title) {
		return "《" + title + "》";
	}
}
