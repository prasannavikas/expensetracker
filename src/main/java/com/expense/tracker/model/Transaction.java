package com.expense.tracker.model;

import com.expense.tracker.enums.Category;
import com.expense.tracker.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class Transaction {
    private long id;
    private String title;
    private BigDecimal amount;
    private TransactionType type;
    private Category category;
    private String description;
    private LocalDate date;

    public Transaction(long id, String title, BigDecimal amount, TransactionType type,
                       Category category, String description, LocalDate date) {
        setId(id);
        setTitle(title);
        setAmount(amount);
        this.type = Objects.requireNonNull(type, "Transaction type cannot be null.");
        validateCategory(type, category);
        this.category = category;
        setDescription(description);
        setDate(date);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("Transaction ID cannot be negative.");
        }
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Transaction title cannot be blank.");
        }
        String normalizedTitle = title.trim();
        if (normalizedTitle.length() > 150) {
            throw new IllegalArgumentException("Transaction title cannot exceed 150 characters.");
        }
        this.title = normalizedTitle;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero.");
        }
        BigDecimal normalizedAmount = amount.stripTrailingZeros();
        if (normalizedAmount.scale() > 2 || normalizedAmount.precision() - normalizedAmount.scale() > 17) {
            throw new IllegalArgumentException(
                    "Transaction amount must fit a DECIMAL(19, 2) value.");
        }
        this.amount = amount;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        TransactionType newType = Objects.requireNonNull(type, "Transaction type cannot be null.");
        if (newType == TransactionType.EXPENSE) {
            validateCategory(newType, category);
        } else {
            category = null;
        }
        this.type = newType;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        validateCategory(type, category);
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        String normalizedDescription = description == null ? "" : description.trim();
        if (normalizedDescription.length() > 1000) {
            throw new IllegalArgumentException("Description cannot exceed 1000 characters.");
        }
        this.description = normalizedDescription;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = Objects.requireNonNull(date, "Transaction date cannot be null.");
    }

    private static void validateCategory(TransactionType type, Category category) {
        if (type == TransactionType.EXPENSE && category == null) {
            throw new IllegalArgumentException("An expense must have a category.");
        }
        if (type == TransactionType.INCOME && category != null) {
            throw new IllegalArgumentException("Income transactions do not use an expense category.");
        }
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", amount=" + amount +
                ", type=" + type +
                ", category=" + category +
                ", description='" + description + '\'' +
                ", date=" + date +
                '}';
    }
}
