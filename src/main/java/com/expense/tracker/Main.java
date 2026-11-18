package com.expense.tracker;

import com.expense.tracker.database.DBConnection;
import com.expense.tracker.enums.Category;
import com.expense.tracker.enums.TransactionType;
import com.expense.tracker.exception.ExpenseNotFoundException;
import com.expense.tracker.exception.ExpenseStorageException;
import com.expense.tracker.model.Transaction;
import com.expense.tracker.repository.InMemoryTransactionRepository;
import com.expense.tracker.repository.JdbcTransactionRepository;
import com.expense.tracker.repository.TransactionRepository;
import com.expense.tracker.reports.ExpenseReport;
import com.expense.tracker.service.ExpenseService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

public class Main {
    private static final NumberFormat AMOUNT =
            NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));

    static {
        AMOUNT.setMinimumFractionDigits(2);
        AMOUNT.setMaximumFractionDigits(2);
    }

    private Main() {
    }

    public static void main(String[] args) {
        TransactionRepository repository;
        if (DBConnection.isConfigured()) {
            repository = new JdbcTransactionRepository();
            System.out.println("Storage: MySQL");
        } else {
            repository = new InMemoryTransactionRepository();
            System.out.println("Storage: in-memory (records are cleared when the app exits)");
        }

        ExpenseService service = new ExpenseService(repository);
        if (DBConnection.isConfigured()) {
            try {
                service.getAllTransactions();
            } catch (ExpenseStorageException exception) {
                System.err.println("Could not start with MySQL storage: " + exception.getMessage());
                return;
            }
        }

        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;
            while (running && scanner.hasNextLine()) {
                printMenu();
                String choice = scanner.nextLine().trim();

                try {
                    switch (choice) {
                        case "1" -> addIncome(scanner, service);
                        case "2" -> addExpense(scanner, service);
                        case "3" -> viewTransactions(service);
                        case "4" -> searchTransaction(scanner, service);
                        case "5" -> updateTransaction(scanner, service);
                        case "6" -> deleteTransaction(scanner, service);
                        case "7" -> viewMonthlyExpenses(scanner, service);
                        case "8" -> viewCategoryReport(scanner, service);
                        case "9" -> viewFinancialReport(scanner, service);
                        case "10" -> System.out.println("Total income: " + money(service.getTotalIncome()));
                        case "11" -> compareMonths(scanner, service);
                        case "12" -> {
                            System.out.println("Goodbye!");
                            running = false;
                        }
                        default -> System.out.println("Please choose an option from 1 to 12.");
                    }
                } catch (ExpenseNotFoundException | ExpenseStorageException | IllegalArgumentException exception) {
                    System.out.println("Error: " + exception.getMessage());
                }
                System.out.println();
            }
        }
    }

    private static void printMenu() {
        System.out.println("===== PERSONAL EXPENSE TRACKER =====");
        System.out.println("1. Add Income");
        System.out.println("2. Add Expense");
        System.out.println("3. View All Transactions");
        System.out.println("4. Search Transaction");
        System.out.println("5. Update Transaction");
        System.out.println("6. Delete Transaction");
        System.out.println("7. View Monthly Expenses");
        System.out.println("8. Category-wise Expense Report");
        System.out.println("9. Generate Financial Report");
        System.out.println("10. Display Total Income");
        System.out.println("11. Compare Expenses Between Months");
        System.out.println("12. Exit");
        System.out.print("Enter your choice: ");
    }

    private static void addIncome(Scanner scanner, ExpenseService service) {
        String title = readText(scanner, "Title: ");
        BigDecimal amount = readAmount(scanner, "Amount: ");
        String description = readOptionalText(scanner, "Description (optional): ");
        LocalDate date = readDate(scanner, "Date (yyyy-MM-dd, blank for today): ");
        Transaction transaction = service.addIncome(title, amount, description, date);
        System.out.println("Income added with ID " + transaction.getId() + ".");
    }

    private static void addExpense(Scanner scanner, ExpenseService service) {
        String title = readText(scanner, "Title: ");
        BigDecimal amount = readAmount(scanner, "Amount: ");
        Category category = readCategory(scanner);
        String description = readOptionalText(scanner, "Description (optional): ");
        LocalDate date = readDate(scanner, "Date (yyyy-MM-dd, blank for today): ");
        Transaction transaction = service.addExpense(title, amount, category, description, date);
        System.out.println("Expense added with ID " + transaction.getId() + ".");
    }

    private static void viewTransactions(ExpenseService service) {
        var transactions = service.getAllTransactions();
        if (transactions.isEmpty()) {
            System.out.println("No transactions have been added.");
            return;
        }
        transactions.forEach(System.out::println);
    }

    private static void searchTransaction(Scanner scanner, ExpenseService service) {
        long id = readId(scanner, "Transaction ID: ");
        System.out.println(service.findTransactionById(id));
    }

    private static void updateTransaction(Scanner scanner, ExpenseService service) {
        long id = readId(scanner, "Transaction ID to update: ");
        service.findTransactionById(id);
        String title = readText(scanner, "New title: ");
        BigDecimal amount = readAmount(scanner, "New amount: ");
        TransactionType type = readType(scanner, "New type (INCOME or EXPENSE): ");
        Category category = type == TransactionType.EXPENSE ? readCategory(scanner) : null;
        String description = readOptionalText(scanner, "New description (optional): ");
        LocalDate date = readDate(scanner, "New date (yyyy-MM-dd): ");
        Transaction updated = service.updateTransaction(id, title, amount, type, category, description, date);
        System.out.println("Transaction updated: " + updated);
    }

    private static void deleteTransaction(Scanner scanner, ExpenseService service) {
        long id = readId(scanner, "Transaction ID to delete: ");
        service.deleteTransaction(id);
        System.out.println("Transaction " + id + " deleted.");
    }

    private static void viewMonthlyExpenses(Scanner scanner, ExpenseService service) {
        YearMonth month = readMonth(scanner, "Month (yyyy-MM, blank for current month): ");
        System.out.println("===== MONTHLY EXPENSES =====");
        System.out.println("Month: " + month);
        System.out.println("Total expenses: " + money(service.getTotalExpenses(month)));
        var expenses = service.getExpensesForMonth(month);
        if (expenses.isEmpty()) {
            System.out.println("No expenses recorded for this month.");
        } else {
            expenses.forEach(System.out::println);
        }
    }

    private static void viewCategoryReport(Scanner scanner, ExpenseService service) {
        YearMonth month = readMonth(scanner, "Month (yyyy-MM, blank for current month): ");
        Map<Category, BigDecimal> totals = service.getExpensesByCategory(month);
        System.out.println("===== CATEGORY-WISE EXPENSES: " + month + " =====");
        if (totals.isEmpty()) {
            System.out.println("No expenses recorded for this month.");
            return;
        }
        totals.forEach((category, amount) -> System.out.println(category + ": " + money(amount)));
    }

    private static void viewFinancialReport(Scanner scanner, ExpenseService service) {
        YearMonth month = readMonth(scanner, "Month (yyyy-MM, blank for current month): ");
        ExpenseReport report = service.generateReport(month);
        System.out.println("===== FINANCIAL REPORT =====");
        System.out.println("Month: " + report.getMonth());
        System.out.println("Total income: " + money(report.getTotalIncome()));
        System.out.println("Total expenses: " + money(report.getTotalExpenses()));
        System.out.println("Available balance / savings: " + money(report.getSavings()));
        System.out.println("Category-wise expenses:");
        if (report.getCategoryTotals().isEmpty()) {
            System.out.println("  No expenses recorded.");
        } else {
            report.getCategoryTotals().forEach(
                    (category, amount) -> System.out.println("  " + category + ": " + money(amount)));
        }
        System.out.println("Highest expense: " + report.getHighestExpense()
                .map(transaction -> money(transaction.getAmount()) + " (" + transaction.getTitle() + ")")
                .orElse("None"));
        System.out.println("Lowest expense: " + report.getLowestExpense()
                .map(transaction -> money(transaction.getAmount()) + " (" + transaction.getTitle() + ")")
                .orElse("None"));
    }

    private static void compareMonths(Scanner scanner, ExpenseService service) {
        YearMonth first = readMonthRequired(scanner, "First month (yyyy-MM): ");
        YearMonth second = readMonthRequired(scanner, "Second month (yyyy-MM): ");
        BigDecimal difference = service.compareExpenses(first, second);
        System.out.println(first + " expenses: " + money(service.getTotalExpenses(first)));
        System.out.println(second + " expenses: " + money(service.getTotalExpenses(second)));
        if (difference.signum() > 0) {
            System.out.println("Expenses increased by " + money(difference) + ".");
        } else if (difference.signum() < 0) {
            System.out.println("Expenses decreased by " + money(difference.abs()) + ".");
        } else {
            System.out.println("Expenses were the same in both months.");
        }
    }

    private static String readText(Scanner scanner, String prompt) {
        System.out.print(prompt);
        String value = scanner.nextLine().trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("This value cannot be blank.");
        }
        return value;
    }

    private static String readOptionalText(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static BigDecimal readAmount(Scanner scanner, String prompt) {
        System.out.print(prompt);
        try {
            BigDecimal amount = new BigDecimal(scanner.nextLine().trim());
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero.");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid number for the amount.");
        }
    }

    private static long readId(Scanner scanner, String prompt) {
        System.out.print(prompt);
        try {
            long id = Long.parseLong(scanner.nextLine().trim());
            if (id <= 0) {
                throw new IllegalArgumentException("Transaction ID must be positive.");
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole number for the transaction ID.");
        }
    }

    private static Category readCategory(Scanner scanner) {
        System.out.println("Categories: FOOD, TRAVEL, SHOPPING, EDUCATION, BILLS, "
                + "ENTERTAINMENT, HEALTH, OTHER");
        System.out.print("Category: ");
        try {
            return Category.valueOf(scanner.nextLine().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Choose one of the listed expense categories.");
        }
    }

    private static TransactionType readType(Scanner scanner, String prompt) {
        System.out.print(prompt);
        try {
            return TransactionType.valueOf(scanner.nextLine().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Type must be INCOME or EXPENSE.");
        }
    }

    private static LocalDate readDate(Scanner scanner, String prompt) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(input);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Enter the date in yyyy-MM-dd format.");
        }
    }

    private static YearMonth readMonth(Scanner scanner, String prompt) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            return YearMonth.now();
        }
        return parseMonth(input);
    }

    private static YearMonth readMonthRequired(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return parseMonth(scanner.nextLine().trim());
    }

    private static YearMonth parseMonth(String input) {
        try {
            return YearMonth.parse(input);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Enter the month in yyyy-MM format.");
        }
    }

    private static String money(BigDecimal amount) {
        synchronized (AMOUNT) {
            return "INR " + AMOUNT.format(amount);
        }
    }
}
