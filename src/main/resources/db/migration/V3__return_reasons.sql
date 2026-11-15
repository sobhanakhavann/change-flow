-- Nonblank Unicode validation and non-owner review authorization belong to the domain.
CREATE TABLE return_reason (
    request_id TEXT NOT NULL REFERENCES change_request(id) ON DELETE RESTRICT,
    request_version INTEGER NOT NULL CHECK (request_version > 0),
    reviewer_id TEXT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    reason TEXT NOT NULL CHECK (length(reason) <= 2000),
    PRIMARY KEY (request_id, request_version)
) STRICT;
