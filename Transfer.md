# Database Concurrency Strategies for Money Transfers

Choosing the right locking strategy depends on your "contention" (how often two people touch the same account).

## 1. Pessimistic Locking (FOR UPDATE)
**Best for:** Financial systems where data integrity is the #1 priority and accounts are frequently accessed.
- **Pros:** Guarantees consistency; simple logic.
- **Cons:** Holds database connections open longer; can cause deadlocks if IDs aren't sorted.
- **Postgres Tip:** Use `FOR NO KEY UPDATE` to allow concurrent indexing operations.

## 2. Optimistic Locking (Version Column)
**Best for:** High-scale systems (like social media "likes" or inventory) where conflicts are rare.
- **Pros:** No database locks held; very fast if no conflict occurs.
- **Cons:** Requires a `version` or `timestamp` column; if a conflict happens, the application must handle a retry.

## 3. Atomic Single-Statement (Postgres CTE)
**Best for:** Performance-critical microservices.
- **Pros:** Single network roundtrip; logic is handled entirely by the DB optimizer.
- **Cons:** Business logic is moved into SQL strings, making it harder to test in pure Java unit tests.

## 4. Constraint-Based (The Safety Net)
**Always do this:** Regardless of the approach above, add a database constraint:
`ALTER TABLE accounts ADD CONSTRAINT no_negatives CHECK (balance >= 0);`