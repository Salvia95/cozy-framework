package ${package}.home.internal.infra;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import ${package}.home.internal.domain.Note;
import ${package}.home.internal.domain.NoteActivity;

/** SQL은 resources/mapper/home/NoteMapper.xml에 있다. */
@Mapper
public interface NoteMapper {

	int insert(NoteRow row);

	List<Note> search(@Param("like") String like, @Param("limit") int limit);

	int insertActivity(@Param("message") String message, @Param("at") Instant at);

	List<NoteActivity> recentActivity(@Param("limit") int limit);
}
