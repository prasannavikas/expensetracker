package com.expense.tracker.repository;

import com.expense.tracker.database.DBConnection;
import com.expense.tracker.enums.Category;
import com.expense.tracker.enums.TransactionType;
import com.expense.tracker.exception.ExpenseStorageException;
import com.expense.tracker.model.Transaction;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcTransactionRepository implements TransactionRepository {
    private static final String COLUMNS =
            "id, title, amount, transaction_type, category, description, transaction_date";

    @Override
    public Transaction insert(Transaction transaction) {
        String sql = "INSERT INTO transactions "
                + "(title, amount, transaction_type, category, description, transaction_date) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindTransaction(statement, transaction, false);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return a generated transaction ID.");
                }
                return new Transaction(keys.getLong(1), transaction.getTitle(), transaction.getAmount(),
                        transaction.getType(), transaction.getCategory(),
                        transaction.getDescription(), transaction.getDate());
            }
        } catch (SQLException exception) {
            throw storageFailure("insert transaction", exception);
        }
    }

    @Override
    public List<Transaction> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM transactions ORDER BY transaction_date, id";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<Transaction> transactions = new ArrayList<>();
            while (results.next()) {
                transactions.add(readTransaction(results));
            }
            return List.copyOf(transactions);
        } catch (SQLException exception) {
            throw storageFailure("load transactions", exception);
        }
    }

    @Override
    public Optional<Transaction> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM transactions WHERE id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(readTransaction(results)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw storageFailure("find transaction", exception);
        }
    }

    @Override
    public Transaction update(Transaction transaction) {
        String sql = "UPDATE transactions SET title = ?, amount = ?, transaction_type = ?, "
                + "category = ?, description = ?, transaction_date = ? WHERE id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindTransaction(statement, transaction, true);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException(
                        "Cannot update transaction " + transaction.getId() + " because it does not exist.");
            }
            return transaction;
        } catch (SQLException exception) {
            throw storageFailure("update transaction", exception);
        }
    }

    @Override
    public boolean deleteById(long id) {
        String sql = "DELETE FROM transactions WHERE id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw storageFailure("delete transaction", exception);
        }
    }

    private static void bindTransaction(PreparedStatement statement, Transaction transaction,
                                        boolean includeId) throws SQLException {
        statement.setString(1, transaction.getTitle());
        statement.setBigDecimal(2, transaction.getAmount());
        statement.setString(3, transaction.getType().name());
        if (transaction.getCategory() == null) {
            statement.setNull(4, java.sql.Types.VARCHAR);
        } else {
            statement.setString(4, transaction.getCategory().name());
        }
        statement.setString(5, transaction.getDescription());
        statement.setDate(6, Date.valueOf(transaction.getDate()));
        if (includeId) {
            statement.setLong(7, transaction.getId());
        }
    }

    private static Transaction readTransaction(ResultSet results) throws SQLException {
        String categoryName = results.getString("category");
        Category category = categoryName == null ? null : Category.valueOf(categoryName);
        return new Transaction(
                results.getLong("id"),
                results.getString("title"),
                results.getBigDecimal("amount"),
                TransactionType.valueOf(results.getString("transaction_type")),
                category,
                results.getString("description"),
                results.getDate("transaction_date").toLocalDate());
    }

    private static ExpenseStorageException storageFailure(String operation, SQLException cause) {
        return new ExpenseStorageException("Database error while attempting to " + operation + ".", cause);
    }
}
