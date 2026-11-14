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

import com.bytechef.platform.sso.web.registration.SsoClientRegistrationRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * Failure handler shared by {@code oauth2Login} and {@code saml2Login}.
 *
 * <p>
 * A failure on an SSO callback redirects to {@code /login?error=sso&reason=<code>}, where {@code code} is one of the
 * {@link SsoLoginError} codes (or {@code sso_authentication_failed} for anything Spring Security itself rejected, such
 * as a bad signature). The code is looked up from a fixed list, so nothing attacker-controlled is reflected. Every
 * other failure (social login) keeps its existing redirect, {@code /login?error=oauth2}.
 */
public class SsoLoginFailureHandler implements AuthenticationFailureHandler {

    static final String SOCIAL_FAILURE_PATH = "/login?error=oauth2";
    static final String SSO_FAILURE_PATH = "/login?error=sso&reason=";

    @Override
    @SuppressFBWarnings(
        value = "UNVALIDATED_REDIRECT", justification = "target is a constant path plus a fixed SsoLoginError code")
    public void onAuthenticationFailure(
        HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
        throws IOException {

        if (!SsoClientRegistrationRepository.isSsoCallbackPath(request.getRequestURI())) {
            response.sendRedirect(SOCIAL_FAILURE_PATH);

            return;
        }

        String errorCode = null;

        if (exception instanceof OAuth2AuthenticationException oAuth2AuthenticationException) {
            errorCode = oAuth2AuthenticationException.getError()
                .getErrorCode();
        } else if (exception instanceof Saml2AuthenticationException saml2AuthenticationException) {
            errorCode = saml2AuthenticationException.getSaml2Error()
                .getErrorCode();
        }

        SsoLoginError ssoLoginError = SsoLoginError.fromCode(errorCode)
            .orElse(SsoLoginError.AUTHENTICATION_FAILED);

        response.sendRedirect(SSO_FAILURE_PATH + ssoLoginError.getCode());
    }
}
