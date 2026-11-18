# Personal Expense Tracker

A Java 17 expense tracker with a browser interface, monthly reports, and optional MySQL storage.

## Requirements

- JDK 17 or newer
- Maven 3.9 or newer
- MySQL 8.0 or newer (only needed for persistent storage)

## Run in Chrome or another browser

From the project directory:

```powershell
mvn compile exec:java
```

Open [http://127.0.0.1:8080](http://127.0.0.1:8080) in Chrome. The Java server listens only on this computer. Stop it with `Ctrl+C` in the terminal. Without database settings, the app uses an in-memory repository, so records are cleared when the server exits.

The previous console interface is still available:

```powershell
mvn compile exec:java -Dexec.mainClass=com.expense.tracker.Main
```

## Enable MySQL storage

1. Run `src/main/resources/db/schema.sql` in MySQL. This creates the `expense_tracker` database and `transactions` table.
2. Set the database environment variables in PowerShell:

```powershell
$env:EXPENSE_DB_URL = "jdbc:mysql://localhost:3306/expense_tracker"
$env:EXPENSE_DB_USER = "your_mysql_user"
$env:EXPENSE_DB_PASSWORD = "your_mysql_password"
mvn compile exec:java
```

The browser application selects MySQL storage when `EXPENSE_DB_URL` is set. Do not commit credentials; use environment variables or your IDE's run configuration. Remove `EXPENSE_DB_URL` to use in-memory storage again.

To use a different local web port, set `EXPENSE_WEB_PORT` before starting the application.

## Menu

The browser interface supports adding, editing, filtering, and deleting income and expenses; viewing category totals and financial reports; comparing spending between months; and searching transactions by their IDs through the API. Dates use the browser's date picker. Money is displayed in INR with Indian digit grouping.

Expenses require a category. Income does not use an expense category. Amounts must be positive.

## Project structure

```text
src/main/java/com/expense/tracker/
├── Main.java
├── WebMain.java
├── database/       # JDBC connection configuration
├── enums/          # Transaction types and expense categories
├── exception/      # Not-found and storage errors
├── model/          # Transaction data model
├── repository/     # In-memory and JDBC data access
├── reports/        # Financial report calculations
├── service/        # Transaction operations and monthly calculations
└── web/            # Local HTTP server and JSON API
src/main/resources/db/schema.sql
src/main/resources/web/index.html
```

The service depends on a repository interface, so the browser and console work with either in-memory or JDBC storage. JDBC statements use `PreparedStatement`; monthly grouping and totals use Java streams and `BigDecimal`.

Run the automated service and reporting tests with:

```powershell
mvn test
```
