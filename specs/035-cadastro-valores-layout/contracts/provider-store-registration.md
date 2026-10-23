# Provider and Store Registration Contract

## Provider kind

The signup flow carries one required kind: `PITTEC`, `GUINCHO`, or `STORE`. Keep the auth profile's existing provider account type. The registration API maps the selected kind to operational mode as needed and persists both concepts separately. Switching choices in the UI resets mode-specific form data before submission.

For new registrations, map PITTEC to operational `PITTEC`, GUINCHO to `MOBILE`, and STORE to `FIXED` where the legacy mode remains required. The commercial kind is authoritative for signup/catalog behavior; `location_mode` continues to serve its existing dispatch and location purposes. Do not infer STORE for existing FIXED providers.

## Address

Registration sends the typed address text as canonical business data. The server geocodes that text to fill discovery coordinates and returns either success with the stored text and derived coordinate status or an actionable retryable address-resolution error. No map selection, device-location proof token or replacement address text is part of provider onboarding.

## GUINCHO

Registration includes at least one vehicle (`plate`, `make`, `model`, `color`). Normalize the plate before duplicate detection. The provider and vehicle creation commit atomically; missing or duplicate plate data returns a field-level validation error. Provider-entered price and duration are omitted from the new guincho registration form; legacy database columns remain untouched for compatibility.

## PITTEC

Registration keeps the provider's service capability/catalog selection. It no longer accepts provider-controlled price or duration as the customer tariff. New customer values are resolved from the central per-service PitTec configuration.

## STORE

Registration collects business details and typed address, and creates a provider of kind STORE without requiring service offers. Owner product operations accept name, description, price and an optional uploaded image path. Product writes are scoped to the authenticated owning store. Customers can read active store products as a catalog; this contract does not provide purchase or stock operations.

## Access and errors

All owner operations validate authenticated provider ownership in the database/RLS. Product images are optional; an upload failure does not discard already entered product details and can be retried. Return stable errors for invalid kind, geocode unavailable, missing vehicle, duplicate plate, required product field, invalid price and owner mismatch.

## Implemented store interfaces

- `create_store_registration(p_provider jsonb, p_address jsonb, p_offers jsonb default '[]')` reuses the provider business/address contract with STORE identity and no service offers.
- `list_stores()` and `list_store_products(p_store_id uuid)` provide the active catalog; the owning store can also read its inactive products.
- `save_store_product(p_product jsonb)` accepts `id`, `name`, `description`, `price`, optional `image_path`, and `active`; owner identity comes from the authenticated provider, never a supplied owner ID.
- `delete_store_product(p_product_id uuid)` requires store ownership.
- Optional product photos use private bucket `store-product-images`, path `<providerId>/<productId>/<imageId>.<extension>`, JPG/PNG/WebP, maximum 5 MB, with signed URLs for authorized reads.
