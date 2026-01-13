package org.example;

import org.example.db.DatabaseService;

import java.math.BigDecimal;
import java.sql.*;

public class OptimisticTransfer {

    public static void main(String[] args) {
        try {
            setupDatabase();

            System.out.println("Initial state:");
            printAccounts();

            // Transfer 100.00 from account 1 to account 2
            transfer(1, 2, new BigDecimal("100.00"));

            System.out.println("\nAfter transfer of 100.00 from 1 to 2:");
            printAccounts();

            // Try to transfer more than available
            System.out.println("\nAttempting to transfer 1000.00 from 1 to 2 (should fail):");
            try {
                transfer(1, 2, new BigDecimal("1000.00"));
            } catch (SQLException e) {
                System.out.println("Expected failure: " + e.getMessage());
            }
            printAccounts();

            // Transfer from 2 to 1 (testing ID ordering for locking)
            System.out.println("\nTransferring 50.00 from 2 to 1:");
            transfer(2, 1, new BigDecimal("50.00"));
            printAccounts();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void setupDatabase() throws SQLException {
        try (Connection conn = DatabaseService.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS accounts");
            stmt.execute("CREATE TABLE accounts (id SERIAL PRIMARY KEY, name VARCHAR(100), balance DECIMAL(19, 4), version INT DEFAULT 0)");
            stmt.execute("INSERT INTO accounts (id, name, balance, version) VALUES (1, 'Alice', 500.00, 0)");
            stmt.execute("INSERT INTO accounts (id, name, balance, version) VALUES (2, 'Bob', 500.00, 0)");

            System.out.println("Database initialized with accounts Alice (1) and Bob (2) each having 500.00");
        }
    }

    private static class AccountState {
        BigDecimal balance;
        int version;

        AccountState(BigDecimal balance, int version) {
            this.balance = balance;
            this.version = version;
        }
    }

    private static AccountState getAccountState(Connection conn, int id) throws SQLException {
        String sql = "SELECT balance, version FROM accounts WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new AccountState(rs.getBigDecimal("balance"), rs.getInt("version"));
                } else {
                    throw new SQLException("Account not found: " + id);
                }
            }
        }
    }

    private static void printAccounts() throws SQLException {
        try (Connection conn = DatabaseService.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name, balance FROM accounts ORDER BY id")) {

            while (rs.next()) {
                System.out.printf("ID: %d, Name: %s, Balance: %.2f%n",
                        rs.getInt("id"), rs.getString("name"), rs.getBigDecimal("balance"));
            }
        }
    }

    public static void transfer(int fromId, int toId, BigDecimal amount) throws SQLException {
        try (Connection conn = DatabaseService.getConnection()) {
            // We don't necessarily need a long transaction here,
            // but we use one to ensure both updates happen together.
            conn.setAutoCommit(false);
            try {
                // 1. Read values WITHOUT locking
                AccountState from = getAccountState(conn, fromId);
                AccountState to = getAccountState(conn, toId);

                if (from.balance.compareTo(amount) < 0) throw new SQLException("Transfer failed (Funds or ID)");

                // 2. Attempt update ONLY if version is still the same
                // UPDATE accounts SET balance = ?, version = version + 1 WHERE id = ? AND version = ?
                boolean successFrom = tryUpdate(conn, fromId, from.balance.subtract(amount), from.version);
                boolean successTo = tryUpdate(conn, toId, to.balance.add(amount), to.version);

                if (!successFrom || !successTo) {
                    // Someone else changed the data!
                    throw new SQLException("Concurrent modification detected. Please retry.");
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static boolean tryUpdate(Connection conn, int id, BigDecimal newBal, int oldVersion) throws SQLException {
        String sql = "UPDATE accounts SET balance = ?, version = version + 1 WHERE id = ? AND version = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBigDecimal(1, newBal);
            pstmt.setInt(2, id);
            pstmt.setInt(3, oldVersion);
            return pstmt.executeUpdate() > 0; // Returns false if version changed
        }
    }
}