package com.expense.tracker.reports;

import com.expense.tracker.enums.Category;
import com.expense.tracker.enums.TransactionType;
import com.expense.tracker.model.Transaction;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ExpenseReport {
    private final YearMonth month;
    private final BigDecimal totalIncome;
    private final BigDecimal totalExpenses;
    private final Map<Category, BigDecimal> categoryTotals;
    private final Optional<Transaction> highestExpense;
    private final Optional<Transaction> lowestExpense;

    public ExpenseReport(YearMonth month, List<Transaction> transactions) {
        this.month = month;
        List<Transaction> incomes = transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.INCOME)
                .toList();
        List<Transaction> expenses = transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.EXPENSE)
                .toList();

        this.totalIncome = sum(incomes);
        this.totalExpenses = sum(expenses);
        this.categoryTotals = expenses.stream()
                .collect(Collectors.groupingBy(Transaction::getCategory,
                        () -> new EnumMap<>(Category.class),
                        Collectors.mapping(Transaction::getAmount,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        this.highestExpense = expenses.stream()
                .max(Comparator.comparing(Transaction::getAmount));
        this.lowestExpense = expenses.stream()
                .min(Comparator.comparing(Transaction::getAmount));
    }

    public YearMonth getMonth() {
        return month;
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public BigDecimal getTotalExpenses() {
        return totalExpenses;
    }

    public BigDecimal getSavings() {
        return totalIncome.subtract(totalExpenses);
    }

    public Map<Category, BigDecimal> getCategoryTotals() {
        return Collections.unmodifiableMap(new EnumMap<>(categoryTotals));
    }

    public Optional<Transaction> getHighestExpense() {
        return highestExpense;
    }

    public Optional<Transaction> getLowestExpense() {
        return lowestExpense;
    }

    private static BigDecimal sum(List<Transaction> transactions) {
        return transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
