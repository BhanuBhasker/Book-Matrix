-- Reference data: the fixed list of event categories.
-- INSERT IGNORE skips rows that already exist (unique name/slug), so this
-- is safe to run on every start-up. This is NOT sample event data —
-- events, venues and users are always created through the application.
INSERT IGNORE INTO categories (name, slug, description) VALUES
    ('Music',      'music',      'Concerts, gigs and live sets');
INSERT IGNORE INTO categories (name, slug, description) VALUES
    ('Theatre',    'theatre',    'Plays, musicals and stage performances');
INSERT IGNORE INTO categories (name, slug, description) VALUES
    ('Comedy',     'comedy',     'Stand-up, improv and sketch shows');
INSERT IGNORE INTO categories (name, slug, description) VALUES
    ('Sports',     'sports',     'Matches, races and tournaments');
INSERT IGNORE INTO categories (name, slug, description) VALUES
    ('Conference', 'conference', 'Talks, summits and meetups');
INSERT IGNORE INTO categories (name, slug, description) VALUES
    ('Workshop',   'workshop',   'Hands-on classes and courses');
INSERT IGNORE INTO categories (name, slug, description) VALUES
    ('Exhibition', 'exhibition', 'Art shows, expos and fairs');
INSERT IGNORE INTO categories (name, slug, description) VALUES
    ('Festival',   'festival',   'Multi-day festivals and celebrations');
