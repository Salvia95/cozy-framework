package {{base}};

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

{{testImports}}import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import {{spiPkg}}.{{E}}Facade;
import {{spiPkg}}.{{E}}Finder;
import {{spiDtoPkg}}.{{E}}Item;

/** {{L}} 만들기·읽기·수정·삭제가 SQLite + MyBatis로 동작한다 (cozy add crud). */
@SpringBootTest(properties = "cozy.data-dir=target/test-data/{{module}}-crud-${random.uuid}")
class {{E}}CrudTest {

	@Autowired
	{{E}}Facade facade;

	@Autowired
	{{E}}Finder finder;

	@Test
	void createReadUpdateDelete() {
		long id = this.facade.create({{createSample}});
		{{E}}Item created = this.finder.find(id).orElseThrow();
		assertThat(created.{{name}}()).isEqualTo({{nameSample}});
		assertThat(created.createdAt()).isEqualTo(created.updatedAt());

		assertThat(this.facade.update(id, {{createRenamed}})).isTrue();
		assertThat(this.finder.find(id).orElseThrow().{{name}}()).isEqualTo("바꾼 {{L}}");
		assertThat(this.finder.search("바꾼", 0, 10).total()).isEqualTo(1);
		assertThat(this.finder.search("없는 단어", 0, 10).items()).isEmpty();
		assertThat(this.finder.list()).extracting({{E}}Item::id).contains(id);

		assertThat(this.facade.delete(id)).isTrue();
		assertThat(this.finder.find(id)).isEmpty();
		assertThat(this.facade.delete(id)).isFalse();
		assertThat(this.facade.update(id, {{createSample}})).isFalse();
	}

	@Test
	void emptyNameIsRejectedByTheDatabase() {
		assertThatThrownBy(() -> this.facade.create({{createBlankName}})).isInstanceOf(DataIntegrityViolationException.class);
	}
}
