import { cancellationResponse } from '../_shared/payment-cancellation.ts';
Deno.serve(req => cancellationResponse(req, false));
