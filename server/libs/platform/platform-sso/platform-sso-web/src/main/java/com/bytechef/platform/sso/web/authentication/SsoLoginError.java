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

import java.util.Arrays;
import java.util.Optional;

/**
 * Why an SSO login was refused. The {@link #getCode() code} is what the login page receives in
 * {@code /login?error=sso&reason=<code>}; it is a fixed server-side value, never echoed request input.
 */
public enum SsoLoginError {

    ACCOUNT_DISABLED("sso_account_disabled", "The ByteChef account is deactivated"),
    ACCOUNT_LINKED_ELSEWHERE(
        "sso_account_linked_elsewhere", "The ByteChef account with this email is linked to a different identity"),
    ACCOUNT_NOT_PROVISIONED(
        "sso_account_not_provisioned", "No ByteChef account exists for this identity and auto-provisioning is off"),
    AUTHENTICATION_FAILED("sso_authentication_failed", "The identity provider response could not be verified"),
    EMAIL_DOMAIN_NOT_ALLOWED(
        "sso_email_domain_not_allowed", "The email domain is not one of the domains this SSO provider serves"),
    EMAIL_MISSING("sso_email_missing", "The identity provider did not supply an email address"),
    EMAIL_NOT_VERIFIED("sso_email_not_verified", "The identity provider did not mark the email address as verified"),
    GUEST_ACCOUNT_NOT_ALLOWED(
        "sso_guest_account_not_allowed", "Guest (external) accounts of the Microsoft Entra tenant cannot sign in"),
    INVALID_IDENTITY("sso_invalid_identity", "The identity provider did not supply a usable subject identifier"),
    PROVIDER_UNAVAILABLE("sso_provider_unavailable", "The SSO provider is disabled or misconfigured"),
    PROVISIONING_FAILED("sso_provisioning_failed", "The ByteChef account could not be created"),
    TENANT_MISMATCH("sso_tenant_mismatch", "The token was issued for a different Microsoft Entra tenant");

    private final String code;
    private final String description;

    SsoLoginError(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static Optional<SsoLoginError> fromCode(String code) {
        return Arrays.stream(values())
            .filter(ssoLoginError -> ssoLoginError.code.equals(code))
            .findFirst();
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
