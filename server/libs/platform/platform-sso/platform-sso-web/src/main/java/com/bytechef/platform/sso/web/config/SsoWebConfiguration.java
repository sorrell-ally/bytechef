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

import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import com.bytechef.platform.security.web.config.AuthorizeHttpRequestContributor;
import com.bytechef.platform.security.web.config.OAuth2LoginCustomizer;
import com.bytechef.platform.security.web.config.Saml2LoginCustomizer;
import com.bytechef.platform.security.web.config.SecurityConfigurerContributor;
import com.bytechef.platform.sso.service.SsoProviderService;
import com.bytechef.platform.sso.web.authentication.SsoSaml2AuthenticationRequestRepository;
import com.bytechef.platform.sso.web.authentication.SsoUserResolver;
import com.bytechef.platform.sso.web.filter.SsoPasswordLoginEnforcementFilter;
import com.bytechef.platform.sso.web.registration.SsoIdTokenDecoderFactory;
import com.bytechef.platform.sso.web.registration.SsoRegistrationCache;
import com.bytechef.platform.sso.web.registration.SsoRegistrationFactory;
import com.bytechef.platform.sso.web.registration.SsoRemoteDocumentFetcher;
import com.bytechef.platform.user.service.AuthorityService;
import com.bytechef.platform.user.service.UserService;
import com.bytechef.tenant.annotation.ConditionalOnSingleTenant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.saml2.provider.service.authentication.AbstractSaml2AuthenticationRequest;
import org.springframework.security.saml2.provider.service.web.Saml2AuthenticationRequestRepository;
import org.springframework.security.web.authentication.RememberMeServices;

/**
 * Wires single sign-on into the existing security SPIs. Active only in the CE edition, in single-tenant mode, and when
 * {@code bytechef.security.sso.enabled=true}; nothing here exists otherwise.
 *
 * <p>
 * The registration repositories and the enforcement filter are intentionally not beans: a second
 * {@code ClientRegistrationRepository} bean would clash with social login's, and a {@code Filter} bean would be
 * auto-registered by Spring Boot on every request outside the security chain.
 */
@Configuration
@ConditionalOnCEVersion
@ConditionalOnSingleTenant
@ConditionalOnProperty(prefix = "bytechef.security.sso", name = "enabled", havingValue = "true")
@Profile("!liquibase")
public class SsoWebConfiguration {

    @Bean
    AuthorizeHttpRequestContributor ssoAuthorizeHttpRequestContributor() {
        return new SsoAuthorizeHttpRequestContributor();
    }

    @Bean
    JwtDecoderFactory<ClientRegistration> ssoIdTokenDecoderFactory() {
        return new SsoIdTokenDecoderFactory();
    }

    @Bean
    OAuth2LoginCustomizer ssoOAuth2LoginCustomizer(
        ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
        ObjectProvider<OAuth2UserService<OidcUserRequest, OidcUser>> oidcUserServiceProvider,
        RememberMeServices rememberMeServices, SsoRegistrationCache ssoRegistrationCache,
        SsoUserResolver ssoUserResolver) {

        return new SsoOAuth2LoginCustomizer(
            clientRegistrationRepositoryProvider.getIfUnique(), oidcUserServiceProvider.getIfUnique(),
            rememberMeServices, ssoRegistrationCache, ssoUserResolver);
    }

    @Bean
    SecurityConfigurerContributor ssoPasswordLoginEnforcementSecurityConfigurerContributor(
        AuthorityService authorityService, SsoProviderService ssoProviderService, UserService userService) {

        return new SecurityConfigurerContributor() {

            @Override
            @SuppressWarnings("unchecked")
            public <T extends AbstractHttpConfigurer<T, B>, B extends HttpSecurityBuilder<B>> T
                getSecurityConfigurerAdapter() {

                return (T) new SsoPasswordLoginEnforcementConfigurer(
                    new SsoPasswordLoginEnforcementFilter(authorityService, ssoProviderService, userService));
            }
        };
    }

    @Bean
    SsoRegistrationCache ssoRegistrationCache(SsoProviderService ssoProviderService) {
        return new SsoRegistrationCache(
            ssoProviderService, new SsoRegistrationFactory(new SsoRemoteDocumentFetcher()));
    }

    @Bean
    Saml2AuthenticationRequestRepository<AbstractSaml2AuthenticationRequest> ssoSaml2AuthenticationRequestRepository() {
        return new SsoSaml2AuthenticationRequestRepository();
    }

    @Bean
    Saml2LoginCustomizer ssoSaml2LoginCustomizer(
        RememberMeServices rememberMeServices, SsoRegistrationCache ssoRegistrationCache,
        SsoUserResolver ssoUserResolver) {

        return new SsoSaml2LoginCustomizer(rememberMeServices, ssoRegistrationCache, ssoUserResolver);
    }
}
