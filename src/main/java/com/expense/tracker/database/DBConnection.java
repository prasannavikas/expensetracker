package com.expense.tracker.database;

import com.expense.tracker.exception.ExpenseStorageException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DBConnection {
    private DBConnection() {
    }

    public static boolean isConfigured() {
        String url = System.getenv("EXPENSE_DB_URL");
        return url != null && !url.isBlank();
    }

    public static Connection getConnection() {
        String url = System.getenv("EXPENSE_DB_URL");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Set EXPENSE_DB_URL to enable MySQL storage.");
        }
        String user = System.getenv().getOrDefault("EXPENSE_DB_USER", "");
        String password = System.getenv().getOrDefault("EXPENSE_DB_PASSWORD", "");
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException exception) {
            throw new ExpenseStorageException(
                    "Could not connect to the configured database. Check MySQL, the JDBC URL, and credentials.",
                    exception);
        }
    }
}
