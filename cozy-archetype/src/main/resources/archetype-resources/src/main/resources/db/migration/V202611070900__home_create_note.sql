-- 홈 메모. 파일 이름: V{yyyyMMddHHmm}__{모듈}_{설명}.sql. 적용된 파일은 고치지 않고 새 파일을 추가한다.
CREATE TABLE home_note (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    title      TEXT NOT NULL CHECK (length(title) > 0),
    created_at TEXT NOT NULL
);

CREATE TABLE home_activity (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    message TEXT NOT NULL,
    at      TEXT NOT NULL
);
