package com.bighealth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.StringJoiner;

@Service
public class SchemaService {

    @Autowired
    private DataSource dataSource;

    public void initializeSchemas() {
        try (Connection connection = dataSource.getConnection()) {
            for (String schema : Schemas.SCHEMAS) {
                createSchema(connection, schema);
                executeSqlFile(connection, schema);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize schemas", e);
        }
    }

    private void createSchema(Connection connection, String schema) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            statement.execute("USE " + schema);
        }
    }

    private void executeSqlFile(Connection connection, String schema) throws Exception {
        ClassPathResource resource = new ClassPathResource("tables.sql");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            StringJoiner joiner = new StringJoiner("\n");
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    joiner.add(line);
                    if (line.endsWith(";")) {
                        try (Statement statement = connection.createStatement()) {
                            statement.execute(joiner.toString());
                        }
                        joiner = new StringJoiner("\n");
                    }
                }
            }
        }
    }
}