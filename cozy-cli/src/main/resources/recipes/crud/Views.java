package {{dtoPkg}};

{{viewsTimeImports}}import {{spiDtoPkg}}.{{E}}Item;
import io.salvia95.cozy.ui.CozyPage;

/** {{L}} 화면 DTO (cozy add crud). 템플릿에는 spi DTO를 화면에 맞게 바꾼 이 record들을 넘긴다. */
public final class {{E}}Views {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.KOREA)
		.withZone(ZoneId.systemDefault());

	private {{E}}Views() {
	}

	/**
	 * 목록 한 행
	 *
{{rowDocs}}	 */
	public record Row({{rowComponents}}) {

		public static Row of({{E}}Item item) {
			return new Row({{rowArgs}});
		}
	}

	/**
	 * 목록 결과 (fragment "result")
	 *
	 * @param rows 행
	 * @param page 페이지
	 */
	public record ListResult(List<Row> rows, CozyPage page) {
	}

	/**
	 * 상세 화면
	 *
{{detailDocs}}	 */
	public record Detail({{detailComponents}}) {

		public static Detail of({{E}}Item item) {
			return new Detail({{detailArgs}});
		}
	}

	public static {{E}}Form formOf({{E}}Item item) {
		{{E}}Form form = new {{E}}Form();
{{formOf}}		return form;
	}

	/** 수정 폼이 지금 값과 같은가 (바뀐 게 없으면 저장하지 않고 알린다). 글의 null과 빈 글자는 같다 */
	public static boolean unchanged({{E}}Item item, {{E}}Form form) {
		return {{unchanged}};
	}

	private static String nullToEmpty(String text) {
		return (text != null) ? text : "";
	}

	static String format(Instant instant) {
		return TIME.format(instant);
	}
}
