package it.cozy.app.activity.internal.infra;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActivityMapper {

	void insert(@Param("eventId") String eventId, @Param("message") String message, @Param("createdAt") Instant createdAt);

	List<ActivityRow> findRecent(@Param("limit") int limit);
}
