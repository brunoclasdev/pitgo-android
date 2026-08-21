-- The live database was never actually built from 20260819000000_create_profiles.sql (see
-- 20260821030000's removed reconciliation attempt / conversation history for the full drift
-- analysis) — it has profiles/providers/addresses with a different shape, and no `private`
-- schema. 20260821000000/010000/020000 all assume `private` already exists (as it would if
-- 20260819000000 had actually run). This migration only creates the empty namespace so those
-- files' own `create or replace function private.*` / `create table private.*` statements can
-- run; it defines nothing inside it, to avoid conflicting with what those files create.
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
