-- specs/001-pittec-client-dashboard: additive updated_at column on public.profiles.
--
-- public.profiles predates the *_created_at/updated_at convention used by providers/appointments/
-- payments and has no updated_at column. The dashboard's optimistic-lock check (FR-018,
-- research.md §9) needs one to detect concurrent edits to a Cliente's profile. Additive and
-- backward-compatible: existing rows default to now() at migration time, existing app versions
-- never read or write this column, so nothing about them changes (constitution §3.1).

alter table public.profiles
  add column if not exists updated_at timestamptz not null default now();
create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function private.set_updated_at();
