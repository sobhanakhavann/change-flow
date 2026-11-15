package com.example.changeflow.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.example.changeflow.domain.ActorPermissions.Role.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** JDBC collaboration tests only; no SQL engine/schema or transaction behavior is proven here. */
class IdentityRepositoryTests {
    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final ResultSet rows = mock(ResultSet.class);
    private final UUID id = UUID.randomUUID();
    private final IdentityRepository repository = new IdentityRepository(dataSource);

    @BeforeEach
    void jdbcStubs() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.getString("id")).thenReturn(id.toString());
    }

    @AfterEach
    void clearThreadResources() {
        TransactionSynchronizationManager.unbindResourceIfPossible(dataSource);
        TransactionSynchronizationManager.clear();
    }

    @Test
    void credentialsUseBoundParametersAndCloseTheirAutocommitConnection() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("username")).thenReturn("account");
        when(rows.getString("password_hash")).thenReturn("fixture-encoded-secret");
        when(rows.getInt("enabled")).thenReturn(1);
        var identity = repository.findForAuthentication("' OR 1=1 --").orElseThrow();
        assertEquals(id, identity.userId());
        assertEquals("account", identity.username());
        assertTrue(identity.enabled());
        assertEquals("fixture-encoded-secret", identity.passwordHash());
        assertFalse(identity.toString().contains("fixture-encoded-secret"));
        verify(statement).setString(1, "' OR 1=1 --");
        verify(statement).close();
        verify(rows).close();
        verify(connection).close();
        verify(connection, never()).commit();
    }

    @Test
    void credentialsAreRereadRatherThanCached() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);
        when(rows.next()).thenReturn(true, false, true, false);
        when(rows.getString("username")).thenReturn("account");
        when(rows.getString("password_hash")).thenReturn("fixture-old", "fixture-new");
        when(rows.getInt("enabled")).thenReturn(1, 0);
        assertTrue(repository.findForAuthentication("account").orElseThrow().enabled());
        var second = repository.findForAuthentication("account").orElseThrow();
        assertFalse(second.enabled());
        assertEquals("fixture-new", second.passwordHash());
        verify(dataSource, times(2)).getConnection();
        verify(statement, times(2)).executeQuery();
    }

    @Test
    void missingCredentialsAreEmpty() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);
        when(rows.next()).thenReturn(false);
        assertTrue(repository.findForAuthentication("absent").isEmpty());
        verify(connection).close();
    }

    @Test
    void authenticationCannotJoinAnOperationTransaction() {
        bindOperation();
        assertThrows(IllegalStateException.class, () -> repository.findForAuthentication("account"));
        verifyNoInteractions(dataSource, connection, statement);
    }

    @Test
    void authenticationRejectsAnUnexpectedNonAutocommitConnection() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> repository.findForAuthentication("account"));
        verify(connection).close();
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void permissionLookupRequiresAnActualOperationTransaction() {
        assertThrows(IllegalStateException.class, () -> repository.loadCurrentPermissions(id));
        verifyNoInteractions(dataSource, connection, statement);
    }

    @Test
    void permissionsUseOneQueryAndTheTransactionBoundConnectionWithoutPasswordData() throws SQLException {
        bindOperation();
        when(connection.getAutoCommit()).thenReturn(false);
        when(rows.next()).thenReturn(true, true, false);
        when(rows.getInt("enabled")).thenReturn(1);
        when(rows.getString("role")).thenReturn("Requester", "Reviewer");
        var actor = repository.loadCurrentPermissions(id).orElseThrow();
        assertEquals(Set.of(Requester, Reviewer), actor.roles());
        assertTrue(actor.enabled());
        assertEquals(id, actor.userId());
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertFalse(sql.getValue().contains("password_hash"));
        verify(statement).setString(1, id.toString());
        verify(statement).executeQuery();
        verify(connection, never()).close();
        verify(dataSource, never()).getConnection();
    }

    @Test
    void permissionReadsObserveNewMockedRolesAndEnabledStatusEachTime() throws SQLException {
        bindOperation();
        when(connection.getAutoCommit()).thenReturn(false);
        when(rows.next()).thenReturn(true, false, true, false);
        when(rows.getInt("enabled")).thenReturn(1, 0);
        when(rows.getString("role")).thenReturn("Reviewer", (String) null);
        assertTrue(repository.loadCurrentPermissions(id).orElseThrow().roles().contains(Reviewer));
        var fresh = repository.loadCurrentPermissions(id).orElseThrow();
        assertFalse(fresh.enabled());
        assertTrue(fresh.roles().isEmpty());
        verify(statement, times(2)).executeQuery();
    }

    @Test
    void missingPermissionIdentityIsEmpty() throws SQLException {
        bindOperation();
        when(connection.getAutoCommit()).thenReturn(false);
        when(rows.next()).thenReturn(false);
        assertTrue(repository.loadCurrentPermissions(id).isEmpty());
    }

    @Test
    void sqlFailuresDoNotExposeDriverDetailsOrCredentialValues() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);
        when(statement.executeQuery()).thenThrow(new SQLException("fixture-encoded-secret"));
        var failure = assertThrows(DataAccessResourceFailureException.class,
                () -> repository.findForAuthentication("account"));
        assertFalse(failure.toString().contains("fixture-encoded-secret"));
        assertNull(failure.getCause());
        verify(connection).close();
    }

    private void bindOperation() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.bindResource(dataSource, new ConnectionHolder(connection));
    }
}
