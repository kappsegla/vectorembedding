SELECT extversion
FROM pg_extension
WHERE extname = 'pg_search';
SELECT *
FROM paradedb.version_info();

SELECT extversion
FROM pg_extension
WHERE extname = 'vector';

-- Aktivera tilläggen (om du inte redan gjort det)
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_search;

-- Skapa tabellen i public schema
CREATE TABLE public.document (
                                  id SERIAL PRIMARY KEY,
                                  title TEXT NOT NULL,
                                  content TEXT NOT NULL,
                                  embedding vector(768)
);

-- Skapa BM25-indexet direkt på tabellen
CREATE INDEX idx_document_search ON public.document
    USING bm25 (id, title, content)
    WITH (key_field = 'id');

-- Verifiera att indexet finns
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'document' AND indexname = 'idx_document_search';

-- Lägg till dokument
INSERT INTO document (title, content, embedding)
VALUES ('Database Connection Pooling Guide',
        'Database connection pooling improves application performance. A pool maintains reusable connections. Configure pool size based on workload.',
        NULL),
       ('PostgreSQL Authentication Setup',
        'Set up PostgreSQL database authentication methods. Configure pg_hba.conf for password, certificate, and LDAP authentication.',
        NULL),
       ('Generic Blog Post',
        'Database database database. Learn about database. Database is important. Database database database. More database info.',
        NULL),
       ('EXPLAIN ANALYZE Quick Tip',
        'Use EXPLAIN ANALYZE to find slow PostgreSQL queries. Shows execution plan and actual timing.',
        NULL),
       ('Complete PostgreSQL Query Tuning Guide',
        'This comprehensive PostgreSQL guide covers query tuning. PostgreSQL query performance depends on proper use of EXPLAIN and EXPLAIN ANALYZE. Run EXPLAIN ANALYZE on slow queries. The EXPLAIN output shows decisions...',
        NULL);

-- Reindexera vid behov
DROP INDEX idx_document_search;
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
WHERE document @@@ 'PostgreSQL'; -- Detta fungerar inte. Vet inte kolumn/kolumner som ska sökas i

-- Verifiera att indexet används
EXPLAIN SELECT title, content
FROM document
WHERE document @@@ 'content:PostgreSQL';


-- En stor fördel med BM25 är att du kan se hur bra en matchning är. Du kan hämta ut poängen så här:
SELECT
    title,
    paradedb.score(id) AS search_score
FROM document
WHERE document @@@ 'content:PostgreSQL'
ORDER BY search_score DESC;

-- Lägg till search_vector
ALTER TABLE document ADD COLUMN search_vector tsvector;
UPDATE document SET search_vector = to_tsvector('english', title || ' ' || content);
-- Skapa ett index för prestanda
CREATE INDEX idx_standard_search ON document USING gin(search_vector);

-- search_vector är motorn i PostgreSQL:s inbyggda fulltextsökning. Det är en speciell datatyp som kallas tsvector (Text Search Vector).
--
-- Istället för att databasen läser igenom varje ord i varje mening varje gång du söker (vilket är extremt långsamt), förbehandlar tsvector texten och sparar den i ett format som är optimerat för sökning.
--
-- Vad händer inuti en search_vector?
-- När du gör om en text till en tsvector händer tre viktiga saker:
--
-- Tokenisering: Texten delas upp i enskilda ord.
--
-- Normalisering (Stemming): Ord skalas ner till sin rot. "Pooling", "pools" och "pooled" blir alla bara pool. Det gör att du hittar rätt även om du inte skriver exakt rätt böjningsform.
--
-- Stop-words: Vanliga ord som "the", "is", "a" och "and" rensas bort eftersom de inte tillför något sök-värde.
--
-- Ett konkret exempel
-- Om du har titeln: "Database Connection Pooling Guide"
--
-- En vanlig textsträng ser ut så här: 'Database Connection Pooling Guide'
--
-- Men som en tsvector ser den ut ungefär så här: 'connect':2 'databas':1 'guid':4 'pool':3 (Siffrorna visar var i meningen ordet fanns, vilket hjälper Postgres att räkna ut relevans/rank).
-- SQL: Skapa en Trigger (Rekommenderas)
-- Eftersom search_vector är en fysisk kolumn måste den uppdateras varje gång du ändrar i title eller content. Istället för att göra det manuellt i Java kan du låta Postgres sköta det automatiskt med en trigger:

-- Skapa en funktion som uppdaterar sökvektorn
CREATE FUNCTION document_search_trigger() RETURNS trigger AS $$
begin
    new.search_vector :=
            setweight(to_tsvector('english', coalesce(new.title,'')), 'A') ||
            setweight(to_tsvector('english', coalesce(new.content,'')), 'B');
    return new;
end
$$ LANGUAGE plpgsql;

-- Här har jag gett title högre vikt ('A') än content ('B'), vilket gör att träffar i rubriken smäller högre än träffar i brödtexten.

-- Koppla funktionen till tabellen
CREATE TRIGGER tsvectorupdate BEFORE INSERT OR UPDATE
    ON document FOR EACH ROW EXECUTE FUNCTION document_search_trigger();

-- Uppdatera kolumnen med data från title och content
UPDATE document
SET search_vector = to_tsvector('english', title || ' ' || content);

-- Se till att framtida uppdateringar sker automatiskt (om du inte redan gjort det)
CREATE INDEX IF NOT EXISTS idx_standard_search ON document USING gin(search_vector);


-- LIKE
SELECT * FROM document WHERE content LIKE '%connection%';

