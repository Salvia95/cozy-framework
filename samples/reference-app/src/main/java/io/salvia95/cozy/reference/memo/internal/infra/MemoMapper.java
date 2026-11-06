package io.salvia95.cozy.reference.memo.internal.infra;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import io.salvia95.cozy.reference.memo.internal.domain.Memo;

/** SQL은 resources/mapper/memo/MemoMapper.xml에 있다. */
@Mapper
public interface MemoMapper {

	int insert(MemoRow row);

	Memo findById(long id);

	List<Memo> findAll();

	List<Memo> search(@Param("like") String like, @Param("pinnedOnly") boolean pinnedOnly, @Param("offset") long offset,
			@Param("limit") int limit);

	long count(@Param("like") String like, @Param("pinnedOnly") boolean pinnedOnly);

	int update(@Param("id") long id, @Param("title") String title, @Param("body") String body,
			@Param("pinned") boolean pinned, @Param("updatedAt") Instant updatedAt);

	int delete(long id);
}
