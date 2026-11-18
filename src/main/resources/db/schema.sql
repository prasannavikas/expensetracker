CREATE DATABASE IF NOT EXISTS expense_tracker;
USE expense_tracker;

CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    transaction_type ENUM('INCOME', 'EXPENSE') NOT NULL,
    category ENUM(
        'FOOD',
        'TRAVEL',
        'SHOPPING',
        'EDUCATION',
        'BILLS',
        'ENTERTAINMENT',
        'HEALTH',
        'OTHER'
    ) NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    transaction_date DATE NOT NULL,
    CONSTRAINT chk_transaction_category CHECK (
        (transaction_type = 'INCOME' AND category IS NULL)
        OR (transaction_type = 'EXPENSE' AND category IS NOT NULL)
    ),
    CONSTRAINT chk_transaction_amount CHECK (amount > 0)
);
