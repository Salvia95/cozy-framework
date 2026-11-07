package io.salvia95.cozy.rules;

import java.util.Optional;

/**
 * 모듈 안의 패키지 자리 (ADR-0029). 모듈 루트에는 {@code package-info.java}만 두고, 타입은 모두 이 중 한 곳에 둔다. 하위 패키지는 그
 * 자리에 속한다({@code api.dto.form}은 {@link #API_DTO}).
 *
 * <pre>
 * {모듈}/
 * ├── api/                 HTTP 진입점: 컨트롤러. api/dto는 폼·필터·화면 DTO
 * ├── spi/                 모듈 사이 계약 (@NamedInterface("spi")): *Facade(쓰기), *Finder(읽기) 인터페이스
 * │   ├── dto/             모듈 사이 DTO
 * │   └── event/           CozyEvent
 * └── internal/            밀폐된 구현
 *     ├── app/command/     *FacadeImpl, *Service + *ServiceImpl, *Listener
 *     ├── app/query/       *FinderImpl, *Query + *QueryImpl, *Projection + *ProjectionImpl
 *     ├── domain/          엔티티·VO·도메인 규칙 (리포지토리·서비스 없음)
 *     ├── infra/           MyBatis Mapper, 행, 저장소 구현, 외부 연동
 *     └── config/          모듈 전용 설정·빈
 * </pre>
 */
public enum Layer {

	API("api"),

	API_DTO("api.dto"),

	SPI("spi"),

	SPI_DTO("spi.dto"),

	SPI_EVENT("spi.event"),

	APP_COMMAND("internal.app.command"),

	APP_QUERY("internal.app.query"),

	DOMAIN("internal.domain"),

	INFRA("internal.infra"),

	CONFIG("internal.config");

	private final String path;

	Layer(String path) {
		this.path = path;
	}

	/** 모듈 기준 패키지에서의 상대 경로 ({@code internal.app.command}) */
	public String path() {
		return this.path;
	}

	/** 모듈 기준 패키지 아래의 이 자리 ({@code it.cozy.app.memo} → {@code it.cozy.app.memo.internal.app.command}) */
	public String packageIn(String modulePackage) {
		return modulePackage + "." + this.path;
	}

	/**
	 * 패키지가 놓인 자리. 가장 깊게 맞는 자리를 고른다({@code spi.dto}는 {@link #SPI}가 아니라 {@link #SPI_DTO}). 모듈 루트, 모듈
	 * 밖, 정해지지 않은 자리({@code internal}, {@code service})는 비어 있다.
	 */
	public static Optional<Layer> of(String modulePackage, String pkg) {
		Layer found = null;
		for (Layer layer : values()) {
			if (within(pkg, layer.packageIn(modulePackage))
					&& (found == null || layer.path.length() > found.path.length())) {
				found = layer;
			}
		}
		return Optional.ofNullable(found);
	}

	static boolean within(String pkg, String root) {
		return pkg.equals(root) || pkg.startsWith(root + ".");
	}
}
