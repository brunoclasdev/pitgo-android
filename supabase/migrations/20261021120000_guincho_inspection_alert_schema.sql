begin;
create table private.guincho_inspection_push_installations (
 installation_id uuid not null, environment_id text not null check(environment_id in ('dev','homol','prod')),
 user_id uuid not null references auth.users(id), binding_id uuid not null default gen_random_uuid(),
 fcm_token text not null check(char_length(fcm_token) between 1 and 4096), firebase_project_id text not null,
 updated_at timestamptz not null default now(), revoked_at timestamptz,
 primary key(installation_id,environment_id), unique(binding_id)
);
create unique index inspection_push_active_token on private.guincho_inspection_push_installations(fcm_token) where revoked_at is null;
create table private.guincho_inspection_review_alerts (
 id uuid primary key default gen_random_uuid(), client_id uuid not null references auth.users(id),
 appointment_id uuid not null references public.appointments(id), version_id uuid not null,
 environment_id text not null check(environment_id in ('dev','homol','prod')), revision bigint not null default 1,
 created_at timestamptz not null default now(), invalidated_at timestamptz,
 foreign key(version_id,appointment_id) references public.guincho_inspection_versions(id,appointment_id),
 unique(client_id,appointment_id,version_id)
);
create index inspection_review_alert_client on private.guincho_inspection_review_alerts(client_id,environment_id,created_at,id) where invalidated_at is null;
create table private.guincho_inspection_push_deliveries (
 id uuid primary key default gen_random_uuid(), alert_id uuid not null references private.guincho_inspection_review_alerts(id) on delete cascade,
 installation_id uuid not null, environment_id text not null, binding_id uuid not null, revision bigint not null,
 state text not null default 'PENDING' check(state in ('PENDING','LEASED','ACCEPTED','OBSOLETE','FAILED_CONFIGURATION')),
 attempt_count integer not null default 0, next_attempt_at timestamptz not null default now(),
 lease_token uuid, lease_until timestamptz, accepted_at timestamptz, last_error_code text,
 foreign key(installation_id,environment_id) references private.guincho_inspection_push_installations(installation_id,environment_id),
 unique(alert_id,installation_id,binding_id,revision)
);
create index inspection_push_due on private.guincho_inspection_push_deliveries(next_attempt_at) where state in ('PENDING','LEASED','FAILED_CONFIGURATION');
alter table private.guincho_inspection_push_installations enable row level security;
alter table private.guincho_inspection_review_alerts enable row level security;
alter table private.guincho_inspection_push_deliveries enable row level security;
revoke all on private.guincho_inspection_push_installations,private.guincho_inspection_review_alerts,private.guincho_inspection_push_deliveries from public,anon,authenticated;
commit;
