CREATE TABLE links (
    id          BIGSERIAL PRIMARY KEY,
    short_code  VARCHAR(12)  NOT NULL,
    long_url    TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ,
    hit_count   BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_links_short_code UNIQUE (short_code)
);

COMMENT ON TABLE links IS 'Core table storing shortened URL mappings';
COMMENT ON COLUMN links.short_code IS 'Base62-encoded short identifier, 6-8 chars';
COMMENT ON COLUMN links.long_url   IS 'The original destination URL';
COMMENT ON COLUMN links.expires_at IS 'NULL means no expiry';
COMMENT ON COLUMN links.hit_count  IS 'Approximate redirect count; exact analytics live in click_events';
