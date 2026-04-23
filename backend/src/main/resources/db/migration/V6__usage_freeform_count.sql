-- V6: FREE tier freeform katsete kvoot.
-- FREE kasutaja saab vaikimisi 3 Claude-kirjutatud (või kasutaja-kirjutatud
-- Pro-feature'i prooviks) CadQuery sandbox-katset kuus. Kulum käib sama
-- tabeli (usage_monthly) peal, et ühest SELECT-st saab kogu kuu pilt.

ALTER TABLE usage_monthly
    ADD COLUMN IF NOT EXISTS freeform_count INT NOT NULL DEFAULT 0;
