-- {{L}} (cozy add crud). 배포한 마이그레이션은 고치지 않고 새 마이그레이션을 더한다 (docs/conventions/sqlite.md)
CREATE TABLE {{table}} (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
{{ddlColumns}}    created_at TEXT    NOT NULL,
    updated_at TEXT    NOT NULL
);

CREATE INDEX {{table}}_by_updated ON {{table}} (updated_at DESC);
