package org.example;

import org.example.db.DatabaseService;

import java.math.BigDecimal;
import java.sql.*;

public class PessimisticTransfer {

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
            stmt.execute("CREATE TABLE accounts (id SERIAL PRIMARY KEY, name VARCHAR(100), balance DECIMAL(19, 4))");
            stmt.execute("INSERT INTO accounts (id, name, balance) VALUES (1, 'Alice', 500.00)");
            stmt.execute("INSERT INTO accounts (id, name, balance) VALUES (2, 'Bob', 500.00)");

            System.out.println("Database initialized with accounts Alice (1) and Bob (2) each having 500.00");
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
            conn.setAutoCommit(false);
            try {
                // 1. Sort IDs to prevent deadlocks
                int first = Math.min(fromId, toId);
                int second = Math.max(fromId, toId);

                // 2. Lock and fetch in one step using FOR NO KEY UPDATE
                BigDecimal balanceFrom = null;
                String lockSql = "SELECT id, balance FROM accounts WHERE id IN (?, ?) ORDER BY id FOR NO KEY UPDATE";

                try (PreparedStatement pstmt = conn.prepareStatement(lockSql)) {
                    pstmt.setInt(1, first);
                    pstmt.setInt(2, second);
                    ResultSet rs = pstmt.executeQuery();

                    while (rs.next()) {
                        if (rs.getInt("id") == fromId) balanceFrom = rs.getBigDecimal("balance");
                    }
                }

                // 3. Logic check
                if (balanceFrom == null || balanceFrom.compareTo(amount) < 0) {
                    throw new SQLException("Transfer failed (Funds or ID)");
                }

                // 4. Atomic updates
                update(conn, fromId, amount.negate());
                update(conn, toId, amount);

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static void update(Connection conn, int id, BigDecimal delta) throws SQLException {
        String sql = "UPDATE accounts SET balance = balance + ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBigDecimal(1, delta);
            pstmt.setInt(2, id);
            pstmt.executeUpdate();
        }
    }
}