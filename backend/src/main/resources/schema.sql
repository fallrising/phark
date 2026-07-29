CREATE TABLE IF NOT EXISTS posts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    author TEXT NOT NULL,
    content TEXT NOT NULL,
    channel TEXT NOT NULL CHECK (channel IN ('home', 'tech', 'ops')),
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

DROP INDEX IF EXISTS idx_posts_channel;
DROP INDEX IF EXISTS idx_posts_created_at;

CREATE INDEX IF NOT EXISTS idx_posts_timeline ON posts(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_posts_channel_timeline ON posts(channel, created_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS replies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    post_id INTEGER NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_replies_post_timeline
ON replies(post_id, created_at ASC, id ASC);
