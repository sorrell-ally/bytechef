/*
 * Copyright 2025 ByteChef
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bytechef.platform.sso.web.authentication;

import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.util.SsoEmailDomainUtils;
import com.bytechef.platform.sso.util.SsoMicrosoftEntraUtils;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Maps a Microsoft Entra ID v2.0 ID token onto an {@link SsoIdentity}. Entra does not emit {@code email_verified}, and
 * its {@code email} claim is optional and administrator-controlled, so the generic OIDC rule would refuse every Entra
 * login; this mapper replaces it with the rule below. It reads the ID token's claims only (the registration has no
 * userinfo endpoint), and Spring Security has already verified the token's signature, {@code aud}, {@code exp},
 * {@code nonce} and {@code iss} (which must equal the configured tenant-specific issuer).
 *
 * <ol>
 * <li><b>Tenant.</b> {@code tid} must equal the tenant GUID of the configured issuer, else {@code sso_tenant_mismatch}.
 * The issuer itself is tenant-specific (multi-tenant authorities are rejected on save), so this is defence in depth:
 * only the configured tenant can mint an accepted token.</li>
 * <li><b>Members only.</b> A guest of the tenant is refused ({@code sso_guest_account_not_allowed}). A guest is
 * detected by an {@code idp} claim that differs from {@code iss} (Entra sets {@code idp} for users whose account is
 * homed outside the issuing tenant) or by the optional {@code acct} claim equal to {@code 1}. Guests' email-like claims
 * come from their home directory or an external identity provider, outside the configured tenant's control.</li>
 * <li><b>Subject.</b> {@code oid}, the user's immutable object id in the tenant, which unlike the pairwise {@code sub}
 * survives re-registering the application. Combined with the fixed tenant it is the identifier Microsoft recommends
 * ({@code oid} + {@code tid}).</li>
 * <li><b>Email.</b> The {@code email} claim is used only when the optional {@code xms_edov} claim is {@code true}
 * (Microsoft: the email's domain belongs to the user's tenant and the tenant admin verified it). Otherwise
 * {@code preferred_username} is used when it is an email address: for a member account it is the user principal name,
 * and Entra only lets a UPN carry a domain suffix that is verified in the tenant. Any other value yields no email.</li>
 * <li><b>Verified.</b> An email chosen by rule 4 counts as verified for {@link SsoUserResolver}: it is asserted by the
 * configured tenant about one of its own members, and its domain is verified in that tenant. {@code SsoUserResolver}
 * still requires the domain to be one of the provider's domains, which the ByteChef administrator chose; so the
 * configured tenant can claim only accounts in domains the administrator entrusted to it.</li>
 * </ol>
 *
 * Microsoft documents {@code email} and {@code preferred_username} as mutable and not for authorization decisions. Here
 * they are used once, to link or provision an account; afterwards the account is matched on {@code oid}. A renamed user
 * therefore keeps their ByteChef account, and an address reassigned to a different person in the tenant does not
 * inherit the previous holder's account once that account has been linked.
 */
final class SsoMicrosoftEntraIdentityMapper {

    static final String ACCOUNT_TYPE_CLAIM = "acct";
    static final String EMAIL_CLAIM = "email";
    static final String EMAIL_DOMAIN_OWNER_VERIFIED_CLAIM = "xms_edov";
    static final String IDENTITY_PROVIDER_CLAIM = "idp";
    static final String ISSUER_CLAIM = "iss";
    static final String OBJECT_ID_CLAIM = "oid";
    static final String PREFERRED_USERNAME_CLAIM = "preferred_username";
    static final String TENANT_ID_CLAIM = "tid";

    private SsoMicrosoftEntraIdentityMapper() {
    }

    static SsoIdentity toSsoIdentity(SsoProvider ssoProvider, Map<String, Object> idTokenClaims) {
        String expectedTenantId = SsoMicrosoftEntraUtils.extractTenantId(ssoProvider.getIssuerUri())
            .orElseThrow(() -> new SsoLoginException(SsoLoginError.PROVIDER_UNAVAILABLE));

        String tenantId = getString(idTokenClaims, TENANT_ID_CLAIM);

        if (tenantId == null || !expectedTenantId.equals(tenantId.toLowerCase(Locale.ROOT))) {
            throw new SsoLoginException(SsoLoginError.TENANT_MISMATCH);
        }

        if (isGuest(idTokenClaims)) {
            throw new SsoLoginException(SsoLoginError.GUEST_ACCOUNT_NOT_ALLOWED);
        }

        String email = resolveEmail(idTokenClaims);

        return new SsoIdentity(
            getString(idTokenClaims, OBJECT_ID_CLAIM), email, email != null, getString(idTokenClaims, "given_name"),
            getString(idTokenClaims, "family_name"));
    }

    static boolean isGuest(Map<String, Object> idTokenClaims) {
        String identityProvider = getString(idTokenClaims, IDENTITY_PROVIDER_CLAIM);

        if (identityProvider != null && !identityProvider.equals(getString(idTokenClaims, ISSUER_CLAIM))) {
            return true;
        }

        return "1".equals(getString(idTokenClaims, ACCOUNT_TYPE_CLAIM));
    }

    static String resolveEmail(Map<String, Object> idTokenClaims) {
        String email = getString(idTokenClaims, EMAIL_CLAIM);

        if (isEmail(email) && isTrue(idTokenClaims.get(EMAIL_DOMAIN_OWNER_VERIFIED_CLAIM))) {
            return email.toLowerCase(Locale.ROOT);
        }

        String preferredUsername = getString(idTokenClaims, PREFERRED_USERNAME_CLAIM);

        if (isEmail(preferredUsername)) {
            return preferredUsername.toLowerCase(Locale.ROOT);
        }

        return null;
    }

    private static String getString(Map<String, Object> claims, String name) {
        Object value = claims.get(name);

        if (value == null) {
            return null;
        }

        String stringValue = Objects.toString(value);

        String trimmedValue = stringValue.trim();

        return trimmedValue.isEmpty() ? null : trimmedValue;
    }

    private static boolean isEmail(String value) {
        return value != null && SsoEmailDomainUtils.extractDomain(value)
            .isPresent();
    }

    private static boolean isTrue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        return value != null && ("true".equalsIgnoreCase(value.toString()) || "1".equals(value.toString()));
    }
}
