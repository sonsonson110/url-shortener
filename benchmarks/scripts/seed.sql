-- Deterministic seed: ids 1..:rows, short_code = base62(id) (same alphabet/order as Base62.java),
-- no expiry. No randomness, so every run produces an identical table.
-- Usage: psql -v rows=10000000 -f seed.sql   (schema must already exist: app ran Flyway)
\set ON_ERROR_STOP on

CREATE FUNCTION pg_temp.base62(n bigint) RETURNS text LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
    alphabet constant text := '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
    result text := '';
    v bigint := n;
BEGIN
    IF v = 0 THEN
        RETURN '0';
    END IF;
    WHILE v > 0 LOOP
        result := substr(alphabet, (v % 62)::int + 1, 1) || result;
        v := v / 62;
    END LOOP;
    RETURN result;
END
$$;

INSERT INTO links (id, short_code, long_url, created_at)
SELECT g, pg_temp.base62(g), 'https://example.com/p/' || g, TIMESTAMPTZ '2026-01-01 00:00:00+00'
FROM generate_series(1, :rows) AS g;

-- Create traffic must not reuse seeded ids (they would collide on short_code).
SELECT setval('links_id_seq', :rows, true);

-- Visibility map + planner stats, so every run starts from the same state.
VACUUM (ANALYZE) links;
