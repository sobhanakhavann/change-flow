package com.example.changeflow.support;

import com.example.changeflow.domain.*;
import com.example.changeflow.repository.*;
import org.springframework.jdbc.support.JdbcTransactionManager;

public class WorkflowTestSupport {
    public final SqliteTestSupport db = new SqliteTestSupport();
    public final IdentityRepository identities = new IdentityRepository(db.dataSource);
    public final ChangeRequestRepository requests = new ChangeRequestRepository(db.dataSource);
    public final ReturnReasonRepository reasons = new ReturnReasonRepository(db.dataSource);
    public ChangeRequestService service() { return service(identities, requests, reasons); }
    public ChangeRequestService service(IdentityRepository identity, ChangeRequestRepository request, ReturnReasonRepository reason) {
        return new ChangeRequestService(identity, request, reason, new DataSourceConfiguration().transactionManager(db.dataSource));
    }
    public static ChangeRequestRules.Content complete() { return new ChangeRequestRules.Content("title", "description", "reason"); }
    public static ExpectedVersion version(java.util.UUID id, long version) { return new ExpectedVersion(id, version); }
}
