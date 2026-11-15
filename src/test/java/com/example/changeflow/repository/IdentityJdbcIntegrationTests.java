package com.example.changeflow.repository;

import com.example.changeflow.support.WorkflowTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityJdbcIntegrationTests {
    @Test void authenticationEndsBeforeOperationAndPermissionsAreFirstAndUncached() {
        var f = new WorkflowTestSupport();
        assertTrue(f.identities.findForAuthentication("owner").isPresent());
        assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(f.db.dataSource));
        var identities = spy(f.identities);
        var requests = spy(f.requests);
        var id = f.db.request(f.db.owner, "Draft", 0);
        f.service(identities, requests, f.reasons).get(f.db.owner, id);
        var order = inOrder(identities, requests);
        order.verify(identities).loadCurrentPermissions(f.db.owner);
        order.verify(requests).find(id);
        f.db.externalUpdate("UPDATE app_user SET enabled=0 WHERE id=?", f.db.owner.toString());
        assertFalse(f.identities.findForAuthentication("owner").orElseThrow().enabled());
        new TransactionTemplate(new JdbcTransactionManager(f.db.dataSource)).execute(status -> {
            assertFalse(f.identities.loadCurrentPermissions(f.db.owner).orElseThrow().enabled());
            assertThrows(IllegalStateException.class, () -> f.identities.findForAuthentication("owner"));
            var connection = DataSourceUtils.getConnection(f.db.dataSource);
            try { assertFalse(connection.isClosed()); } catch (java.sql.SQLException failure) { throw new AssertionError(failure); }
            finally { DataSourceUtils.releaseConnection(connection, f.db.dataSource); }
            return null;
        });
        assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(f.db.dataSource));
    }

    @Test void unprovisionedOrInvalidStoragePathsFailClosedWithoutCreatingDirectories() {
        var config = new DataSourceConfiguration();
        var file = com.example.changeflow.support.SqliteTestSupport.newFile();
        assertThrows(IllegalArgumentException.class, () -> config.dataSource(file.getParent().toString(), 100));
        assertThrows(IllegalArgumentException.class, () -> config.dataSource(file.resolve("missing").resolve("db.sqlite").toString(), 100));
        assertThrows(IllegalArgumentException.class, () -> config.dataSource(":memory:", 100));
        assertThrows(IllegalArgumentException.class, () -> config.dataSource(file.toString(), 0));
    }
}
