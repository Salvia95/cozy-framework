CREATE TABLE sample_item (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,
    created_at TEXT,
    local_dt   TEXT,
    local_d    TEXT,
    uid        TEXT,
    flag       INTEGER,
    maybe      INTEGER
);

CREATE TABLE unique_item (
    code TEXT PRIMARY KEY
);
