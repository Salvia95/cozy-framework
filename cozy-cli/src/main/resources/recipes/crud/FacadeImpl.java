package {{commandPkg}};

import java.time.Clock;
{{instantImports}}{{publisherImport}}import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import {{infraPkg}}.{{E}}Mapper;
import {{infraPkg}}.{{E}}Row;
import {{spiPkg}}.{{E}}Facade;
{{eventImports}}
/**
 * {{L}} 쓰기 (cozy add crud). 트랜잭션 경계가 여기다(쓰기 트랜잭션은 BEGIN IMMEDIATE, docs/conventions/sqlite.md).{{eventDoc}}{{eventsNote}}
 */
@Service
@Transactional
class {{E}}FacadeImpl implements {{E}}Facade {

	private final {{E}}Mapper mapper;
{{eventsField}}
	private final Clock clock = Clock.systemUTC();

	{{E}}FacadeImpl({{E}}Mapper mapper{{eventsParam}}) {
		this.mapper = mapper;{{eventsAssign}}
	}

	@Override
	public long create({{params}}) {
		Instant now = Instant.now(this.clock);
		{{E}}Row row = new {{E}}Row();
{{rowSets}}		row.setCreatedAt(now);
		row.setUpdatedAt(now);
		this.mapper.insert(row);
{{publishCreated}}		return row.getId();
	}

	@Override
	public boolean update(long id, {{params}}) {
		boolean updated = this.mapper.update(id, {{updateArgs}}, Instant.now(this.clock)) == 1;
{{publishUpdated}}		return updated;
	}

	@Override
	public boolean delete(long id) {
		boolean deleted = this.mapper.delete(id) == 1;
{{publishDeleted}}		return deleted;
	}
{{nullToEmpty}}}
