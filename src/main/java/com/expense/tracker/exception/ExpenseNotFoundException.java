package com.expense.tracker.exception;

public class ExpenseNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExpenseNotFoundException(long id) {
        super("No transaction found with ID " + id + ".");
    }
}
