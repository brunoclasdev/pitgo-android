create table private.registration_cases(
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id),
 account_type text not null check(account_type in ('CLIENT','PROVIDER')),
 verification_required boolean not null default true, legacy_exempt_at timestamptz,
 status text default 'INCOMPLETE' check(status in ('INCOMPLETE','IN_REVIEW','CORRECTION_REQUESTED','APPROVED')),
 revision bigint not null default 1, current_submission_id uuid, approved_submission_id uuid,
 activated_at timestamptz, created_at timestamptz not null default now(),
 unique(user_id,account_type), foreign key(user_id,account_type) references public.account_roles(user_id,account_type),
 check((verification_required and legacy_exempt_at is null and status is not null) or
       (not verification_required and legacy_exempt_at is not null and status is null)),
 check(status is distinct from 'APPROVED' or (approved_submission_id is not null and activated_at is not null))
);
create table private.registration_submissions(
 id uuid primary key default gen_random_uuid(), case_id uuid not null references private.registration_cases(id),
 version integer not null, state text not null default 'DRAFT' check(state in ('DRAFT','SUBMITTED','CORRECTION_REQUESTED','APPROVED')),
 snapshot jsonb not null default '{}', submitted_at timestamptz, decided_at timestamptz,
 created_at timestamptz not null default now(), unique(case_id,version)
);
alter table private.registration_cases add foreign key(current_submission_id) references private.registration_submissions(id);
alter table private.registration_cases add foreign key(approved_submission_id) references private.registration_submissions(id);
create table private.registration_evidence(
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id),
 case_id uuid not null references private.registration_cases(id), origin_submission_id uuid not null references private.registration_submissions(id),
 kind text not null check(kind in ('CNH_FRONT','CNH_BACK','SELFIE','SELFIE_WITH_DOCUMENT','VEHICLE_DOCUMENT')),
 vehicle_reference_id uuid, storage_path text not null unique,
 mime_type text not null check(mime_type in ('image/jpeg','image/png')),
 byte_size bigint not null check(byte_size between 1 and 10485760),
 upload_state text not null default 'RESERVED' check(upload_state in ('RESERVED','RECEIVED')),
 created_at timestamptz not null default now(), confirmed_at timestamptz,
 check((kind='VEHICLE_DOCUMENT')=(vehicle_reference_id is not null))
);
create table private.registration_submission_evidence(
 submission_id uuid not null references private.registration_submissions(id),
 evidence_id uuid not null references private.registration_evidence(id), kind text not null,
 vehicle_reference_id uuid, primary key(submission_id,evidence_id),
 check((kind='VEHICLE_DOCUMENT')=(vehicle_reference_id is not null))
);
create unique index registration_personal_slot on private.registration_submission_evidence(submission_id,kind) where vehicle_reference_id is null;
create unique index registration_vehicle_slot on private.registration_submission_evidence(submission_id,vehicle_reference_id) where vehicle_reference_id is not null;
create table private.registration_reviews(
 id uuid primary key default gen_random_uuid(), submission_id uuid not null unique references private.registration_submissions(id),
 reviewer_user_id uuid not null references auth.users(id), result text not null check(result in ('APPROVED','CORRECTION_REQUESTED')),
 checked_evidence_ids uuid[] not null, correction_items jsonb not null default '[]', reason text,
 decided_at timestamptz not null default now(), check(result <> 'CORRECTION_REQUESTED' or (length(btrim(reason))>0 and jsonb_array_length(correction_items)>0))
);
create table private.registration_operations(
 actor_id uuid not null, operation_id uuid not null, command text not null, payload jsonb not null,
 result jsonb not null, primary key(actor_id,operation_id)
);
create index registration_queue on private.registration_cases(status,account_type,created_at,id);
create index registration_versions on private.registration_submissions(case_id,version desc);
do $$ declare t text; begin
 for t in select tablename from pg_tables where schemaname='private' and tablename like 'registration_%' loop
 execute format('alter table private.%I enable row level security',t);
 execute format('revoke all on private.%I from public, anon, authenticated',t);
 end loop;
end $$;
