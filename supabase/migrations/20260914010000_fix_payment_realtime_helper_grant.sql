-- The PitTec payment Realtime policy calls this SECURITY DEFINER helper. The
-- feature migration revoked the default privileges but did not grant EXECUTE
-- back to authenticated users, making every private payment channel respond
-- with the generic Realtime "Unauthorized" error.
begin;

grant execute on function private.can_read_payment_topic(text) to authenticated;

commit;
