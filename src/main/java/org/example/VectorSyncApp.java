package org.example;

import com.pgvector.PGvector;
import org.example.db.DatabaseService;
import org.example.embedding.LMStudioClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class VectorSyncApp {

    private final LMStudioClient embeddingClient = new LMStudioClient();

    public static void main(String[] args) {
        VectorSyncApp app = new VectorSyncApp();
        try {
            // Steg 1: Synka saknade embeddings
            app.syncEmbeddings();

            // Steg 2: Demonstration av sökningar
            String[] queries = {
                    "error PG-1234",
                    "why is my database slow",
                    "fix connection timeout",
                    "tropical vacation",
                    "coding",
                    "thirsty"
            };

            for (String query : queries) {
                // Välj vilken sökmetod som ska köras genom att avkommentera:
                // app.standardTextSearch(query);
                // app.bm25Search(query);
                // app.vectorSearch(query);
                app.hybridSearch(query);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void syncEmbeddings() throws Exception {
        String selectSql = "SELECT id, content FROM document WHERE embedding IS NULL";
        String updateSql = "UPDATE document SET embedding = ? WHERE id = ?";

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {

            ResultSet rs = selectStmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String content = rs.getString("content");

                System.out.println("Generating embedding for ID: " + id);
                float[] vector = embeddingClient.getEmbedding(content);

                updateStmt.setObject(1, new PGvector(vector));
                updateStmt.setInt(2, id);
                updateStmt.executeUpdate();
            }
            System.out.println("Sync complete.");
        }
    }

    public void standardTextSearch(String queryText) throws Exception {
        String stdQuery = queryText.trim().replaceAll("\\s+", " | ");
        String sql = """
                SELECT title, ts_rank(search_vector, to_tsquery('english', ?)) as score
                FROM document
                WHERE search_vector @@ to_tsquery('english', ?)
                ORDER BY score DESC
                LIMIT 3;
                """;

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, stdQuery);
            stmt.setString(2, stdQuery);

            System.out.println("\n--- Standard Text Search Results: [" + queryText + "] ---");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                System.out.printf(" - %-35s | Score: %.4f%n", rs.getString("title"), rs.getDouble("score"));
            }
        }
    }

    public void bm25Search(String queryText) throws Exception {
        String bm25Query = "content:(" + queryText.trim().replaceAll("\\s+", " OR ") + ")";
        String sql = """
                SELECT title, paradedb.score(id) as score
                FROM document
                WHERE document @@@ ?
                ORDER BY score DESC
                LIMIT 3;
                """;

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bm25Query);

            System.out.println("\n--- BM25 Search Results: [" + queryText + "] ---");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                System.out.printf(" - %-35s | Score: %.4f%n", rs.getString("title"), rs.getDouble("score"));
            }
        }
    }

    public void vectorSearch(String queryText) throws Exception {
        float[] queryVector = embeddingClient.getEmbedding(queryText);
        PGvector pgVec = new PGvector(queryVector);
        String sql = """
                SELECT title, (1 - (embedding <=> ?)) as score
                FROM document
                WHERE embedding <=> ? < 0.8
                ORDER BY score DESC
                LIMIT 3;
                """;

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, pgVec);
            stmt.setObject(2, pgVec);

            System.out.println("\n--- Vector Search Results: [" + queryText + "] ---");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                System.out.printf(" - %-35s | Score: %.4f%n", rs.getString("title"), rs.getDouble("score"));
            }
        }
    }

    public void hybridSearch(String queryText) throws Exception {
        float[] queryVector = embeddingClient.getEmbedding(queryText);
        PGvector pgVec = new PGvector(queryVector);

        // 1. Skapa en "snäll" söksträng för Postgres (Std) med OR-logik
        // "why is slow" -> "why | is | slow"
        String stdQuery = queryText.trim().replaceAll("\\s+", " | ");

        // 2. Skapa söksträng för ParadeDB (BM25)
        String bm25Query = "content:(" + queryText.trim().replaceAll("\\s+", " OR ") + ")";

        String sql = """
                SELECT 
                    title, 
                    -- Nu använder vi to_tsquery med OR-strängen
                    ts_rank(search_vector, to_tsquery('english', ?)) as standard_score,
                    paradedb.score(id) as bm25, 
                    (1 - (embedding <=> ?)) as vector,
                    (
                        ts_rank(search_vector, to_tsquery('english', ?)) + 
                        paradedb.score(id) + 
                        (1 - (embedding <=> ?)) * 2
                    ) as hybrid_score
                FROM document
                WHERE document @@@ ? 
                   -- Samma här: to_tsquery för WHERE-villkoret
                   OR search_vector @@ to_tsquery('english', ?)
                   OR embedding <=> ? < 0.8
                ORDER BY hybrid_score DESC
                LIMIT 3;
                """;

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Mappa parametrarna
            stmt.setString(1, stdQuery);    // Std (SELECT) - Använd OR-strängen!
            stmt.setObject(2, pgVec);       // Vector (SELECT)
            stmt.setString(3, stdQuery);    // Std (Hybrid calc) - Använd OR-strängen!
            stmt.setObject(4, pgVec);       // Vector (Hybrid calc)
            stmt.setString(5, bm25Query);   // BM25 (WHERE)
            stmt.setString(6, stdQuery);    // Std (WHERE) - Använd OR-strängen!
            stmt.setObject(7, pgVec);       // Vector (WHERE)

            System.out.println("\n--- Triple Hybrid Search Results: [" + queryText + "] ---");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                System.out.printf(" - %-35s | Hybrid: %.4f (Std: %.3f, BM25: %.3f, Vec: %.4f)%n",
                        rs.getString("title"),
                        rs.getDouble("hybrid_score"),
                        rs.getDouble("standard_score"),
                        rs.getDouble("bm25"),
                        rs.getDouble("vector"));
            }
        }
    }
}
