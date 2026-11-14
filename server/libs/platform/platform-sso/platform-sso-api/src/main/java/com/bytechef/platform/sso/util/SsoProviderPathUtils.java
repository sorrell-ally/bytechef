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

package com.bytechef.platform.sso.util;

import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.domain.SsoProviderType;

/**
 * The server-relative endpoints Spring Security serves for a provider. Built only from the server-generated
 * registration id, never from request input, so discovery responses cannot be turned into open redirects.
 */
public final class SsoProviderPathUtils {

    public static final String OIDC_CALLBACK_PATH = "/login/oauth2/code/";
    public static final String OIDC_LOGIN_PATH = "/oauth2/authorization/";
    public static final String SAML_ASSERTION_CONSUMER_PATH = "/login/saml2/sso/";
    public static final String SAML_LOGIN_PATH = "/saml2/authenticate/";
    public static final String SAML_METADATA_PATH = "/saml2/metadata/";

    private SsoProviderPathUtils() {
    }

    public static String getCallbackPath(SsoProvider ssoProvider) {
        if (ssoProvider.getType() == SsoProviderType.SAML) {
            return SAML_ASSERTION_CONSUMER_PATH + ssoProvider.getRegistrationId();
        }

        return OIDC_CALLBACK_PATH + ssoProvider.getRegistrationId();
    }

    public static String getLoginPath(SsoProvider ssoProvider) {
        if (ssoProvider.getType() == SsoProviderType.SAML) {
            return SAML_LOGIN_PATH + ssoProvider.getRegistrationId();
        }

        return OIDC_LOGIN_PATH + ssoProvider.getRegistrationId();
    }

    public static String getServiceProviderMetadataPath(SsoProvider ssoProvider) {
        if (ssoProvider.getType() != SsoProviderType.SAML) {
            return null;
        }

        return SAML_METADATA_PATH + ssoProvider.getRegistrationId();
    }
}
