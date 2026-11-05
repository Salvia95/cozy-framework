-- 이벤트 발행 기록 (PRD §8.1). Spring Modulith 2.1 공식 JDBC 스키마 v2의 컬럼을 따르고 타입만 SQLite에 맞춘다.
-- UUID와 시각은 TEXT. 시각은 UTC, 소수점 9자리 고정 폭이라 문자열 비교가 시간 순서와 같다.
CREATE TABLE cozy_event_publication (
    id                     TEXT    NOT NULL PRIMARY KEY,
    listener_id            TEXT    NOT NULL,
    event_type             TEXT    NOT NULL,
    serialized_event       TEXT    NOT NULL,
    publication_date       TEXT    NOT NULL,
    completion_date        TEXT,
    status                 TEXT,
    completion_attempts    INTEGER NOT NULL DEFAULT 1,
    last_resubmission_date TEXT
);

CREATE INDEX cozy_event_publication_completion_date ON cozy_event_publication (completion_date);
CREATE INDEX cozy_event_publication_listener_event ON cozy_event_publication (listener_id, serialized_event);

-- 인박스 (PRD §8.3). 같은 이벤트를 같은 리스너가 두 번 처리하지 않게 한다.
CREATE TABLE cozy_inbox (
    event_id     TEXT NOT NULL,
    listener_id  TEXT NOT NULL,
    processed_at TEXT NOT NULL,
    PRIMARY KEY (event_id, listener_id)
);

CREATE INDEX cozy_inbox_processed_at ON cozy_inbox (processed_at);
