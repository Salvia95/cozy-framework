-- 최근 활동 (activity 모듈). event_id는 활동을 만든 이벤트 (인박스와 함께 중복을 막는 두 번째 장치)
CREATE TABLE activity (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id   TEXT    NOT NULL UNIQUE,
    message    TEXT    NOT NULL,
    created_at TEXT    NOT NULL
);

CREATE INDEX activity_created_at ON activity (created_at);
