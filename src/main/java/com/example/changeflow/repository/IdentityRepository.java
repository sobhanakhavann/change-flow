package com.example.changeflow.repository;

import com.example.changeflow.domain.ActorPermissions;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Uncached identity reads. Does not consult Security authorities or manage business transactions. */
@Repository
public class IdentityRepository {
    private final DataSource dataSource;

    public IdentityRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * Standalone autocommit lookup: closes its statement/result/connection before returning.
     * The future authentication adapter must finish this call before entering any service operation.
     */
    public Optional<AuthenticationIdentity> findForAuthentication(String username) {
        Objects.requireNonNull(username, "username");
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalStateException("Authentication lookup must precede the operation transaction");
        }
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getAutoCommit()) {
                throw new IllegalStateException("Authentication requires an independent autocommit connection");
            }
            try (var statement = connection.prepareStatement("""
                    SELECT id, username, password_hash, enabled
                    FROM app_user WHERE username = ?
                    """)) {
                statement.setString(1, username);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    var identity = new AuthenticationIdentity(UUID.fromString(rows.getString("id")),
                            rows.getString("username"), rows.getString("password_hash"), rows.getInt("enabled") == 1);
                    // Fully consume the result before the autocommit connection is closed.
                    if (rows.next()) {
                        throw new SQLException("Nonunique identity");
                    }
                    return Optional.of(identity);
                }
            }
        } catch (SQLException failure) {
            throw safeReadFailure();
        }
    }

    /**
     * MUST be the first SELECT in each fresh business or diagnostic transaction, before resource reads.
     * This repository cannot detect earlier SQL issued elsewhere on the connection: the T020 service
     * owns ordering; T014/T034 and later integration tests must prove actual SQLite snapshot behavior.
     * Always rereads enabled/roles together, never credentials or prior authentication authorities.
     */
    public Optional<ActorPermissions> loadCurrentPermissions(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Current permissions require a fresh operation transaction");
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (!DataSourceUtils.isConnectionTransactional(connection, dataSource) || connection.getAutoCommit()) {
                throw new IllegalStateException("Current permissions require the operation-bound connection");
            }
            try (var statement = connection.prepareStatement("""
                    SELECT u.id, u.enabled, r.role
                    FROM app_user u LEFT JOIN user_role r ON r.user_id = u.id
                    WHERE u.id = ?
                    """)) {
                statement.setString(1, userId.toString());
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    boolean enabled = rows.getInt("enabled") == 1;
                    var roles = EnumSet.noneOf(ActorPermissions.Role.class);
                    do {
                        String role = rows.getString("role");
                        if (role != null) {
                            roles.add(ActorPermissions.Role.valueOf(role));
                        }
                    } while (rows.next());
                    return Optional.of(new ActorPermissions(userId, enabled, roles));
                }
            }
        } catch (SQLException failure) {
            throw safeReadFailure();
        } finally {
            // Spring retains the transaction-bound connection until the owning transaction ends.
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static DataAccessResourceFailureException safeReadFailure() {
        // Driver messages/causes may contain sensitive input; do not propagate them to reports.
        return new DataAccessResourceFailureException("Identity storage read failed");
    }

    /** Internal authentication material, never a resource response or loggable record. */
    public static final class AuthenticationIdentity {
        private final UUID userId;
        private final String username;
        private final String passwordHash;
        private final boolean enabled;

        private AuthenticationIdentity(UUID userId, String username, String passwordHash, boolean enabled) {
            this.userId = userId;
            this.username = username;
            this.passwordHash = passwordHash;
            this.enabled = enabled;
        }

        public UUID userId() { return userId; }
        public String username() { return username; }
        public String passwordHash() { return passwordHash; }
        public boolean enabled() { return enabled; }

        @Override
        public String toString() { return "AuthenticationIdentity[redacted]"; }
    }
}
