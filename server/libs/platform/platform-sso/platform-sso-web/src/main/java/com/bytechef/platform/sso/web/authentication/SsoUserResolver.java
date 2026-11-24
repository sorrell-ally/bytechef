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

import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import com.bytechef.platform.security.constant.AuthorityConstants;
import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.util.SsoEmailDomainUtils;
import com.bytechef.platform.user.constant.UserConstants;
import com.bytechef.platform.user.domain.Authority;
import com.bytechef.platform.user.domain.User;
import com.bytechef.platform.user.service.AuthorityService;
import com.bytechef.platform.user.service.PendingUserInvitation;
import com.bytechef.platform.user.service.PendingUserInvitation.InvitationRole;
import com.bytechef.platform.user.service.UserService;
import com.bytechef.tenant.annotation.ConditionalOnSingleTenant;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Maps an identity asserted by an SSO provider onto a ByteChef user. The rules, in order:
 *
 * <ol>
 * <li><b>Known identity.</b> A user already linked to this provider ({@code auth_provider} = the provider's
 * registration id, {@code provider_id} = subject) signs in, unless deactivated.</li>
 * <li><b>Link by email.</b> Otherwise an existing user with the asserted email is linked only when the email's domain
 * is one of the provider's domains, the provider vouches for the email ({@code email_verified} for OIDC), and the
 * account is not already linked to a different identity. Linking an account across providers, or to a second subject of
 * the same provider, is refused: that is the account-takeover path this check exists to close.</li>
 * <li><b>Provision.</b> Otherwise, when auto-provisioning is on and the same domain and verification conditions hold, a
 * user is created with the provider's default authority ({@code ROLE_USER} unless an admin chose otherwise).</li>
 * <li>Anything else is refused with an {@link SsoLoginException}.</li>
 * </ol>
 *
 * <p>
 * Unlike {@code UserService.findOrCreateSocialUser}, this never links by email without the domain and verification
 * checks and never falls back to {@code ROLE_ADMIN}.
 */
@Component
@ConditionalOnCEVersion
@ConditionalOnSingleTenant
@ConditionalOnProperty(prefix = "bytechef.security.sso", name = "enabled", havingValue = "true")
public class SsoUserResolver {

    private static final Logger log = LoggerFactory.getLogger(SsoUserResolver.class);

    public static final String INVITATION_COOKIE = "BYTECHEF_INVITATION";

    private static final int GENERATED_PASSWORD_BYTES = 32;
    private static final int PROVIDER_ID_MAX_LENGTH = 255;

    private final AuthorityService authorityService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<PendingUserInvitation> pendingUserInvitations;
    private final SecureRandom secureRandom = new SecureRandom();
    private final UserService userService;

    @SuppressFBWarnings("EI")
    public SsoUserResolver(
        AuthorityService authorityService, PasswordEncoder passwordEncoder,
        ObjectProvider<PendingUserInvitation> pendingUserInvitations, UserService userService) {

        this.authorityService = authorityService;
        this.passwordEncoder = passwordEncoder;
        this.pendingUserInvitations = pendingUserInvitations;
        this.userService = userService;
    }

    /**
     * @return the user's authorities as Spring Security granted authorities
     */
    public List<GrantedAuthority> getGrantedAuthorities(User user) {
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();

        for (Long authorityId : user.getAuthorityIds()) {
            Optional<Authority> authority = authorityService.fetchAuthority(authorityId);

            if (authority.isEmpty()) {
                throw new SsoLoginException(SsoLoginError.PROVISIONING_FAILED);
            }

            grantedAuthorities.add(new SimpleGrantedAuthority(authority.get()
                .getName()));
        }

        return List.copyOf(grantedAuthorities);
    }

    /**
     * Resolves (and links or provisions, when allowed) the ByteChef user for an SSO identity.
     *
     * @throws SsoLoginException when the identity must not sign in
     */
    public User resolveUser(SsoProvider ssoProvider, SsoIdentity ssoIdentity) {
        String subject = ssoIdentity.subject();

        if (subject == null || subject.isBlank() || subject.length() > PROVIDER_ID_MAX_LENGTH) {
            throw new SsoLoginException(SsoLoginError.INVALID_IDENTITY);
        }

        String authProvider = ssoProvider.getAuthProvider();

        Optional<User> linkedUser = userService.fetchUserByAuthProviderAndProviderId(authProvider, subject);

        if (linkedUser.isPresent()) {
            User user = linkedUser.get();

            if (!user.isActivated()) {
                throw new SsoLoginException(SsoLoginError.ACCOUNT_DISABLED);
            }

            updateProfile(user, ssoIdentity);

            userService.save(user);

            return user;
        }

        String email = ssoIdentity.email();

        if (email == null || email.isBlank()) {
            throw new SsoLoginException(SsoLoginError.EMAIL_MISSING);
        }

        String normalizedEmail = email.trim()
            .toLowerCase(Locale.ROOT);

        Optional<String> domain = SsoEmailDomainUtils.extractDomain(normalizedEmail);

        if (domain.isEmpty() || !ssoProvider.getDomains()
            .contains(domain.get())) {

            throw new SsoLoginException(SsoLoginError.EMAIL_DOMAIN_NOT_ALLOWED);
        }

        if (!ssoIdentity.emailVerified()) {
            throw new SsoLoginException(SsoLoginError.EMAIL_NOT_VERIFIED);
        }

        Optional<User> emailUser = userService.fetchUserByEmail(normalizedEmail);

        if (emailUser.isPresent()) {
            return linkUser(emailUser.get(), authProvider, ssoIdentity);
        }

        Optional<InvitationRole> invitationRole = findInvitationRole(normalizedEmail);

        if (invitationRole.isEmpty() && !ssoProvider.isAutoProvision()) {
            throw new SsoLoginException(SsoLoginError.ACCOUNT_NOT_PROVISIONED);
        }

        String authorityName = invitationRole.map(InvitationRole::roleName)
            .orElse(null);
        User user = provisionUser(ssoProvider, ssoIdentity, normalizedEmail, authorityName);

        invitationRole.ifPresent(role -> pendingUserInvitations.getObject()
            .markAccepted(role.id()));

        return user;
    }

