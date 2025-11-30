-- This script runs automatically on first container startup
-- Create pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- -- Example table using pgvector
-- CREATE TABLE items (
--                        id SERIAL PRIMARY KEY,
--                        embedding VECTOR(3)  -- 3-dimensional vector
-- );
--
-- -- Insert demo data
-- INSERT INTO items (embedding) VALUES
--                                   ('[1,2,3]'),
--                                   ('[0.5,0.25,0.75]');
