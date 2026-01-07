-- Ensure the pgvector extension is available
CREATE EXTENSION IF NOT EXISTS vector;

-- Create the table for storing document chunks and their embeddings
CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    filename TEXT NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding VECTOR(768)
);

-- Create an index for fast cosine similarity searches
-- The 'vector_cosine_ops' is used for the '<=>' operator (cosine distance)
CREATE INDEX ON documents USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);