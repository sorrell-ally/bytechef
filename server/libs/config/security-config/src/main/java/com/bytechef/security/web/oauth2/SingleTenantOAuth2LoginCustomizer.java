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

package com.bytechef.security.web.oauth2;

import com.bytechef.platform.security.web.config.OAuth2LoginCustomizer;
import com.bytechef.tenant.annotation.ConditionalOnSingleTenant;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.stereotype.Component;

/**
 * Ordered first among {@link OAuth2LoginCustomizer}s: the single-sign-on customizer composes on top of this one (it
 * reuses the social client registrations and OIDC user service as fallbacks), so it must run after it.
 *
 * @author Ivica Cardic
 */
@Component
@ConditionalOnSingleTenant
@ConditionalOnProperty(prefix = "bytechef.security.social-login", name = "enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SingleTenantOAuth2LoginCustomizer implements OAuth2LoginCustomizer {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final SocialOidcUserService socialOidcUserService;
    private final RememberMeServices rememberMeServices;

    @SuppressFBWarnings("EI")
    public SingleTenantOAuth2LoginCustomizer(
        CustomOAuth2UserService customOAuth2UserService, SocialOidcUserService socialOidcUserService,
        RememberMeServices rememberMeServices) {

        this.customOAuth2UserService = customOAuth2UserService;
        this.socialOidcUserService = socialOidcUserService;
        this.rememberMeServices = rememberMeServices;
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        http.oauth2Login(oauth2 -> oauth2
            .userInfoEndpoint(endpoint -> endpoint
                .userService(customOAuth2UserService)
                .oidcUserService(socialOidcUserService))
            .successHandler(new OAuth2AuthenticationSuccessHandler(rememberMeServices))
            .failureHandler(new OAuth2AuthenticationFailureHandler()));
    }
}
