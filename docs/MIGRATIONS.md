# Flyway Migration Notes

## V6 Renumbering (2026-04-23)

The original `V6__usage_freeform_count.sql` was renumbered to `V8__usage_freeform_count.sql`
because the enterprise-improvements patches introduced `V6__pgvector_and_object_storage.sql`.

### Production Deploy Risks

> **Warning**: V6 migration was locally a duplicate, renamed to V8.
> Production deploy requires attention if the old V6 was already applied.

If the production database already ran the original `V6__usage_freeform_count.sql`:

1. Flyway will fail with **"Validate failed: Migration checksum mismatch for V6"**
   because V6 content changed (now it's pgvector + object storage).

2. Before deploying, run Flyway repair to fix the history table:
   ```bash
   docker exec <backend-container> java -jar /app/app.jar --spring.flyway.repair-enabled=true
   ```
   Or connect to the database directly:
   ```sql
   -- Option A: Delete the old V6 record so Flyway re-applies it
   DELETE FROM flyway_schema_history WHERE version = '6';

   -- Option B: Manually apply the new V6 migration content,
   -- then update the checksum
   UPDATE flyway_schema_history
   SET checksum = <new-checksum>, description = 'pgvector and object storage'
   WHERE version = '6';
   ```

3. Set environment variable for first deploy:
   ```
   SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
   ```

### Current Migration Order

| Version | Description |
|---------|-------------|
| V1 | init (users, usage_monthly) |
| V2 | designs |
| V3 | gallery, versions, orders |
| V4 | printflow |
| V5 | ai_superpowers |
| V6 | pgvector extension + embeddings + S3 columns + audit_log |
| V7 | refresh_tokens and feature_flags |
| V8 | usage freeform_count (renamed from V6) |
