package com.tangfan.aiagent.infra.database;

import com.tangfan.aiagent.model.ConversationHistory;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.SelectionQuery;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class PostgresDbClient {
    private static PostgresDbClient instance;

    private final SessionFactory sessionFactory;

    private PostgresDbClient() {
        String jdbcUrl = System.getenv("POSTGRES_JDBC_URL");
        String username = System.getenv("POSTGRES_USER");
        String password = System.getenv("POSTGRES_PASSWORD");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: POSTGRES_JDBC_URL");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: POSTGRES_USER");
        }
        if (password == null) {
            throw new IllegalStateException("Missing required environment variable: POSTGRES_PASSWORD");
        }

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.postgresql.Driver")
                .applySetting("hibernate.connection.url", jdbcUrl)
                .applySetting("hibernate.connection.username", username)
                .applySetting("hibernate.connection.password", password)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .applySetting("hibernate.show_sql", "false")
                .build();
        try {
            sessionFactory = new MetadataSources(registry)
                    .addAnnotatedClass(ConversationHistory.class)
                    .buildMetadata()
                    .buildSessionFactory();
        } catch (RuntimeException exception) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw new IllegalStateException("Failed to initialize Hibernate", exception);
        }
    }

    public static synchronized PostgresDbClient fromEnvironment() {
        if (instance == null) {
            instance = new PostgresDbClient();
        }
        return instance;
    }

    public int insert(String tableName, Object entity) throws SQLException {
        Objects.requireNonNull(entity, "entity");
        String entityName = quoteTable(tableName, entity.getClass());
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                session.persist(entity);
                transaction.commit();
                return 1;
            } catch (RuntimeException exception) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw new SQLException("Hibernate insert failed for " + entityName, exception);
            }
        }
    }

    public <T> List<T> select(String tableName, Class<T> resultType, Object conditions) throws SQLException {
        Objects.requireNonNull(resultType, "resultType");
        String entityName = quoteTable(tableName, resultType);
        Map<String, Object> conditionValues = fieldsToValues(conditions);
        String hql = "from " + entityName + " e" + whereClause(conditionValues);

        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                SelectionQuery<T> query = session.createSelectionQuery(hql, resultType);
                conditionValues.forEach((field, value) -> query.setParameter("condition_" + field, value));
                List<T> rows = query.getResultList();
                transaction.commit();
                return rows;
            } catch (RuntimeException exception) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw new SQLException("Hibernate select failed for " + entityName, exception);
            }
        }
    }

    public int update(String tableName, Object valuesObject, Object conditions) throws SQLException {
        Objects.requireNonNull(valuesObject, "valuesObject");
        Objects.requireNonNull(conditions, "conditions");
        if (!valuesObject.getClass().equals(conditions.getClass())) {
            throw new IllegalArgumentException("Update values and conditions must use the same entity class");
        }
        String entityName = quoteTable(tableName, valuesObject.getClass());
        Map<String, Object> values = fieldsToValues(valuesObject);
        Map<String, Object> conditionValues = fieldsToValues(conditions);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Update object has no non-null fields");
        }
        if (conditionValues.isEmpty()) {
            throw new IllegalArgumentException("Update requires at least one non-null condition");
        }

        String assignments = values.keySet().stream()
                .map(field -> "e." + field + " = :value_" + field)
                .collect(Collectors.joining(", "));
        String hql = "update " + entityName + " e set " + assignments + whereClause(conditionValues);

        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                MutationQuery query = session.createMutationQuery(hql);
                values.forEach((field, value) -> query.setParameter("value_" + field, value));
                conditionValues.forEach((field, value) -> query.setParameter("condition_" + field, value));
                int updatedRows = query.executeUpdate();
                transaction.commit();
                return updatedRows;
            } catch (RuntimeException exception) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw new SQLException("Hibernate update failed for " + entityName, exception);
            }
        }
    }

    public int delete(String tableName, Object conditions) throws SQLException {
        Objects.requireNonNull(conditions, "conditions");
        String entityName = quoteTable(tableName, conditions.getClass());
        Map<String, Object> conditionValues = fieldsToValues(conditions);
        if (conditionValues.isEmpty()) {
            throw new IllegalArgumentException("Delete requires at least one non-null condition");
        }
        String hql = "delete from " + entityName + " e" + whereClause(conditionValues);

        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            try {
                MutationQuery query = session.createMutationQuery(hql);
                conditionValues.forEach((field, value) -> query.setParameter("condition_" + field, value));
                int deletedRows = query.executeUpdate();
                transaction.commit();
                return deletedRows;
            } catch (RuntimeException exception) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw new SQLException("Hibernate delete failed for " + entityName, exception);
            }
        }
    }

    private static Map<String, Object> fieldsToValues(Object object) {
        if (object == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (Field field : object.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(object);
                if (value != null) {
                    values.put(field.getName(), value);
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalArgumentException("Cannot read field: " + field.getName(), exception);
            }
        }
        return values;
    }

    private static String quoteTable(String tableName, Class<?> entityType) {
        Entity entity = entityType.getAnnotation(Entity.class);
        Table table = entityType.getAnnotation(Table.class);
        if (entity == null || table == null) {
            throw new IllegalArgumentException("Class must be a mapped Hibernate entity: "
                    + entityType.getName());
        }
        String expectedTable = table.schema().isBlank()
                ? table.name()
                : table.schema() + "." + table.name();
        if (!tableName.equals(table.name()) && !tableName.equals(expectedTable)) {
            throw new IllegalArgumentException("Table name " + tableName + " does not match entity table "
                    + expectedTable);
        }
        return entity.name().isBlank() ? entityType.getSimpleName() : entity.name();
    }

    private static String whereClause(Map<String, Object> conditions) {
        if (conditions.isEmpty()) {
            return "";
        }
        List<String> predicates = new ArrayList<>();
        conditions.keySet().forEach(field -> predicates.add("e." + field + " = :condition_" + field));
        return " where " + String.join(" and ", predicates);
    }
}
