ALTER TABLE feeds ADD COLUMN filter_keywords TEXT NULL;
ALTER TABLE articles ADD COLUMN is_filtered BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_articles_feed_filtered_pub ON articles (feed_id, is_filtered, published_at DESC);
CREATE INDEX idx_articles_filtered_pub ON articles (is_filtered, published_at DESC);
