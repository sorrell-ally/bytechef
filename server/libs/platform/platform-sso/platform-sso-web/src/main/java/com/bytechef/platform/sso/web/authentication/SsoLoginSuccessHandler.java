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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.RememberMeServices;

/**
 * Completes an OIDC or SAML login the same way social login does: notify remember-me, then redirect to the fixed client
 * route {@code /oauth2/redirect}. The target is a constant, so no request parameter can steer it (no open redirect);
 * the client restores any deep link it remembered before leaving.
 */
public class SsoLoginSuccessHandler implements AuthenticationSuccessHandler {

    static final String REDIRECT_PATH = "/oauth2/redirect";

    private final RememberMeServices rememberMeServices;

    public SsoLoginSuccessHandler(RememberMeServices rememberMeServices) {
        this.rememberMeServices = rememberMeServices;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        rememberMeServices.loginSuccess(request, response, authentication);

        response.sendRedirect(REDIRECT_PATH);
    }
}
