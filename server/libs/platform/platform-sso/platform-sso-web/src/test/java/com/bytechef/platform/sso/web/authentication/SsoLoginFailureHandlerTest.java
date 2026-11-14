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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.saml2.core.Saml2Error;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException;

class SsoLoginFailureHandlerTest {

    private final SsoLoginFailureHandler ssoLoginFailureHandler = new SsoLoginFailureHandler();

    @Test
    void testOidcFailureCarriesKnownReason() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ssoLoginFailureHandler.onAuthenticationFailure(
            new MockHttpServletRequest("GET", "/login/oauth2/code/sso-0011223344556677"), response,
            new OAuth2AuthenticationException(
                new OAuth2Error(SsoLoginError.EMAIL_NOT_VERIFIED.getCode(), "description", null)));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=sso&reason=sso_email_not_verified");
    }

    @Test
    void testUnknownReasonIsNotReflected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ssoLoginFailureHandler.onAuthenticationFailure(
            new MockHttpServletRequest("POST", "/login/saml2/sso/sso-0011223344556677"), response,
            new Saml2AuthenticationException(new Saml2Error("<script>", "attacker controlled")));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=sso&reason=sso_authentication_failed");
    }

    @Test
    void testSocialFailureKeepsExistingRedirect() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ssoLoginFailureHandler.onAuthenticationFailure(
            new MockHttpServletRequest("GET", "/login/oauth2/code/google"), response,
            new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=oauth2");
    }
}
