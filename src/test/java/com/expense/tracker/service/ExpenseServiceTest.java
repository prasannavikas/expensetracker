package com.expense.tracker.service;

import com.expense.tracker.enums.Category;
import com.expense.tracker.enums.TransactionType;
import com.expense.tracker.exception.ExpenseNotFoundException;
import com.expense.tracker.model.Transaction;
import com.expense.tracker.repository.InMemoryTransactionRepository;
import com.expense.tracker.reports.ExpenseReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExpenseServiceTest {
    private ExpenseService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseService(new InMemoryTransactionRepository());
    }

    @Test
    void calculatesMonthlyTotalsAndGroupsExpensesByCategory() {
        YearMonth november = YearMonth.of(2026, 11);
        service.addIncome("Salary", new BigDecimal("30000.00"), "", LocalDate.of(2026, 11, 1));
        service.addExpense("Groceries", new BigDecimal("3000.00"), Category.FOOD,
                "", LocalDate.of(2026, 11, 5));
        service.addExpense("Bus", new BigDecimal("200.00"), Category.TRAVEL,
                "", LocalDate.of(2026, 11, 6));
        service.addExpense("December bill", new BigDecimal("1000.00"), Category.BILLS,
                "", LocalDate.of(2026, 12, 1));

        assertEquals(new BigDecimal("30000.00"), service.getTotalIncome(november));
        assertEquals(new BigDecimal("3200.00"), service.getTotalExpenses(november));
        assertEquals(Map.of(
                Category.FOOD, new BigDecimal("3000.00"),
                Category.TRAVEL, new BigDecimal("200.00")),
                service.getExpensesByCategory(november));
    }

    @Test
    void generatesReportAndComparesMonths() {
        YearMonth november = YearMonth.of(2026, 11);
        YearMonth december = YearMonth.of(2026, 12);
        service.addIncome("Salary", new BigDecimal("30000.00"), "", LocalDate.of(2026, 11, 1));
        service.addExpense("Large", new BigDecimal("2500.00"), Category.SHOPPING,
                "", LocalDate.of(2026, 11, 10));
        service.addExpense("Small", new BigDecimal("100.00"), Category.FOOD,
                "", LocalDate.of(2026, 11, 11));
        service.addExpense("December", new BigDecimal("1000.00"), Category.BILLS,
                "", LocalDate.of(2026, 12, 1));

        ExpenseReport report = service.generateReport(november);

        assertEquals(new BigDecimal("30000.00"), report.getTotalIncome());
        assertEquals(new BigDecimal("2600.00"), report.getTotalExpenses());
        assertEquals(new BigDecimal("27400.00"), report.getSavings());
        assertEquals("Large", report.getHighestExpense().orElseThrow().getTitle());
        assertEquals("Small", report.getLowestExpense().orElseThrow().getTitle());
        assertEquals(new BigDecimal("-1600.00"), service.compareExpenses(november, december));
    }

    @Test
    void supportsSearchUpdateAndDelete() {
        Transaction added = service.addIncome(
                "Old title", new BigDecimal("100.00"), "", LocalDate.of(2026, 11, 1));

        Transaction updated = service.updateTransaction(added.getId(), "New title",
                new BigDecimal("150.00"), TransactionType.EXPENSE, Category.EDUCATION,
                "Course book", LocalDate.of(2026, 11, 2));
        assertEquals("New title", service.findTransactionById(added.getId()).getTitle());
        assertEquals(TransactionType.EXPENSE, updated.getType());

        service.deleteTransaction(added.getId());
        assertThrows(ExpenseNotFoundException.class, () -> service.findTransactionById(added.getId()));
    }

    @Test
    void rejectsMissingExpenseCategoryAndAmountsWithMoreThanTwoDecimals() {
        assertThrows(IllegalArgumentException.class, () -> service.addExpense(
                "Missing category", BigDecimal.ONE, null, "", LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> service.addIncome(
                "Fractional cents", new BigDecimal("1.001"), "", LocalDate.now()));
    }
}
