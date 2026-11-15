CREATE TABLE change_request (
    id TEXT NOT NULL COLLATE BINARY PRIMARY KEY,
    requester_id TEXT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    title TEXT NOT NULL DEFAULT '' CHECK (length(title) <= 200),
    change_description TEXT NOT NULL DEFAULT '' CHECK (length(change_description) <= 5000),
    change_reason TEXT NOT NULL DEFAULT '' CHECK (length(change_reason) <= 2000),
    status TEXT NOT NULL CHECK (status IN ('Draft', 'PendingReview', 'ChangesRequested', 'Approved')),
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0)
) STRICT;

CREATE INDEX idx_change_request_requester_id ON change_request(requester_id);
CREATE INDEX idx_change_request_status ON change_request(status);
