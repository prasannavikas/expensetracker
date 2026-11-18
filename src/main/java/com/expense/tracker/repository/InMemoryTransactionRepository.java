package com.expense.tracker.repository;

import com.expense.tracker.model.Transaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryTransactionRepository implements TransactionRepository {
    private final Map<Long, Transaction> transactions = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public Transaction insert(Transaction transaction) {
        Transaction stored = copyWithId(transaction, nextId++);
        transactions.put(stored.getId(), stored);
        return copy(stored);
    }

    @Override
    public List<Transaction> findAll() {
        List<Transaction> result = new ArrayList<>();
        transactions.values().forEach(transaction -> result.add(copy(transaction)));
        return List.copyOf(result);
    }

    @Override
    public Optional<Transaction> findById(long id) {
        return Optional.ofNullable(transactions.get(id)).map(InMemoryTransactionRepository::copy);
    }

    @Override
    public Transaction update(Transaction transaction) {
        if (!transactions.containsKey(transaction.getId())) {
            throw new IllegalArgumentException("Cannot update a transaction that does not exist.");
        }
        transactions.put(transaction.getId(), copy(transaction));
        return copy(transaction);
    }

    @Override
    public boolean deleteById(long id) {
        return transactions.remove(id) != null;
    }

    private static Transaction copyWithId(Transaction transaction, long id) {
        return new Transaction(id, transaction.getTitle(), transaction.getAmount(),
                transaction.getType(), transaction.getCategory(),
                transaction.getDescription(), transaction.getDate());
    }

    private static Transaction copy(Transaction transaction) {
        return copyWithId(transaction, transaction.getId());
    }
}
