package com.example.chatapp.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FlywayMigrationIntegrationTest {

    @Test
    void flyway_FullMigrationCycle_ShouldApplyV1AndV2Successfully() throws Exception {
        // Arrange: In-memory H2 database in MySQL mode
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:flyway_test_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        // Clean any existing schema
        flyway.clean();

        // Act: Run migrations
        MigrateResult result = flyway.migrate();

        // Assert: Both migrations executed
        assertTrue(result.success, "Flyway migrations should succeed");
        assertTrue(result.migrationsExecuted >= 2, "Should execute at least V1 and V2");

        // Validate checksums
        assertDoesNotThrow(flyway::validate, "Flyway validation should pass without checksum mismatch");

        // Inspect database metadata
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, "workspace_invitations", null)) {
                Set<String> columnNames = new HashSet<>();
                while (columns.next()) {
                    columnNames.add(columns.getString("COLUMN_NAME").toLowerCase());
                }

                assertTrue(columnNames.contains("custom_message"), "workspace_invitations should have custom_message column");
                assertTrue(columnNames.contains("initial_channel_ids"), "workspace_invitations should have initial_channel_ids column");
                assertTrue(columnNames.contains("token"), "workspace_invitations should have token column");
                assertTrue(columnNames.contains("status"), "workspace_invitations should have status column");
            }
        }
    }
}
