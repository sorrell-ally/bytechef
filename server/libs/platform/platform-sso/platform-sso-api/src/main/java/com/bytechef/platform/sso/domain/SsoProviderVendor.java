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

package com.bytechef.platform.sso.domain;

/**
 * The identity-provider product behind an OIDC {@link SsoProvider}, selecting vendor-specific validation and claim
 * mapping. SAML providers are always {@link #GENERIC}.
 */
public enum SsoProviderVendor {

    /**
     * Standard OpenID Connect: the {@code email} claim with {@code email_verified == true} is required to link or
     * provision an account.
     */
    GENERIC,

    /**
     * Microsoft Entra ID (v2.0 endpoint, single tenant only). See {@code SsoMicrosoftEntraIdentityMapper} for the
     * tenant, guest and email rules.
     */
    MICROSOFT_ENTRA_ID
}