    private User linkUser(User user, String authProvider, SsoIdentity ssoIdentity) {
        String currentAuthProvider = user.getAuthProvider();

        boolean local = currentAuthProvider == null || UserConstants.AUTH_PROVIDER_LOCAL.equals(currentAuthProvider);

        if (!local && !(authProvider.equals(currentAuthProvider) && user.getProviderId() == null)) {
            throw new SsoLoginException(SsoLoginError.ACCOUNT_LINKED_ELSEWHERE);
        }

        if (!user.isActivated()) {
            if (!user.isInvited()) {
                throw new SsoLoginException(SsoLoginError.ACCOUNT_DISABLED);
            }

            user.setActivated(true);
            user.setActivationKey(null);
        }

        user.setAuthProvider(authProvider);
        user.setProviderId(ssoIdentity.subject());

        updateProfile(user, ssoIdentity);

        userService.save(user);

        log.info("Linked user id={} to SSO provider {}", user.getId(), authProvider);

        return user;
    }

    private Optional<InvitationRole> findInvitationRole(String email) {
        PendingUserInvitation pendingUserInvitation = pendingUserInvitations.getIfAvailable();

        if (pendingUserInvitation == null) {
            return Optional.empty();
        }

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return Optional.empty();
        }

        HttpServletRequest httpServletRequest = servletRequestAttributes.getRequest();
        Cookie[] cookies = httpServletRequest.getCookies();

        if (cookies == null) {
            return Optional.empty();
        }

        for (Cookie cookie : cookies) {
            if (!INVITATION_COOKIE.equals(cookie.getName())) {
                continue;
            }

            try {
                long invitationId = Long.parseLong(cookie.getValue());

                return pendingUserInvitation.findOpenInvitation(invitationId, email);
            } catch (NumberFormatException numberFormatException) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private User provisionUser(SsoProvider ssoProvider, SsoIdentity ssoIdentity, String email) {
        return provisionUser(ssoProvider, ssoIdentity, email, null);
    }

    private User provisionUser(SsoProvider ssoProvider, SsoIdentity ssoIdentity, String email, String authorityName) {
        // LOGIN_MAX_LENGTH is the tighter of the login and email column limits; the email doubles as the login
        if (email.length() > UserConstants.LOGIN_MAX_LENGTH) {
            throw new SsoLoginException(SsoLoginError.PROVISIONING_FAILED);
        }

        String defaultAuthority = Objects.requireNonNullElse(
            authorityName, Objects.requireNonNullElse(ssoProvider.getDefaultAuthority(), AuthorityConstants.USER));

        Authority authority = authorityService.getAuthorities()
            .stream()
            .filter(candidateAuthority -> defaultAuthority.equals(candidateAuthority.getName()))
            .findFirst()
            .orElseThrow(() -> new SsoLoginException(SsoLoginError.PROVISIONING_FAILED));

        User user = new User();

        user.setActivated(true);
        user.setAuthProvider(ssoProvider.getAuthProvider());
        user.setAuthorities(Set.of(authority));
        user.setEmail(email);
        user.setFirstName(ssoIdentity.firstName());
        user.setLangKey(UserConstants.DEFAULT_LANGUAGE);
        user.setLastName(ssoIdentity.lastName());
        user.setLogin(email);
        user.setPassword(passwordEncoder.encode(generateUnusablePassword()));
        user.setProviderId(ssoIdentity.subject());

        userService.save(user);

        log.info(
            "Provisioned user for SSO provider {} with authority {}", ssoProvider.getAuthProvider(), defaultAuthority);

        return user;
    }

    private String generateUnusablePassword() {
        byte[] randomBytes = new byte[GENERATED_PASSWORD_BYTES];

        secureRandom.nextBytes(randomBytes);

        Base64.Encoder encoder = Base64.getUrlEncoder();

        return encoder.encodeToString(randomBytes);
    }

    private static void updateProfile(User user, SsoIdentity ssoIdentity) {
        if (ssoIdentity.firstName() != null) {
            user.setFirstName(ssoIdentity.firstName());
        }

        if (ssoIdentity.lastName() != null) {
            user.setLastName(ssoIdentity.lastName());
        }
    }
}
