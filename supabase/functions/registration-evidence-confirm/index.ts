import { createClient } from 'npm:@supabase/supabase-js@2';
import { Image } from 'npm:imagescript@1.3.0';
const json=(status:number,code:unknown)=>Response.json(typeof code==='string'?{code}:code,{status});
const uuid=(v:unknown):v is string=>typeof v==='string' && /^[0-9a-f]{8}-[0-9a-f-]{27}$/i.test(v);
// Inspect dimensions before allocating a decoded bitmap (prevents decompression bombs).
function dimensions(b:Uint8Array,mime:string):boolean {
 const d=new DataView(b.buffer,b.byteOffset,b.byteLength); let w=0,h=0;
 if(mime==='image/png') {
  if(b.length<24 || d.getUint32(0)!==0x89504e47 || d.getUint32(4)!==0x0d0a1a0a) return false;
  w=d.getUint32(16);h=d.getUint32(20);
 } else {
  if(b.length<4 || b[0]!==255 || b[1]!==216) return false;
  let i=2;
  while(i+4<b.length) {
   if(b[i++]!==255) return false;
   while(b[i]===255)i++;
   const marker=b[i++]; if(marker===0xda || marker===0xd9)break;
   const length=d.getUint16(i); if(length<2 || i+length>b.length)return false;
   if([0xc0,0xc1,0xc2].includes(marker)) {h=d.getUint16(i+3);w=d.getUint16(i+5);break;}
   i+=length;
  }
 }
 return w>0 && h>0 && w<=8192 && h<=8192 && w*h<=16000000;
}
Deno.serve(async request=>{
 if(request.method!=='POST')return json(405,'METHOD_NOT_ALLOWED');
 const url=Deno.env.get('SUPABASE_URL'),anon=Deno.env.get('SUPABASE_ANON_KEY'),service=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
 if(!url||!anon||!service)return json(503,'SERVICE_UNAVAILABLE');
 try {
 const input=await request.json();
 if(!uuid(input.evidence_id)||!uuid(input.operation_id)||!Number.isSafeInteger(input.expected_revision))return json(400,'INVALID_INPUT');
 const caller=createClient(url,anon,{global:{headers:{Authorization:request.headers.get('authorization')??''}},auth:{persistSession:false}});
 const {data:user,error:authError}=await caller.auth.getUser();
 if(authError||!user.user)return json(401,'AUTH_REQUIRED');
 const admin=createClient(url,service,{auth:{persistSession:false}});
 const {data: reservation,error}=await admin.rpc('get_registration_evidence_internal',{p_user_id:user.user.id,p_evidence_id:input.evidence_id});
 if(error||!reservation)return json(403,'FORBIDDEN');
 const {data:blob,error:downloadError}=await admin.storage.from('registration-documents').download(reservation.storage_path);
 if(downloadError||!blob)return json(400,'UPLOAD_NOT_CONFIRMED');
 if(blob.size>10485760||blob.size!==Number(reservation.byte_size))return json(413,'INVALID_IMAGE');
 const bytes=new Uint8Array(await blob.arrayBuffer());
 if(!dimensions(bytes,reservation.mime_type))return json(400,'INVALID_IMAGE');
 try {const image=await Image.decode(bytes);if(!image.width||!image.height)return json(400,'INVALID_IMAGE');}catch{return json(400,'INVALID_IMAGE');}
 const {data:result,error:confirmError}=await admin.rpc('confirm_registration_evidence_internal',{
 p_user_id:user.user.id,p_evidence_id:input.evidence_id,p_operation_id:input.operation_id,p_expected_revision:input.expected_revision});
 if(confirmError)return json(confirmError.code==='40001'?409:400,confirmError.code==='40001'?'REVISION_CONFLICT':'INVALID_STATE');
 return json(200,result);
 }catch{return json(400,'INVALID_INPUT');}
});
