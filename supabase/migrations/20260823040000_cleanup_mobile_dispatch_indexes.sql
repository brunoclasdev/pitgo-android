-- The appointment tracking migration used the older active-provider predicate
-- (ACCEPTED/IN_PROGRESS). Dispatch also reserves PENDING providers, so keep only the newer index.
drop index if exists public.appointments_one_active_provider;
notify pgrst,'reload schema';
