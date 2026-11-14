# Single sign-on (platform-sso)

Admin-managed OIDC and SAML 2.0 identity providers. Active only in the CE edition, single-tenant mode, with
`bytechef.security.sso.enabled=true`.

## Microsoft Entra ID (OIDC)

ByteChef supports Entra ID as a **single-tenant** OIDC provider. Multi-tenant authorities (`common`,
`organizations`, `consumers`) are refused on save: they would let any Microsoft tenant sign in, and other tenants'
administrators control the email-like claims of their users.

### 1. Register the application in Entra

In the Microsoft Entra admin center, go to **Entra ID > App registrations > New registration**:

- **Supported account types**: *Accounts in this organizational directory only (single tenant)*.
- **Redirect URI**: platform **Web**, value `<ByteChef public URL><callbackPath>`. `callbackPath` is shown on the
  provider after you create it in ByteChef (`/login/oauth2/code/sso-…`). Example:
  `https://bytechef.example.com/login/oauth2/code/sso-0123456789abcdef`. You can create the ByteChef provider first
  (disabled) to learn the path, then enable it.
- **Certificates & secrets**: create a **client secret** and copy its *Value*.
- **API permissions**: Microsoft Graph delegated permissions `openid`, `profile` and `email` (add any that are
  missing; grant admin consent if your tenant does not allow user consent). ByteChef requests `openid profile email`.
  `profile` is required: Entra only emits `oid`, `preferred_username` and the name claims with it.
- **Token configuration (optional)**: add the optional ID-token claims `email` and `xms_edov` if you want ByteChef to
  use the user's mail address instead of their sign-in name (see the email rule below). Without them ByteChef uses
  `preferred_username` (the user principal name).

No implicit-grant or ID-token checkbox is needed: ByteChef uses the authorization code flow with PKCE.

### 2. Create the provider in ByteChef

| Field | Value |
| --- | --- |
| Type | `OIDC` |
| Vendor | `MICROSOFT_ENTRA_ID` |
| Issuer URI | `https://login.microsoftonline.com/<Directory (tenant) ID>/v2.0` - the tenant **GUID**, not a domain name |
| Client ID | *Application (client) ID* |
| Client secret | the secret *Value* |
| Scopes | leave empty (defaults to `openid profile email`) |
| Domains | the email domains of your users, e.g. `acme.com` - only domains verified in your tenant make sense |

US Government (`login.microsoftonline.us`) and China (`login.partner.microsoftonline.cn`) authority hosts are also
accepted.

### How an Entra sign-in is mapped

1. The ID token's `iss` must equal the configured issuer and `tid` must equal its tenant GUID
   (`sso_tenant_mismatch` otherwise).
2. Guests of the tenant (`idp` differing from `iss`, or `acct = 1`) are refused (`sso_guest_account_not_allowed`).
3. The account key is `oid`, the user's immutable object id in the tenant.
4. Email: the `email` claim only when `xms_edov` is `true`; otherwise `preferred_username` when it is an email address
   (for members it is the UPN, whose domain Entra requires to be verified in the tenant); otherwise none
   (`sso_email_missing`).
5. That email must be in the provider's domains (`sso_email_domain_not_allowed`). It is then used once to link an
   existing ByteChef account or provision a new one; afterwards the account is matched by `oid`.

Generic OIDC providers are unchanged: they need `email` with `email_verified = true`.
