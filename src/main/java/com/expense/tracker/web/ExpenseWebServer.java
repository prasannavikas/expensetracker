package com.expense.tracker.web;

import com.expense.tracker.enums.Category;
import com.expense.tracker.enums.TransactionType;
import com.expense.tracker.exception.ExpenseNotFoundException;
import com.expense.tracker.exception.ExpenseStorageException;
import com.expense.tracker.model.Transaction;
import com.expense.tracker.reports.ExpenseReport;
import com.expense.tracker.service.ExpenseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class ExpenseWebServer {
    private static final int MAX_REQUEST_BYTES = 16_384;

    private final ExpenseService service;
    private final int port;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    public ExpenseWebServer(ExpenseService service, int port) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Web port must be between 1 and 65535.");
        }
        this.service = service;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handleRequest);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("Personal Expense Tracker is running at http://127.0.0.1:" + port);
        System.out.println("Storage: " + (System.getenv("EXPENSE_DB_URL") == null
                ? "in-memory (records are cleared when the app exits)"
                : "MySQL"));
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") && exchange.getRequestMethod().equals("GET")) {
                serveIndex(exchange);
            } else if (path.equals("/api/categories") && exchange.getRequestMethod().equals("GET")) {
                sendJson(exchange, 200, Arrays.stream(Category.values())
                        .map(Enum::name)
                        .toList());
            } else if (path.equals("/api/transactions")) {
                handleTransactions(exchange);
            } else if (path.startsWith("/api/transactions/")) {
                handleTransaction(exchange, path.substring("/api/transactions/".length()));
            } else if (path.equals("/api/reports")) {
                handleReport(exchange);
            } else if (path.equals("/api/reports/compare")) {
                handleComparison(exchange);
            } else {
                sendError(exchange, 404, "Page or API route not found.");
            }
        } catch (ExpenseNotFoundException exception) {
            sendError(exchange, 404, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (ExpenseStorageException exception) {
            System.err.println("Expense tracker storage error: " + exception.getMessage());
            sendError(exchange, 500, "Storage operation failed. Check the database configuration and logs.");
        } catch (JsonProcessingException exception) {
            sendError(exchange, 400, "Request body must be valid transaction JSON.");
        } finally {
            exchange.close();
        }
    }

    private void serveIndex(HttpExchange exchange) throws IOException {
        try (var input = ExpenseWebServer.class.getResourceAsStream("/web/index.html")) {
            if (input == null) {
                sendError(exchange, 500, "The browser interface resource is missing.");
                return;
            }
            byte[] body = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; "
                            + "connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private void handleTransactions(HttpExchange exchange) throws IOException {
        switch (exchange.getRequestMethod()) {
            case "GET" -> sendJson(exchange, 200, service.getAllTransactions().stream()
                    .map(TransactionResponse::from)
                    .toList());
            case "POST" -> {
                Transaction transaction = addTransaction(readRequest(exchange));
                sendJson(exchange, 201, TransactionResponse.from(transaction));
            }
            default -> methodNotAllowed(exchange, "GET, POST");
        }
    }

    private void handleTransaction(HttpExchange exchange, String idPath) throws IOException {
        if (!idPath.matches("[1-9][0-9]*")) {
            sendError(exchange, 404, "Transaction ID must be a positive whole number.");
            return;
        }
        long id;
        try {
            id = Long.parseLong(idPath);
        } catch (NumberFormatException exception) {
            sendError(exchange, 400, "Transaction ID is out of range.");
            return;
        }

        switch (exchange.getRequestMethod()) {
            case "GET" -> sendJson(exchange, 200,
                    TransactionResponse.from(service.findTransactionById(id)));
            case "PUT" -> {
                TransactionRequest request = readRequest(exchange);
                Transaction transaction = parseTransactionRequest(id, request);
                Transaction updated = service.updateTransaction(id, transaction.getTitle(),
                        transaction.getAmount(), transaction.getType(), transaction.getCategory(),
                        transaction.getDescription(), transaction.getDate());
                sendJson(exchange, 200, TransactionResponse.from(updated));
            }
            case "DELETE" -> {
                service.deleteTransaction(id);
                exchange.sendResponseHeaders(204, -1);
            }
            default -> methodNotAllowed(exchange, "GET, PUT, DELETE");
        }
    }

    private void handleReport(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            methodNotAllowed(exchange, "GET");
            return;
        }
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        YearMonth month = parseMonth(query.get("month"));
        ExpenseReport report = service.generateReport(month);
        Map<String, String> categoryTotals = new LinkedHashMap<>();
        report.getCategoryTotals().forEach((category, amount) ->
                categoryTotals.put(category.name(), amount.toPlainString()));
        sendJson(exchange, 200, new ReportResponse(
                report.getMonth().toString(),
                report.getTotalIncome().toPlainString(),
                report.getTotalExpenses().toPlainString(),
                report.getSavings().toPlainString(),
                categoryTotals,
                report.getHighestExpense().map(TransactionResponse::from).orElse(null),
                report.getLowestExpense().map(TransactionResponse::from).orElse(null)));
    }

    private void handleComparison(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            methodNotAllowed(exchange, "GET");
            return;
        }
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        YearMonth firstMonth = parseMonth(query.get("first"));
        YearMonth secondMonth = parseMonth(query.get("second"));
        sendJson(exchange, 200, new ComparisonResponse(
                firstMonth.toString(),
                service.getTotalExpenses(firstMonth).toPlainString(),
                secondMonth.toString(),
                service.getTotalExpenses(secondMonth).toPlainString(),
                service.compareExpenses(firstMonth, secondMonth).toPlainString()));
    }

    private TransactionRequest readRequest(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Request body is too large.");
        }
        return objectMapper.readValue(body, TransactionRequest.class);
    }

    private Transaction addTransaction(TransactionRequest request) {
        Transaction transaction = parseTransactionRequest(0, request);
        if (transaction.getType() == TransactionType.INCOME) {
            return service.addIncome(transaction.getTitle(), transaction.getAmount(),
                    transaction.getDescription(), transaction.getDate());
        }
        return service.addExpense(transaction.getTitle(), transaction.getAmount(),
                transaction.getCategory(), transaction.getDescription(), transaction.getDate());
    }

    private static Transaction parseTransactionRequest(long id, TransactionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A transaction request body is required.");
        }
        TransactionType type = parseType(request.type());
        Category category = type == TransactionType.EXPENSE ? parseCategory(request.category()) : null;
        if (type == TransactionType.INCOME && request.category() != null
                && !request.category().isBlank()) {
            throw new IllegalArgumentException("Income transactions cannot have an expense category.");
        }
        BigDecimal amount = parseAmount(request.amount());
        LocalDate date = parseDate(request.date());
        return new Transaction(id,
                required(request.title(), "title"),
                amount,
                type,
                category,
                request.description(),
                date);
    }

    private static TransactionType parseType(String value) {
        try {
            return TransactionType.valueOf(required(value, "type").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Transaction type must be INCOME or EXPENSE.");
        }
    }

    private static Category parseCategory(String value) {
        try {
            return Category.valueOf(required(value, "category").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Choose a valid expense category.");
        }
    }

    private static BigDecimal parseAmount(String value) {
        try {
            return new BigDecimal(required(value, "amount"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid number for the amount.");
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(required(value, "date"));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Enter the date in yyyy-MM-dd format.");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Transaction " + field + " is required.");
        }
        return value.trim();
    }

    private static YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A month is required in yyyy-MM format.");
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Enter the month in yyyy-MM format.");
        }
    }

    private static Map<String, String> parseQuery(String query) {
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        return Arrays.stream(query.split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        parts -> URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        parts -> parts.length == 1
                                ? ""
                                : URLDecoder.decode(parts[1], StandardCharsets.UTF_8),
                        (first, ignored) -> first));
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Map.of("error", message == null ? "Request failed." : message));
    }

    private void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        sendError(exchange, 405, "This HTTP method is not supported for this endpoint.");
    }

    private record TransactionRequest(
            String title,
            String amount,
            String type,
            String category,
            String description,
            String date) {
    }

    private record TransactionResponse(
            long id,
            String title,
            String amount,
            String type,
            String category,
            String description,
            String date) {
        private static TransactionResponse from(Transaction transaction) {
            return new TransactionResponse(
                    transaction.getId(),
                    transaction.getTitle(),
                    transaction.getAmount().toPlainString(),
                    transaction.getType().name(),
                    transaction.getCategory() == null ? null : transaction.getCategory().name(),
                    transaction.getDescription(),
                    transaction.getDate().toString());
        }
    }

    private record ReportResponse(
            String month,
            String totalIncome,
            String totalExpenses,
            String savings,
            Map<String, String> categoryTotals,
            TransactionResponse highestExpense,
            TransactionResponse lowestExpense) {
    }

    private record ComparisonResponse(
            String firstMonth,
            String firstExpenses,
            String secondMonth,
            String secondExpenses,
            String difference) {
    }
}
