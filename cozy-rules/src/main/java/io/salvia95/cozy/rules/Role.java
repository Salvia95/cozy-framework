package io.salvia95.cozy.rules;

/**
 * 자리 표: 무엇을 어디에 어떤 이름으로 두는지 (ADR-0029, ADR-0032 §4). 생성기(cozy-cli)는 이 표로 파일을 만들고, 검사 C8
 * ({@link LayerRules})은 이 표대로 만든 타입을 통과시킨다. 둘이 어긋나지 않는다는 것은 cozy-rules의 테스트가 역할마다 확인한다.
 *
 * <p>이름은 {@code 바탕 이름 + 접미사}다. 바탕 이름은 엔티티나 개념({@code Memo}), 접미사가 빈 역할은 바탕 이름 그대로다
 * ({@code MemoItem}, {@code MemoCreated}).
 */
public enum Role {

	/** 화면 컨트롤러. {@code @Controller}. {@code api/} 바로 아래에는 컨트롤러만 둔다 */
	CONTROLLER(Layer.API, Kind.CLASS, "Controller", null),

	/** 입력 폼. getter/setter가 있는 클래스 ({@code @Valid} 바인딩) */
	FORM(Layer.API_DTO, Kind.CLASS, "Form", null),

	/** 목록 필터 */
	FILTER(Layer.API_DTO, Kind.CLASS, "Filter", null),

	/** 화면 DTO 묶음. 화면별 record를 안에 둔다 */
	VIEWS(Layer.API_DTO, Kind.CLASS, "Views", null),

	/** 화면 하나의 DTO */
	VIEW(Layer.API_DTO, Kind.RECORD, "View", null),

	/** 쓰기 계약 */
	FACADE(Layer.SPI, Kind.INTERFACE, "Facade", null),

	/** 읽기 계약 */
	FINDER(Layer.SPI, Kind.INTERFACE, "Finder", null),

	/** 모듈 사이 DTO. 스프링 빈이 아니다 */
	SPI_DTO(Layer.SPI_DTO, Kind.RECORD, "", null),

	/** 모듈 사이 이벤트. CozyEvent를 구현한다 */
	EVENT(Layer.SPI_EVENT, Kind.RECORD, "", null),

	/** 쓰기 구현. {@code @Service @Transactional} */
	FACADE_IMPL(Layer.APP_COMMAND, Kind.CLASS, "FacadeImpl", FACADE),

	/** 모듈 안 쓰기 서비스 */
	SERVICE(Layer.APP_COMMAND, Kind.INTERFACE, "Service", null),

	SERVICE_IMPL(Layer.APP_COMMAND, Kind.CLASS, "ServiceImpl", SERVICE),

	/** 이벤트 리스너. 인터페이스 없이 둔다 */
	LISTENER(Layer.APP_COMMAND, Kind.CLASS, "Listener", null),

	/** 읽기 구현. {@code @Transactional(readOnly = true)} */
	FINDER_IMPL(Layer.APP_QUERY, Kind.CLASS, "FinderImpl", FINDER),

	/** 쓰기가 확인용으로 쓰는 단순 조회 */
	QUERY(Layer.APP_QUERY, Kind.INTERFACE, "Query", null),

	QUERY_IMPL(Layer.APP_QUERY, Kind.CLASS, "QueryImpl", QUERY),

	/** 집계 조회 */
	PROJECTION(Layer.APP_QUERY, Kind.INTERFACE, "Projection", null),

	PROJECTION_IMPL(Layer.APP_QUERY, Kind.CLASS, "ProjectionImpl", PROJECTION),

	/** 도메인 모델·규칙 */
	DOMAIN(Layer.DOMAIN, Kind.CLASS, "", null),

	/** MyBatis Mapper. SQL은 {@code resources/mapper/{모듈}/{이름}Mapper.xml} */
	MAPPER(Layer.INFRA, Kind.INTERFACE, "Mapper", null),

	/** 테이블 행 */
	ROW(Layer.INFRA, Kind.CLASS, "Row", null),

	/** 모듈 전용 설정·빈 ({@code @Configuration}) */
	CONFIGURATION(Layer.CONFIG, Kind.CLASS, "Configuration", null);

	/** 만들 타입의 종류 */
	public enum Kind {

		CLASS, INTERFACE, RECORD

	}

	private final Layer layer;

	private final Kind kind;

	private final String suffix;

	private final Role contract;

	Role(Layer layer, Kind kind, String suffix, Role contract) {
		this.layer = layer;
		this.kind = kind;
		this.suffix = suffix;
		this.contract = contract;
	}

	public Layer layer() {
		return this.layer;
	}

	public Kind kind() {
		return this.kind;
	}

	/** 이름 접미사. 빈 문자열이면 바탕 이름 그대로 */
	public String suffix() {
		return this.suffix;
	}

	/** {@code *Impl}이 구현하는 인터페이스의 역할. 구현 역할이 아니면 {@code null} */
	public Role contract() {
		return this.contract;
	}

	/** 바탕 이름으로 지은 타입 이름 ({@code Memo} → {@code MemoFacadeImpl}) */
	public String typeName(String base) {
		return base + this.suffix;
	}

	/** 모듈 기준 패키지 아래의 전체 이름 */
	public String qualifiedName(String modulePackage, String base) {
		return this.layer.packageIn(modulePackage) + "." + typeName(base);
	}
}
