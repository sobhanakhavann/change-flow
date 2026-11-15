CREATE TABLE app_user (
    id TEXT NOT NULL PRIMARY KEY,
    username TEXT NOT NULL COLLATE BINARY UNIQUE,
    password_hash TEXT NOT NULL,
    enabled INTEGER NOT NULL CHECK (enabled IN (0, 1))
) STRICT;

CREATE TABLE user_role (
    user_id TEXT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    role TEXT NOT NULL CHECK (role IN ('Requester', 'Reviewer')),
    PRIMARY KEY (user_id, role)
) STRICT;
