package com.example.changeflow.repository;

import com.example.changeflow.domain.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChangeRequestRepository {
    private final JdbcTemplate jdbc;
    private static final String SELECT = """
            SELECT q.*, f.reason AS feedback_reason, f.reviewer_id AS feedback_reviewer,
                   f.request_version AS feedback_version
            FROM change_request q LEFT JOIN return_reason f ON f.request_id=q.id
                AND f.request_version=(SELECT max(r.request_version) FROM return_reason r WHERE r.request_id=q.id)
            """;

    public ChangeRequestRepository(DataSource source) { jdbc = new JdbcTemplate(source); }

    public Optional<RequestView> find(UUID id) {
        return jdbc.query(SELECT + " WHERE q.id=?", this::map, id.toString()).stream().findFirst();
    }

    public List<RequestView> visible(ActorPermissions actor, int limit, UUID after) {
        boolean owner = actor.enabled() && actor.roles().contains(ActorPermissions.Role.Requester);
        boolean reviewer = actor.enabled() && actor.roles().contains(ActorPermissions.Role.Reviewer);
        return jdbc.query(SELECT + " WHERE ((?=1 AND q.requester_id=?) OR (?=1 AND q.status IN ('PendingReview','Approved')))"
                        + " AND q.id>? ORDER BY q.id COLLATE BINARY LIMIT ?", this::map,
                owner ? 1 : 0, actor.userId().toString(), reviewer ? 1 : 0, after == null ? "" : after.toString(), limit);
    }

    public void insert(ChangeRequest request) {
        jdbc.update("INSERT INTO change_request(id,requester_id,title,change_description,change_reason,status,version) VALUES (?,?,?,?,?,?,?)",
                request.id().toString(), request.requesterId().toString(), request.title(), request.changeDescription(),
                request.reasonForChange(), request.status().name(), request.version());
    }

    public void update(ChangeRequest before, ChangeRequest after) {
        try {
            int rows = jdbc.update("UPDATE change_request SET title=?,change_description=?,change_reason=?,status=?,version=? WHERE id=? AND version=?",
                    after.title(), after.changeDescription(), after.reasonForChange(), after.status().name(), after.version(),
                    before.id().toString(), before.version());
            if (rows != 1) { throw new RequestFailure(RequestFailure.Kind.VERSION_CONFLICT); }
        } catch (DataAccessException failure) {
            if (isSnapshotFailure(failure)) { throw new SnapshotFailure(); }
            throw failure;
        }
    }

    private static boolean isSnapshotFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.sqlite.SQLiteException sqlite
                    && sqlite.getResultCode() == org.sqlite.SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT) { return true; }
        }
        return false;
    }

    /** No SQL/driver message escapes as business response content. */
    public static final class SnapshotFailure extends RuntimeException { }

    private RequestView map(ResultSet row, int index) throws SQLException {
        var request = new ChangeRequest(UUID.fromString(row.getString("id")), UUID.fromString(row.getString("requester_id")),
                row.getString("title"), row.getString("change_description"), row.getString("change_reason"),
                ChangeRequest.Status.valueOf(row.getString("status")), row.getLong("version"));
        ReturnReason reason = row.getString("feedback_reason") == null ? null : new ReturnReason(request.id(),
                row.getLong("feedback_version"), UUID.fromString(row.getString("feedback_reviewer")), row.getString("feedback_reason"));
        return new RequestView(request, reason);
    }
}
