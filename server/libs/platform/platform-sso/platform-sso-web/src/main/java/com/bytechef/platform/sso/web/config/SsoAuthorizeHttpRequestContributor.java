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

package com.bytechef.platform.sso.web.config;

import com.bytechef.platform.security.web.config.AuthorizeHttpRequestContributor;
import java.util.List;

/**
 * Permits the SAML endpoints on the API chain (the OIDC ones, {@code /oauth2/authorization/**} and
 * {@code /login/oauth2/code/**}, are already permitted by {@code UserAuthorizeHttpRequestContributor}).
 */
public class SsoAuthorizeHttpRequestContributor implements AuthorizeHttpRequestContributor {

    @Override
    public List<String> getApiPermitAllRequestMatcherPaths() {
        return List.of(
            SsoSaml2LoginCustomizer.AUTHENTICATION_REQUEST_PATTERN,
            SsoSaml2LoginCustomizer.ASSERTION_CONSUMER_SERVICE_PATTERN, SsoSaml2LoginCustomizer.METADATA_PATTERN);
    }
}
