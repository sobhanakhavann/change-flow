package com.example.changeflow.repository;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Only for our unpooled, independently owned physical SQLite connections.
 * Restoring autoCommit can issue COMMIT in Xerial, including after failed rollback.
 * A disposable connection needs no reset: close it, which discards pending work.
 * Do not use this manager with a pool's logical close/reset operation.
 */
public class SqliteTransactionManager extends JdbcTransactionManager {
    public SqliteTransactionManager(DataSource source) {
        super(source);
        setNestedTransactionAllowed(false);
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        var holder = (ConnectionHolder) TransactionSynchronizationManager.unbindResource(obtainDataSource());
        try {
            holder.getConnection().close();
        } catch (SQLException failure) {
            throw new TransactionSystemException("SQLite connection cleanup failed");
        } finally {
            holder.clear();
        }
    }
}
