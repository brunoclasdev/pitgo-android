import { createClient } from 'npm:@supabase/supabase-js@2';

// Always use the caller JWT, including when the enclosing function uses service_role.
export async function registrationAccess(request: Request, mode: 'CLIENT' | 'PROVIDER' | 'ANY' = 'CLIENT'): Promise<Response | null> {
  try {
  const url = Deno.env.get('SUPABASE_URL'), key = Deno.env.get('SUPABASE_ANON_KEY');
  if (!url || !key) return Response.json({code: 'SERVICE_UNAVAILABLE'}, {status:503});
  const caller = createClient(url,key,{global:{headers:{Authorization:request.headers.get('authorization') ?? ''}},auth:{persistSession:false}});
  const {data: user,error: authError} = await caller.auth.getUser();
  if (authError || !user.user) return Response.json({code:'AUTH_REQUIRED'},{status:401});
  const modes = mode === 'ANY' ? ['CLIENT','PROVIDER'] : [mode];
  for (const value of modes) {
    const {data,error} = await caller.rpc('assert_my_registration_access',{p_account_type:value});
    if (!error && data === true) return null;
    if (error && error.code !== '42501') return Response.json({code:'SERVICE_UNAVAILABLE'},{status:503});
  }
  return Response.json({code:'REGISTRATION_REQUIRED'},{status:403});
  } catch { return Response.json({code:'SERVICE_UNAVAILABLE'},{status:503}); }
}
