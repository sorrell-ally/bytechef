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
import com.bytechef.platform.sso.web.registration.SsoRegistrationCache;
import com.bytechef.platform.user.domain.User;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.saml2.core.Saml2Error;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider.ResponseToken;
import org.springframework.security.saml2.provider.service.authentication.Saml2AssertionAuthentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertionAccessor;

/**
 * Turns a validated SAML response into a ByteChef session. Signature, audience, destination, {@code InResponseTo} and
 * time-window validation stay with {@link OpenSaml5AuthenticationProvider}; this only runs after they pass.
 *
 * <p>
 * The subject is the {@code NameID}. The email is taken from the first present of the common attributes ({@code email},
 * {@code mail}, the WS-Federation/Entra {@code emailaddress} claim, the X.500 {@code mail} OID) and falls back to the
 * {@code NameID} when that is an email address. A signed assertion is the IdP's own statement, so the email counts as
 * verified; the provider's domain list still limits which accounts it can link or create.
 */
public class SsoSaml2ResponseAuthenticationConverter implements Converter<ResponseToken, AbstractAuthenticationToken> {

    static final List<String> EMAIL_ATTRIBUTE_NAMES = List.of(
        "email", "mail", "emailAddress", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress",
        "urn:oid:0.9.2342.19200300.100.1.3");
    static final List<String> FIRST_NAME_ATTRIBUTE_NAMES = List.of(
        "firstName", "givenName", "given_name", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname",
        "urn:oid:2.5.4.42");
    static final List<String> LAST_NAME_ATTRIBUTE_NAMES = List.of(
        "lastName", "surname", "sn", "family_name", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname",
        "urn:oid:2.5.4.4");

    private static final Logger log = LoggerFactory.getLogger(SsoSaml2ResponseAuthenticationConverter.class);

    private final Converter<ResponseToken, Saml2Authentication> delegateConverter;
    private final SsoRegistrationCache ssoRegistrationCache;
    private final SsoUserResolver ssoUserResolver;

    public SsoSaml2ResponseAuthenticationConverter(
        SsoRegistrationCache ssoRegistrationCache, SsoUserResolver ssoUserResolver) {

        this(new OpenSaml5AuthenticationProvider.ResponseAuthenticationConverter(), ssoRegistrationCache,
            ssoUserResolver);
    }

    SsoSaml2ResponseAuthenticationConverter(
        Converter<ResponseToken, Saml2Authentication> delegateConverter, SsoRegistrationCache ssoRegistrationCache,
        SsoUserResolver ssoUserResolver) {

        this.delegateConverter = delegateConverter;
        this.ssoRegistrationCache = ssoRegistrationCache;
        this.ssoUserResolver = ssoUserResolver;
    }

    @Override
    public AbstractAuthenticationToken convert(ResponseToken responseToken) {
        Saml2Authentication saml2Authentication = delegateConverter.convert(responseToken);

        if (!(saml2Authentication instanceof Saml2AssertionAuthentication saml2AssertionAuthentication)) {
            throw toSaml2AuthenticationException(SsoLoginError.AUTHENTICATION_FAILED);
        }

        String registrationId = saml2AssertionAuthentication.getRelyingPartyRegistrationId();

        if (registrationId == null) {
            throw toSaml2AuthenticationException(SsoLoginError.AUTHENTICATION_FAILED);
        }

        try {
            SsoProvider ssoProvider = ssoRegistrationCache.findEnabledSsoProvider(registrationId)
                .orElseThrow(() -> new SsoLoginException(SsoLoginError.PROVIDER_UNAVAILABLE));

            Saml2ResponseAssertionAccessor saml2ResponseAssertionAccessor = saml2AssertionAuthentication
                .getCredentials();

            if (saml2ResponseAssertionAccessor == null) {
                throw new SsoLoginException(SsoLoginError.INVALID_IDENTITY);
            }

            SsoIdentity ssoIdentity = toSsoIdentity(saml2ResponseAssertionAccessor);

            User user = ssoUserResolver.resolveUser(ssoProvider, ssoIdentity);

            List<GrantedAuthority> grantedAuthorities = ssoUserResolver.getGrantedAuthorities(user);

            SsoUserPrincipal ssoUserPrincipal = new SsoUserPrincipal(
                user.getLogin(), grantedAuthorities, registrationId);

            return new Saml2AssertionAuthentication(
                ssoUserPrincipal, saml2ResponseAssertionAccessor, grantedAuthorities, registrationId);
        } catch (SsoLoginException ssoLoginException) {
            SsoLoginError ssoLoginError = ssoLoginException.getSsoLoginError();

            log.info("SSO login through {} refused: {}", registrationId, ssoLoginError.getCode());

            throw toSaml2AuthenticationException(ssoLoginError);
        }
    }

    static SsoIdentity toSsoIdentity(Saml2ResponseAssertionAccessor saml2ResponseAssertionAccessor) {
        String nameId = saml2ResponseAssertionAccessor.getNameId();

        String email = findFirstAttribute(saml2ResponseAssertionAccessor, EMAIL_ATTRIBUTE_NAMES);

        if (email == null && nameId != null && nameId.contains("@")) {
            email = nameId;
        }

        return new SsoIdentity(
            nameId, email == null ? null : email.toLowerCase(Locale.ROOT), true,
            findFirstAttribute(saml2ResponseAssertionAccessor, FIRST_NAME_ATTRIBUTE_NAMES),
            findFirstAttribute(saml2ResponseAssertionAccessor, LAST_NAME_ATTRIBUTE_NAMES));
    }

    private static String findFirstAttribute(
        Saml2ResponseAssertionAccessor saml2ResponseAssertionAccessor, List<String> attributeNames) {

        for (String attributeName : attributeNames) {
            Object value = saml2ResponseAssertionAccessor.getFirstAttribute(attributeName);

            if (value == null) {
                continue;
            }

            String stringValue = String.valueOf(value);

            String trimmedValue = stringValue.trim();

            if (!trimmedValue.isEmpty()) {
                return trimmedValue;
            }
        }

        return null;
    }

    private static Saml2AuthenticationException toSaml2AuthenticationException(SsoLoginError ssoLoginError) {
        return new Saml2AuthenticationException(
            new Saml2Error(ssoLoginError.getCode(), ssoLoginError.getDescription()));
    }
}
