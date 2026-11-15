package com.example.changeflow.repository;

import com.example.changeflow.domain.ReturnReason;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReturnReasonRepository {
    private final JdbcTemplate jdbc;
    public ReturnReasonRepository(DataSource source) { jdbc = new JdbcTemplate(source); }
    public void append(ReturnReason reason) {
        jdbc.update("INSERT INTO return_reason(request_id,request_version,reviewer_id,reason) VALUES (?,?,?,?)",
                reason.requestId().toString(), reason.requestVersion(), reason.reviewerId().toString(), reason.reason());
    }
}
