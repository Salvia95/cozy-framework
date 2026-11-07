package {{infraPkg}};

{{mapperImports}}import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import {{domainPkg}}.{{E}};

/** {{L}} SQL (cozy add crud). 쿼리는 resources/mapper/{{module}}/{{E}}Mapper.xml에 있다. */
@Mapper
public interface {{E}}Mapper {

	int insert({{E}}Row row);

	{{E}} findById(long id);

	List<{{E}}> findAll();

	List<{{E}}> search(@Param("like") String like, @Param("offset") long offset, @Param("limit") int limit);

	long count(@Param("like") String like);

	int update(@Param("id") long id, {{mapperParams}}, @Param("updatedAt") Instant updatedAt);

	int delete(long id);
}
