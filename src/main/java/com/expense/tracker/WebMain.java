package com.expense.tracker;

import com.expense.tracker.database.DBConnection;
import com.expense.tracker.repository.InMemoryTransactionRepository;
import com.expense.tracker.repository.JdbcTransactionRepository;
import com.expense.tracker.repository.TransactionRepository;
import com.expense.tracker.service.ExpenseService;
import com.expense.tracker.web.ExpenseWebServer;

public final class WebMain {
    private WebMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("EXPENSE_WEB_PORT", "8080"));
        TransactionRepository repository = DBConnection.isConfigured()
                ? new JdbcTransactionRepository()
                : new InMemoryTransactionRepository();

        ExpenseWebServer server = new ExpenseWebServer(new ExpenseService(repository), port);
        server.start();
    }
}
