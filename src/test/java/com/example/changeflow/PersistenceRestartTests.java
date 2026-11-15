package com.example.changeflow;

import com.example.changeflow.domain.ChangeRequestService;
import com.example.changeflow.support.SqliteTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceRestartTests {
    @Test void closedApplicationContextsReopenTheSamePersistentFileWithoutReset() {
        var fixture = new SqliteTestSupport();
        var id = fixture.request(fixture.owner, "ChangesRequested", 2);
        fixture.jdbc.update("INSERT INTO return_reason VALUES (?,2,?,?)", id.toString(), fixture.reviewer.toString(), "persisted reason");
        Object previousSource = null;
        for (int restart = 0; restart < 2; restart++) {
            try (var context = new SpringApplicationBuilder(ChangeFlowApplication.class).run(
                    "--changeflow.database.path=" + fixture.file, "--server.port=0", "--spring.main.banner-mode=off")) {
                var source = context.getBean(javax.sql.DataSource.class);
                assertNotSame(previousSource, source);
                previousSource = source;
                var view = context.getBean(ChangeRequestService.class).get(fixture.owner, id);
                assertEquals(2, view.request().version());
                assertEquals("title", view.request().title());
                assertEquals("ChangesRequested", view.request().status().name());
                assertEquals("persisted reason", view.latestReturnReason().reason());
            }
        }
    }
}
