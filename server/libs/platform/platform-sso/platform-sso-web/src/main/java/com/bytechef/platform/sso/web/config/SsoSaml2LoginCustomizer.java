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

import com.bytechef.platform.security.web.config.Saml2LoginCustomizer;
import com.bytechef.platform.sso.util.SsoProviderPathUtils;
import com.bytechef.platform.sso.web.authentication.SsoLoginFailureHandler;
import com.bytechef.platform.sso.web.authentication.SsoLoginSuccessHandler;
import com.bytechef.platform.sso.web.authentication.SsoSaml2ResponseAuthenticationConverter;
import com.bytechef.platform.sso.web.authentication.SsoUserResolver;
import com.bytechef.platform.sso.web.registration.SsoRegistrationCache;
import com.bytechef.platform.sso.web.registration.SsoRelyingPartyRegistrationRepository;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Configures {@code saml2Login} and {@code saml2Metadata} for database-backed SAML providers on the API filter chain.
 *
 * <p>
 * CSRF: the assertion consumer service receives a cross-site HTTP-POST from the IdP that cannot carry ByteChef's CSRF
 * token, so exactly {@code POST /login/saml2/sso/{registrationId}} is exempted. That endpoint is protected by the SAML
 * protocol instead: the response must be signed by the provider's metadata key, addressed to this SP (audience and
 * destination), within its validity window, and must answer an AuthnRequest this server issued ({@code InResponseTo}),
 * which {@code OpenSaml5AuthenticationProvider} enforces. The exemption is added through the existing
 * {@code HttpSecurity.csrf(...)} customizer, which accumulates ignore matchers, so no SPI change was needed.
 */
public class SsoSaml2LoginCustomizer implements Saml2LoginCustomizer {

    static final String ASSERTION_CONSUMER_SERVICE_PATTERN =
        SsoProviderPathUtils.SAML_ASSERTION_CONSUMER_PATH + "{registrationId}";
    static final String AUTHENTICATION_REQUEST_PATTERN = SsoProviderPathUtils.SAML_LOGIN_PATH + "{registrationId}";
    static final String METADATA_PATTERN = SsoProviderPathUtils.SAML_METADATA_PATH + "{registrationId}";

    private final RememberMeServices rememberMeServices;
    private final SsoRegistrationCache ssoRegistrationCache;
    private final SsoUserResolver ssoUserResolver;

    public SsoSaml2LoginCustomizer(
        RememberMeServices rememberMeServices, SsoRegistrationCache ssoRegistrationCache,
        SsoUserResolver ssoUserResolver) {

        this.rememberMeServices = rememberMeServices;
        this.ssoRegistrationCache = ssoRegistrationCache;
        this.ssoUserResolver = ssoUserResolver;
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        RelyingPartyRegistrationRepository relyingPartyRegistrationRepository =
            new SsoRelyingPartyRegistrationRepository(ssoRegistrationCache);

        OpenSaml5AuthenticationProvider openSaml5AuthenticationProvider = new OpenSaml5AuthenticationProvider();

        openSaml5AuthenticationProvider.setResponseAuthenticationConverter(
            new SsoSaml2ResponseAuthenticationConverter(ssoRegistrationCache, ssoUserResolver));

        http.saml2Login(saml2 -> saml2
            .relyingPartyRegistrationRepository(relyingPartyRegistrationRepository)
            .authenticationManager(new ProviderManager(openSaml5AuthenticationProvider))
            .authenticationRequestUri(AUTHENTICATION_REQUEST_PATTERN)
            .loginProcessingUrl(ASSERTION_CONSUMER_SERVICE_PATTERN)
            .loginPage("/")
            .successHandler(new SsoLoginSuccessHandler(rememberMeServices))
            .failureHandler(new SsoLoginFailureHandler()));

        http.saml2Metadata(saml2Metadata -> saml2Metadata.metadataUrl(METADATA_PATTERN));

        http.csrf(csrf -> csrf.ignoringRequestMatchers(getAssertionConsumerServiceRequestMatcher()));
    }

    static RequestMatcher getAssertionConsumerServiceRequestMatcher() {
        PathPatternRequestMatcher.Builder builder = PathPatternRequestMatcher.withDefaults();

        return builder.matcher(HttpMethod.POST, ASSERTION_CONSUMER_SERVICE_PATTERN);
    }
}
