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

/**
 * The verified facts an identity provider asserted about a user, normalized across OIDC and SAML.
 *
 * @param subject       the stable provider-side identifier (OIDC {@code sub}, SAML {@code NameID})
 * @param email         the email address, or {@code null}
 * @param emailVerified whether the provider vouches for the email: OIDC {@code email_verified == true}; always
 *                      {@code true} for SAML, whose signed assertion is the provider's own statement
 */
public record SsoIdentity(String subject, String email, boolean emailVerified, String firstName, String lastName) {
}
