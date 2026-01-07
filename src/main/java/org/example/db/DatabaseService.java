package org.example.db;

import com.pgvector.PGvector;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseService {

    // --- Configuration ---
    // TODO: Externalize these connection details into a properties file or environment variables.
//    private static final String DB_URL = "jdbc:postgresql://localhost:5432/vectordb";
//    private static final String DB_USER = "demo_user";
//    private static final String DB_PASSWORD = "demo_pass";
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "mysecretpassword";

    /**
     * Establishes a connection to the PostgreSQL database.
     * It also adds support for the pgvector type.
     *
     * @return A Connection object to the database.
     * @throws SQLException if a database access error occurs.
     */
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        // Add the pgvector type to the connection
        PGvector.registerTypes(conn);
        return conn;
    }
}
