begin;
-- Dashboard staff is an auth identity and need not have a mobile profile.
alter table public.appointment_disputes drop constraint appointment_disputes_resolved_by_fkey;
alter table public.appointment_disputes add constraint appointment_disputes_resolved_by_fkey
foreign key (resolved_by) references auth.users(id);
commit;
