package com.expense.tracker.repository;

import com.expense.tracker.model.Transaction;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    Transaction insert(Transaction transaction);

    List<Transaction> findAll();

    Optional<Transaction> findById(long id);

    Transaction update(Transaction transaction);

    boolean deleteById(long id);
}
