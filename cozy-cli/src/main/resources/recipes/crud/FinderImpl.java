package {{queryPkg}};

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import {{domainPkg}}.{{E}};
import {{infraPkg}}.{{E}}Mapper;
import {{spiPkg}}.{{E}}Finder;
import {{spiDtoPkg}}.{{E}}Item;
import {{spiDtoPkg}}.{{E}}Page;

/** {{L}} 읽기 (cozy add crud). 도메인 모델을 spi DTO로 바꿔서 내보낸다. 읽기 트랜잭션(BEGIN DEFERRED)이다. */
@Service
@Transactional(readOnly = true)
class {{E}}FinderImpl implements {{E}}Finder {

	private final {{E}}Mapper mapper;

	{{E}}FinderImpl({{E}}Mapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public Optional<{{E}}Item> find(long id) {
		return Optional.ofNullable(this.mapper.findById(id)).map({{E}}FinderImpl::item);
	}

	@Override
	public List<{{E}}Item> list() {
		return this.mapper.findAll().stream().map({{E}}FinderImpl::item).toList();
	}

	@Override
	public {{E}}Page search(String query, long offset, int limit) {
		String like = (query == null || query.isBlank()) ? null : "%" + escapeLike(query.trim()) + "%";
		List<{{E}}Item> items = this.mapper.search(like, offset, limit).stream().map({{E}}FinderImpl::item).toList();
		return new {{E}}Page(items, this.mapper.count(like));
	}

	private static {{E}}Item item({{E}} entity) {
		return new {{E}}Item({{itemArgs}});
	}

	/** LIKE의 특수문자(%, _)를 글자 그대로 찾는다 */
	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
