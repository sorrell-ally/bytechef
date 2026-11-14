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

import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.web.registration.SsoClientRegistrationRepository;
import com.bytechef.platform.sso.web.registration.SsoRegistrationCache;
import com.bytechef.platform.user.domain.User;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * The single OIDC user service of {@code oauth2Login}. SSO registrations (prefix {@code sso-}) are resolved with
 * {@link SsoUserResolver}; every other registration is delegated unchanged to the social-login user service, so both
 * features share one {@code oauth2Login} configuration without one overriding the other.
 */
public class SsoOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final Logger log = LoggerFactory.getLogger(SsoOidcUserService.class);

    private final OAuth2UserService<OidcUserRequest, OidcUser> fallbackOidcUserService;
    private final OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService;
    private final SsoRegistrationCache ssoRegistrationCache;
    private final SsoUserResolver ssoUserResolver;

    public SsoOidcUserService(
        OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService,
        OAuth2UserService<OidcUserRequest, OidcUser> fallbackOidcUserService,
        SsoRegistrationCache ssoRegistrationCache, SsoUserResolver ssoUserResolver) {

        this.fallbackOidcUserService = fallbackOidcUserService;
        this.oidcUserService = oidcUserService;
        this.ssoRegistrationCache = ssoRegistrationCache;
        this.ssoUserResolver = ssoUserResolver;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest oidcUserRequest) throws OAuth2AuthenticationException {
        ClientRegistration clientRegistration = oidcUserRequest.getClientRegistration();

        String registrationId = clientRegistration.getRegistrationId();

        if (!SsoClientRegistrationRepository.isSsoRegistrationId(registrationId)) {
            return fallbackOidcUserService.loadUser(oidcUserRequest);
        }

        try {
            SsoProvider ssoProvider = ssoRegistrationCache.findEnabledSsoProvider(registrationId)
                .orElseThrow(() -> new SsoLoginException(SsoLoginError.PROVIDER_UNAVAILABLE));

            OidcUser oidcUser = oidcUserService.loadUser(oidcUserRequest);

            if (oidcUser == null) {
                throw new SsoLoginException(SsoLoginError.INVALID_IDENTITY);
            }

            SsoIdentity ssoIdentity = toSsoIdentity(ssoProvider, oidcUser);

            User user = ssoUserResolver.resolveUser(ssoProvider, ssoIdentity);

            List<GrantedAuthority> grantedAuthorities = ssoUserResolver.getGrantedAuthorities(user);

            return new SsoOidcUser(user.getLogin(), grantedAuthorities, registrationId, oidcUser);
        } catch (SsoLoginException ssoLoginException) {
            SsoLoginError ssoLoginError = ssoLoginException.getSsoLoginError();

            log.info("SSO login through {} refused: {}", registrationId, ssoLoginError.getCode());

            throw new OAuth2AuthenticationException(
                new OAuth2Error(ssoLoginError.getCode(), ssoLoginError.getDescription(), null), ssoLoginException);
        }
    }

    /**
     * Generic OIDC: {@code sub}, {@code email}, and verification only from {@code email_verified == true}. Microsoft
     * Entra ID: the ID-token rule of {@link SsoMicrosoftEntraIdentityMapper}.
     */
    static SsoIdentity toSsoIdentity(SsoProvider ssoProvider, OidcUser oidcUser) {
        if (ssoProvider.isMicrosoftEntraId()) {
            OidcIdToken oidcIdToken = oidcUser.getIdToken();

            if (oidcIdToken == null) {
                throw new SsoLoginException(SsoLoginError.INVALID_IDENTITY);
            }

            return SsoMicrosoftEntraIdentityMapper.toSsoIdentity(ssoProvider, oidcIdToken.getClaims());
        }

        return new SsoIdentity(
            oidcUser.getSubject(), oidcUser.getEmail(), Boolean.TRUE.equals(oidcUser.getEmailVerified()),
            oidcUser.getGivenName(), oidcUser.getFamilyName());
    }
}
