# PostgreSQL Full-Text Search: From Basics to Advanced Ranking

Run database with:
docker run --name my-postgres -e POSTGRES_PASSWORD=mysecretpassword -p 5432:5432 -d paradedb/paradedb:latest

## 1. Starting Simple: Creating a Document Table

Let's begin with a basic table structure for storing documents:

```sql
-- Create the table in public schema
CREATE TABLE public.document
(
    id      SERIAL PRIMARY KEY,
    title   TEXT NOT NULL,
    content TEXT NOT NULL
);
```

---

## 2. The Classic Approach: LIKE Queries

The traditional method uses pattern matching with LIKE:

```sql
SELECT * FROM document WHERE content LIKE '%connection%';
```

### Limitations of LIKE

- **No language awareness**: Won't find "run" if you search for "running"
- **Performance issues**: Cannot use standard indexes when using `%` at the beginning (`'%term'`)
- **No relevance ranking**: Results appear in database order, not by relevance

**Reference**:
- [PostgreSQL Documentation: pg_trgm](https://www.postgresql.org/docs/current/pgtrgm.html)
- [Using Trigrams to Enhance PostgreSQL Full-Text Search](https://www.slingacademy.com/)

---

## 3. Adding Full-Text Search Capabilities

### Creating a Search Vector

```sql
-- Add search_vector column
ALTER TABLE public.document
    ADD COLUMN search_vector tsvector
        GENERATED ALWAYS AS (
            setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
            setweight(to_tsvector('english', coalesce(content, '')), 'B')
            ) STORED;
```

### What's Happening Here?

- **`setweight(..., 'A')`**: Gives the title highest priority. Matches in the title weigh more than in the body text
- **`to_tsvector`**: Transforms text into "lexemes" (word stems). "The Fat Rats" becomes `'fat' & 'rat'`. Common words like "the" are skipped
- **`coalesce`**: Prevents the entire vector from becoming empty if a field is NULL

---

## 4. Indexing for Lightning-Fast Search

Create a GIN index (Generalized Inverted Index) that works like a book's index:

```sql
CREATE INDEX idx_document_search ON public.document 
    USING GIN(search_vector);
```

**Reference**: [PostgreSQL Documentation: Preferred Index Types for Text Search](https://www.postgresql.org/docs/current/textsearch-indexes.html)

---

## 5. Executing Searches with Ranking

```sql
-- Perform search with ranking
SELECT
    id,
    title,
    ts_rank(search_vector, query) as score
FROM public.document,
     plainto_tsquery('english', 'connection pool') query
WHERE search_vector @@ query
ORDER BY score DESC
LIMIT 3;
```

### Key Details

- **`@@` operator**: The "match operator" that checks if your search query (tsquery) exists within the document's vector (tsvector)
- **`plainto_tsquery`**: Removes unnecessary words (like "the", "a", "my") and places an `&` (AND) between each word
- **Normalization (the number 1)**: In `ts_rank(..., 1)`, the 1 means we divide the score by the document's length. This prevents a 50-page document from ranking higher than a short document simply because the word appears more times in total

---

## 6. Stop Words and Language Configuration

### Stop Words

PostgreSQL's stop word lists are stored in the filesystem, one per language. For English:
```
/usr/share/postgresql/17/tsearch_data/english.stop
```

### Language Adaptation (Swedish Example)

If your text is in Swedish, it's critical to change the language setting. This ensures PostgreSQL understands that "kopplingar" and "koppling" are related.

- Change `'english'` to `'swedish'` in `to_tsvector` (when creating the column)
- Change `'english'` to `'swedish'` in `plainto_tsquery` (when executing the search)

---

## 7. Understanding Text Search Transformations

### Example: Query Transformation

```sql
SELECT plainto_tsquery('english', 'The Fat Rats');
```
**Result**: `'fat' & 'rat'`

### Example: Vector Creation

```sql
SELECT to_tsvector('english', 'The quick brown fox jumps');
```
**Result**: `'brown':3 'fox':4 'jump':5 'quick':2`

### Example: Web Search Syntax

```sql
SELECT websearch_to_tsquery('english', 'quick or fox');
```

**Reference**: [PostgreSQL Full-Text Search with "websearch" Syntax](https://adamj.eu/tech/)

---

## 8. Why This Approach is Better

1. **Smart**: Understands that "rats" is the plural of "rat"
2. **Fast**: GIN index finds the right rows in milliseconds
3. **Relevant**: Thanks to `ts_rank` and weighting (A and B), the best answers appear at the top

---

## 9. Introducing BM25: Best Matching 25

BM25 is the algorithm used by modern search engines like Elasticsearch and Lucene. Now available for PostgreSQL through the `pg_textsearch` extension.
**Reference**:[What is bm25 best-matching-25 algorithm](https://www.geeksforgeeks.org/nlp/what-is-bm25-best-matching-25-algorithm/)

### ts_rank vs BM25

PostgreSQL's `ts_rank` is a relatively simple algorithm based primarily on Term Frequency (TF) — how often a word appears in a document. BM25 is more sophisticated and adds two critical factors:

---

## 10. BM25 Advantages

### 1. Inverse Document Frequency (IDF) – "Rarity Score"

- **ts_rank**: Doesn't care if a word is common or rare across your entire database. If the word "database" appears 5 times in a document, it gets a high score, even if "database" appears in all your documents
- **BM25**: Understands that "database" is common and therefore less important, while a rare word like "connection termination" is a much stronger indicator of relevance. It gives higher scores to matches on rare words

### 2. Term Frequency Saturation – "Saturation"

- **ts_rank**: Score increases linearly. If a word appears 20 times, it's seen as twice as relevant as 10 times
- **BM25**: Understands that relevance plateaus. The difference between 0 and 1 occurrence is enormous, but the difference between 80 and 81 occurrences is essentially zero

### 3. Document Length Normalization

- **ts_rank**: Optional normalization (0–2), but simplistic
- **BM25**: Built-in, mathematically tuned length normalization that prevents long documents from dominating simply because they contain more words

### 4. Indexing Model

- **ts_rank**: Requires a tsvector + GIN index
- **BM25**: Indexes raw text directly using a BM25 index (via pg_textsearch)

---

## 11. Understanding pg_textsearch and ParadeDB

### pg_textsearch (The Engine)

pg_textsearch is the extension itself for PostgreSQL. It's the technical component that gives Postgres the ability to use the BM25 algorithm. Built on Tantivy (an extremely fast search library written in Rust).

- **Purpose**: To replace Postgres's built-in tsvector and ts_rank with something as fast as Elasticsearch but living inside your database
- **Function**: Provides operators like `<@>` and functions like `to_bm25query`

### ParadeDB (The Platform)

ParadeDB is a modern "Elasticsearch replacement" built entirely on top of PostgreSQL. It's essentially a pre-packaged version of Postgres that comes pre-installed with:

- pg_textsearch (for BM25 search)
- pgvector (for AI search)
- Other optimizations for analytical queries

**Reference**: [You don't need Elasticsearch: BM25 is now in Postgres](https://tigerdata.io/)

---

## 12. Implementing BM25 in PostgreSQL

```sql
-- Enable extension (if not already done)
CREATE EXTENSION IF NOT EXISTS pg_search;

-- Create BM25 index directly on the table
CREATE INDEX idx_document_search ON public.document
    USING bm25 (id, title, content)
    WITH (key_field = 'id');
```

### Reindexing if Needed

```sql
DROP INDEX idx_document_search;
CREATE INDEX idx_document_search ON public.document
    USING bm25 (id, title, content)
    WITH (key_field = 'id');
```

### Searching with BM25

```sql
-- Search in the "content" column using the @@@ operator
SELECT title, content
FROM document
WHERE document @@@ 'content:PostgreSQL';
```

---

## 13. Beyond Keywords: Vector Search

### Classic Full-Text Search (tsvector/tsquery)

**Sees only the word — not the meaning**

Searching for "bank" returns matches for:
- "I deposited money at the bank"
- "We sat on a bank by the lake"
- "The riverbank overflowed"

All contain the word "bank", so all match.

### Vector Search (Embeddings)

**Sees the meaning — not just the word**

When searching for "bank" in the financial sense, the model will rank:
- "I deposited money at the bank" → high similarity
- "The riverbank overflowed" → low similarity
- "We sat on a bank by the lake" → low similarity

---

## 14. How Vector Search Works

Embeddings place words in a semantic space where:

- "bank" (finance) is near "account", "loan", "interest"
- "bank" (seating) is near "bench", "park", "wood"
- "bank" (riverbank) is near "shore", "river", "erosion"

This is why vector search works even when the user writes something that doesn't exist in the text — the model understands the meaning.

---

## 15. Vector Search Example: "Java"

Consider these three sentences:
- "I drink Java coffee every morning"
- "Java is an object-oriented language"
- "We traveled to Java in Indonesia"

### Search Results by Meaning

- Search: "programming language" → matches "Java is an object-oriented language"
- Search: "coffee" → matches "I drink Java coffee"
- Search: "tropical island" → matches "We traveled to Java in Indonesia"

---

## Summary

PostgreSQL offers a complete spectrum of search capabilities:

1. **LIKE queries**: Simple but limited
2. **Full-text search (tsvector)**: Fast and language-aware
3. **BM25 (pg_textsearch)**: Modern relevance ranking
4. **Vector search**: Semantic understanding

Choose the right tool for your use case, or combine them for maximum power.