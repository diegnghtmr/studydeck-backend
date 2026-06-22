-- V6: User-chosen deck appearance.
-- Both nullable: when null, the UI derives a stable icon/color from the deck id (hash fallback),
-- so existing decks keep working with no backfill required.

ALTER TABLE deck ADD COLUMN icon  TEXT;
ALTER TABLE deck ADD COLUMN color TEXT;

COMMENT ON COLUMN deck.icon  IS 'User-chosen glyph name (e.g. book, beaker). Null = id-derived default.';
COMMENT ON COLUMN deck.color IS 'User-chosen accent color as #rrggbb hex. Null = id-derived default.';
