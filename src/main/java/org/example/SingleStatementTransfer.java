package org.example;

import org.example.db.DatabaseService;

import java.math.BigDecimal;
import java.sql.*;

public class SingleStatementTransfer {

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
        String sql =
                "WITH withdrawal AS (" +
                        "  UPDATE accounts SET balance = balance - ? " +
                        "  WHERE id = ? AND balance >= ? RETURNING id" +
                        ") " +
                        "UPDATE accounts SET balance = balance + ? " +
                        "WHERE id = ? AND EXISTS (SELECT 1 FROM withdrawal)";

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBigDecimal(1, amount);
            pstmt.setInt(2, fromId);
            pstmt.setBigDecimal(3, amount);
            pstmt.setBigDecimal(4, amount);
            pstmt.setInt(5, toId);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected < 1) throw new SQLException("Transfer failed (Funds or ID)");
        }
    }
}