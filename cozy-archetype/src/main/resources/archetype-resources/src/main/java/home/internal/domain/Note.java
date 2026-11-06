package ${package}.home.internal;

import java.time.Instant;

/** 메모 한 건. 모듈 밖으로 내보내지 않는다(화면에는 view의 record로 바꿔서 넘긴다). */
public record Note(long id, String title, Instant createdAt) {
}
