package com.expense.tracker.service;

import com.expense.tracker.enums.Category;
import com.expense.tracker.enums.TransactionType;
import com.expense.tracker.exception.ExpenseNotFoundException;
import com.expense.tracker.model.Transaction;
import com.expense.tracker.repository.TransactionRepository;
import com.expense.tracker.reports.ExpenseReport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ExpenseService {
    private final TransactionRepository repository;

    public ExpenseService(TransactionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Transaction repository cannot be null.");
    }

    public Transaction addIncome(String title, BigDecimal amount, String description,
                                 LocalDate date) {
        return repository.insert(new Transaction(
                0, title, amount, TransactionType.INCOME, null, description, date));
    }

    public Transaction addExpense(String title, BigDecimal amount, Category category,
                                  String description, LocalDate date) {
        return repository.insert(new Transaction(
                0, title, amount, TransactionType.EXPENSE, category, description, date));
    }

    public List<Transaction> getAllTransactions() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Transaction::getDate)
                        .thenComparingLong(Transaction::getId))
                .toList();
    }

    public Transaction findTransactionById(long id) {
        return repository.findById(id).orElseThrow(() -> new ExpenseNotFoundException(id));
    }

    public Transaction updateTransaction(long id, String title, BigDecimal amount,
                                         TransactionType type, Category category,
                                         String description, LocalDate date) {
        findTransactionById(id);
        return repository.update(new Transaction(id, title, amount, type, category, description, date));
    }

    public void deleteTransaction(long id) {
        if (!repository.deleteById(id)) {
            throw new ExpenseNotFoundException(id);
        }
    }

    public BigDecimal getTotalIncome() {
        return sumAmounts(repository.findAll().stream()
                .filter(transaction -> transaction.getType() == TransactionType.INCOME));
    }

    public BigDecimal getTotalIncome(YearMonth month) {
        return sumAmounts(transactionsForMonth(month).stream()
                .filter(transaction -> transaction.getType() == TransactionType.INCOME));
    }

    public BigDecimal getTotalExpenses(YearMonth month) {
        return sumAmounts(transactionsForMonth(month).stream()
                .filter(transaction -> transaction.getType() == TransactionType.EXPENSE));
    }

    public List<Transaction> getExpensesForMonth(YearMonth month) {
        return transactionsForMonth(month).stream()
                .filter(transaction -> transaction.getType() == TransactionType.EXPENSE)
                .sorted(Comparator.comparing(Transaction::getDate)
                        .thenComparingLong(Transaction::getId))
                .toList();
    }

    public Map<Category, BigDecimal> getExpensesByCategory(YearMonth month) {
        return getExpensesForMonth(month).stream()
                .collect(Collectors.groupingBy(
                        Transaction::getCategory,
                        () -> new java.util.EnumMap<>(Category.class),
                        Collectors.mapping(Transaction::getAmount,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
    }

    public ExpenseReport generateReport(YearMonth month) {
        return new ExpenseReport(month, transactionsForMonth(month));
    }

    public BigDecimal compareExpenses(YearMonth firstMonth, YearMonth secondMonth) {
        return getTotalExpenses(secondMonth).subtract(getTotalExpenses(firstMonth));
    }

    private List<Transaction> transactionsForMonth(YearMonth month) {
        return repository.findAll().stream()
                .filter(transaction -> YearMonth.from(transaction.getDate()).equals(month))
                .toList();
    }

    private static BigDecimal sumAmounts(java.util.stream.Stream<Transaction> transactions) {
        return transactions.map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
