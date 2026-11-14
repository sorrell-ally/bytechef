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

import com.bytechef.platform.security.web.config.OAuth2LoginCustomizer;
import com.bytechef.platform.sso.web.authentication.SsoLoginFailureHandler;
import com.bytechef.platform.sso.web.authentication.SsoLoginSuccessHandler;
import com.bytechef.platform.sso.web.authentication.SsoOidcUserService;
import com.bytechef.platform.sso.web.authentication.SsoUserResolver;
import com.bytechef.platform.sso.web.registration.SsoClientRegistrationRepository;
import com.bytechef.platform.sso.web.registration.SsoRegistrationCache;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.RememberMeServices;

/**
 * Adds database-backed OIDC providers to the single {@code oauth2Login} of the API filter chain.
 *
 * <p>
 * Coexistence with social login: there is one {@code oauth2Login}, so whichever customizer runs last wins for the
 * repository, the OIDC user service and the handlers. This customizer is ordered last ({@link Ordered}) and composes
 * rather than replaces: the repository falls back to the social {@code ClientRegistrationRepository} bean, the OIDC
 * user service delegates non-SSO registrations to the social OIDC user service bean, and the failure handler keeps
 * social login's {@code /login?error=oauth2}. The success handler behaves exactly like social login's. The plain-OAuth2
 * user service (GitHub) is left as social login set it.
 */
public class SsoOAuth2LoginCustomizer implements OAuth2LoginCustomizer, Ordered {

    private final @Nullable ClientRegistrationRepository socialClientRegistrationRepository;
    private final @Nullable OAuth2UserService<OidcUserRequest, OidcUser> socialOidcUserService;
    private final RememberMeServices rememberMeServices;
    private final SsoRegistrationCache ssoRegistrationCache;
    private final SsoUserResolver ssoUserResolver;

    public SsoOAuth2LoginCustomizer(
        @Nullable ClientRegistrationRepository socialClientRegistrationRepository,
        @Nullable OAuth2UserService<OidcUserRequest, OidcUser> socialOidcUserService,
        RememberMeServices rememberMeServices, SsoRegistrationCache ssoRegistrationCache,
        SsoUserResolver ssoUserResolver) {

        this.socialClientRegistrationRepository = socialClientRegistrationRepository;
        this.socialOidcUserService = socialOidcUserService;
        this.rememberMeServices = rememberMeServices;
        this.ssoRegistrationCache = ssoRegistrationCache;
        this.ssoUserResolver = ssoUserResolver;
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        ClientRegistrationRepository clientRegistrationRepository = new SsoClientRegistrationRepository(
            ssoRegistrationCache, socialClientRegistrationRepository);

        OidcUserService standardOidcUserService = new OidcUserService();

        SsoOidcUserService ssoOidcUserService = new SsoOidcUserService(
            standardOidcUserService, socialOidcUserService == null ? standardOidcUserService : socialOidcUserService,
            ssoRegistrationCache, ssoUserResolver);

        http.oauth2Login(oauth2 -> oauth2
            .clientRegistrationRepository(clientRegistrationRepository)
            .userInfoEndpoint(endpoint -> endpoint.oidcUserService(ssoOidcUserService))
            .successHandler(new SsoLoginSuccessHandler(rememberMeServices))
            .failureHandler(new SsoLoginFailureHandler()));
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
