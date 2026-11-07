package {{spiPkg}};

import java.util.List;
import java.util.Optional;

import {{spiDtoPkg}}.{{E}}Item;
import {{spiDtoPkg}}.{{E}}Page;

/** {{L}} 읽기 (cozy add crud). 정렬은 최근에 고친 순서다. 다른 모듈은 이 모듈의 spi 의존을 선언하고 쓴다. */
public interface {{E}}Finder {

	Optional<{{E}}Item> find(long id);

	List<{{E}}Item> list();

	/**
	 * {{searchLabels}}에 {@code query}가 들어간 {{L}} 한 페이지.
	 * @param query 비우면 전체
	 */
	{{E}}Page search(String query, long offset, int limit);
}
