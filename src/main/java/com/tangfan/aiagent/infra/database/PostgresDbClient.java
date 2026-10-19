package com.tangfan.aiagent.infra.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Small parameterized JDBC helper for PostgreSQL tables.
 *
 * <p>Configure {@code POSTGRES_JDBC_URL}, {@code POSTGRES_USER}, and
 * {@code POSTGRES_PASSWORD}, or pass connection details to the constructor.
 * Table and column names are SQL identifiers and are validated separately;
 * data values are always bound as prepared-statement parameters.
 */
public class PostgresDbClient {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public PostgresDbClient(String jdbcUrl, String username, String password) {
        this.jdbcUrl = requireText(jdbcUrl, "JDBC URL");
        this.username = requireText(username, "username");
        this.password = Objects.requireNonNull(password, "password");
    }

    public static PostgresDbClient fromEnvironment() {
        return new PostgresDbClient(
                requiredEnvironmentValue("POSTGRES_JDBC_URL"),
                requiredEnvironmentValue("POSTGRES_USER"),
                environmentValue("POSTGRES_PASSWORD", ""));
    }

    public int insert(String table, Map<String, ?> values) throws SQLException {
        requireValues(values);
        List<String> columns = new ArrayList<>(values.keySet());
        String sql = "INSERT INTO " + quoteTable(table) + " ("
                + columns.stream().map(PostgresDbClient::quoteColumn).collect(Collectors.joining(", "))
                + ") VALUES (" + placeholders(columns.size()) + ")";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindValues(statement, values.values());
            return statement.executeUpdate();
        }
    }

    public List<Map<String, Object>> select(String table, List<String> columns,
                                            Map<String, ?> conditions) throws SQLException {
        Objects.requireNonNull(columns, "columns");
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("At least one selected column is required");
        }
        if (columns.contains("*") && columns.size() != 1) {
            throw new IllegalArgumentException("'*' cannot be combined with selected columns");
        }

        String selectedColumns = columns.get(0).equals("*")
                ? "*"
                : columns.stream().map(PostgresDbClient::quoteColumn).collect(Collectors.joining(", "));
        SqlConditions where = buildWhereConditions(conditions);
        String sql = "SELECT " + selectedColumns + " FROM " + quoteTable(table) + where.sql();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindValues(statement, where.values());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                int columnCount = resultSet.getMetaData().getColumnCount();
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int index = 1; index <= columnCount; index++) {
                        row.put(resultSet.getMetaData().getColumnLabel(index), resultSet.getObject(index));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    public int update(String table, Map<String, ?> values, Map<String, ?> conditions) throws SQLException {
        requireValues(values);
        SqlConditions where = buildWhereConditions(conditions);
        if (where.sql().isEmpty()) {
            throw new IllegalArgumentException("Update requires at least one condition");
        }

        String assignments = values.keySet().stream()
                .map(column -> quoteColumn(column) + " = ?")
                .collect(Collectors.joining(", "));
        String sql = "UPDATE " + quoteTable(table) + " SET " + assignments + where.sql();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindValues(statement, values.values());
            bindValues(statement, values.size() + 1, where.values());
            return statement.executeUpdate();
        }
    }

    public int delete(String table, Map<String, ?> conditions) throws SQLException {
        SqlConditions where = buildWhereConditions(conditions);
        if (where.sql().isEmpty()) {
            throw new IllegalArgumentException("Delete requires at least one condition");
        }

        String sql = "DELETE FROM " + quoteTable(table) + where.sql();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindValues(statement, where.values());
            return statement.executeUpdate();
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private static SqlConditions buildWhereConditions(Map<String, ?> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return new SqlConditions("", List.of());
        }

        List<Object> values = new ArrayList<>();
        List<String> predicates = new ArrayList<>();
        conditions.forEach((column, value) -> {
            if (value == null) {
                predicates.add(quoteColumn(column) + " IS NULL");
            } else {
                predicates.add(quoteColumn(column) + " = ?");
                values.add(value);
            }
        });
        return new SqlConditions(" WHERE " + String.join(" AND ", predicates), values);
    }

    private static void bindValues(PreparedStatement statement, Iterable<?> values) throws SQLException {
        bindValues(statement, 1, values);
    }

    private static void bindValues(PreparedStatement statement, int startIndex,
                                   Iterable<?> values) throws SQLException {
        int index = startIndex;
        for (Object value : values) {
            statement.setObject(index++, value);
        }
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private static String quoteTable(String table) {
        String[] parts = requireText(table, "table").split("\\.", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException("Table must be a table name or schema-qualified table name");
        }
        return java.util.Arrays.stream(parts)
                .map(part -> "\"" + validateIdentifier(part) + "\"")
                .collect(Collectors.joining("."));
    }

    private static String quoteColumn(String column) {
        return "\"" + validateIdentifier(column) + "\"";
    }

    private static String validateIdentifier(String identifier) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return identifier;
    }

    private static void requireValues(Map<String, ?> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("At least one field value is required");
        }
    }

    private static String requiredEnvironmentValue(String name) {
        String value = environmentValue(name, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static String environmentValue(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private record SqlConditions(String sql, List<Object> values) {
    }
}
