package com.expense.tracker.exception;

public class ExpenseStorageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExpenseStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
