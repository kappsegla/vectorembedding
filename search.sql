-- Skapa tabellen i public schema
CREATE TABLE public.document
(
    id      SERIAL PRIMARY KEY,
    title   TEXT NOT NULL,
    content TEXT NOT NULL
);

-- Lägg till dokument
INSERT INTO document (title, content)
VALUES ('Database Connection Pooling Guide',
        'Database connection pooling improves application performance. A pool maintains reusable connections. Configure pool size based on workload.'),
       ('PostgreSQL Authentication Setup',
        'Set up PostgreSQL database authentication methods. Configure pg_hba.conf for password, certificate, and LDAP authentication.'),
       ('Generic Blog Post',
        'Database database database. Learn about database. Database is important. Database database database. More database info.'),
       ('EXPLAIN ANALYZE Quick Tip',
        'Use EXPLAIN ANALYZE to find slow PostgreSQL queries. Shows execution plan and actual timing.'),
       ('Complete PostgreSQL Query Tuning Guide',
        'This comprehensive PostgreSQL guide covers query tuning. PostgreSQL query performance depends on proper use of EXPLAIN and EXPLAIN ANALYZE. Run EXPLAIN ANALYZE on slow queries. The EXPLAIN output shows decisions...');

-- LIKE
SELECT *
FROM document
WHERE content LIKE '%connection%';

-- Lägg till search_vector
ALTER TABLE public.document
    ADD COLUMN search_vector tsvector
        GENERATED ALWAYS AS (
            setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
            setweight(to_tsvector('english', coalesce(content, '')), 'B')
            ) STORED;

-- Skapa index så vi inte behöver scanna hela tabellen
CREATE INDEX idx_document_search ON public.document USING GIN (search_vector);

-- Utför sökning med rankning
SELECT id,
       title,
       ts_rank(search_vector, query) as score
FROM public.document,
     plainto_tsquery('english', 'connection pool') query
WHERE search_vector @@ query
ORDER BY score DESC
LIMIT 3;

-- https://docs.paradedb.com/documentation/indexing/create-index

-- BM25 kolla version
SELECT extversion
FROM pg_extension
WHERE extname = 'pg_search';

-- Aktivera tillägg (om du inte redan gjort det)
CREATE EXTENSION IF NOT EXISTS pg_search;

-- Skapa BM25-indexet direkt på tabellen
CREATE INDEX idx_document_search ON public.document
    USING bm25 (id, title, content)
    WITH (key_field = 'id');

-- Med det nya indexet använder du operatorn @@@ för att söka:
-- Sök i kolumnen "content"
SELECT title, content
FROM document
WHERE document @@@ 'content:PostgreSQL';

-- Sök i både "title" och "content" (notera versaler för OR)
SELECT title, content
FROM document
WHERE document @@@ 'title:PostgreSQL OR content:PostgreSQL';

-- Sök efter termen i alla fält som ingår i indexet
SELECT title, content
FROM document
WHERE document @@@ 'PostgreSQL';
-- Detta fungerar inte. Vet inte kolumn/kolumner som ska sökas i

-- Verifiera att indexet används
EXPLAIN
SELECT title, content
FROM document
WHERE document @@@ 'content:PostgreSQL';

-- Du kan se hur bra en matchning är. Du kan hämta ut poängen så här:
SELECT title,
       paradedb.score(id) AS search_score
FROM document
WHERE document @@@ 'content:PostgreSQL'
ORDER BY search_score DESC;

--
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE public.document
    ADD COLUMN embedding vector(768);

-- Run program to build vectors
INSERT INTO document (title, content)
VALUES ('I drink Java coffee every morning',
        'Every morning I brew a cup of strong Java coffee. The aroma of freshly roasted coffee helps me wake up and start the day with energy.');

INSERT INTO document (title, content)
VALUES ('Java is an object-oriented language',
        'Java is a popular object-oriented programming language used for everything from backend development to Android apps. The language is known for its portability and large ecosystem.');

INSERT INTO document (title, content)
VALUES ('We traveled to Java in Indonesia',
        'Last year we traveled to the island of Java in Indonesia. We visited temples, hiked through volcanic landscapes, and experienced the local culture and food.');
