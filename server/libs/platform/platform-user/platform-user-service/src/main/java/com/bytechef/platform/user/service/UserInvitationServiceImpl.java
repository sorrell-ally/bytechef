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

package com.bytechef.platform.user.service;

import com.bytechef.config.ApplicationProperties;
import com.bytechef.exception.ConfigurationException;
import com.bytechef.platform.audit.AuditData;
import com.bytechef.platform.audit.Audited;
import com.bytechef.platform.security.constant.AuthorityConstants;
import com.bytechef.platform.user.audit.UserInvitationAuditEvents;
import com.bytechef.platform.user.domain.Authority;
import com.bytechef.platform.user.domain.User;
import com.bytechef.platform.user.domain.UserInvitation;
import com.bytechef.platform.user.dto.AdminUserDTO;
import com.bytechef.platform.user.dto.UserInvitationResult;
import com.bytechef.platform.user.exception.EmailAlreadyUsedException;
import com.bytechef.platform.user.exception.InvalidEmailException;
import com.bytechef.platform.user.exception.UserErrorType;
import com.bytechef.platform.user.repository.UserInvitationRepository;
import com.bytechef.platform.user.repository.UserRepository;
import com.bytechef.platform.user.validator.PasswordValidator;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * @author Ivica Cardic
 */
@Service
@Transactional
public class UserInvitationServiceImpl implements UserInvitationService {

    private static final EmailValidator EMAIL_VALIDATOR = EmailValidator.getInstance();
    private static final int TOKEN_BYTES = 32;

    private final ApplicationProperties applicationProperties;
    private final AuthenticationPolicyService authenticationPolicyService;
    private final AuthorityService authorityService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final UserInvitationRepository userInvitationRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    @SuppressFBWarnings("EI")
    public UserInvitationServiceImpl(
        ApplicationProperties applicationProperties, AuthenticationPolicyService authenticationPolicyService,
        AuthorityService authorityService, PasswordEncoder passwordEncoder,
        UserInvitationRepository userInvitationRepository, UserRepository userRepository, UserService userService) {

        this.applicationProperties = applicationProperties;
        this.authenticationPolicyService = authenticationPolicyService;
        this.authorityService = authorityService;
        this.passwordEncoder = passwordEncoder;
        this.userInvitationRepository = userInvitationRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    @Audited(event = UserInvitationAuditEvents.USER_INVITATION_ACCEPTED, data = {
        @AuditData(key = "email", value = "#result.email")
    })
    public User acceptWithPassword(String token, String password, String firstName, String lastName) {
        if (authenticationPolicyService.isSsoOnlyEnabled() &&
            !authenticationPolicyService.isPasswordLoginBreakGlass()) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Password registration is turned off. Sign in with your organization's single sign-on provider.");
        }

        PasswordValidator.validate(password);

        UserInvitation userInvitation = requireOpenInvitation(token);
        String email = userInvitation.getEmail();

        if (userService.fetchUserByEmail(email)
            .isPresent()) {

            throw new EmailAlreadyUsedException();
        }

        AdminUserDTO adminUserDTO = new AdminUserDTO();

        adminUserDTO.setLogin(email);
        adminUserDTO.setEmail(email);
        adminUserDTO.setFirstName(firstName);
        adminUserDTO.setLastName(lastName);
        adminUserDTO.setAuthorities(Set.of(userInvitation.getRoleName()));

        User user = userService.create(adminUserDTO);

        user.setPassword(passwordEncoder.encode(password));

        userRepository.save(user);

        markAccepted(userInvitation.getId());

        return user;
    }

    @Override
    public UserInvitationResult createInvitation(String email, String roleName) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedRole = requireRole(roleName);

        if (userService.fetchUserByEmail(normalizedEmail)
            .isPresent()) {

            throw new EmailAlreadyUsedException();
        }

        boolean pendingExists =
            userInvitationRepository.findAllByAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedDateDesc()
                .stream()
                .anyMatch(userInvitation -> userInvitation.isPending(Instant.now()) &&
                    normalizedEmail.equals(userInvitation.getEmail()));

        if (pendingExists) {
            throw new ConfigurationException(
                "An invitation for '" + normalizedEmail + "' is already pending.",
                UserErrorType.INVITATION_ALREADY_PENDING);
        }

        return saveInvitation(new UserInvitation(), normalizedEmail, normalizedRole);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserInvitation> fetchOpenInvitation(String token) {
        return findByToken(token).filter(userInvitation -> userInvitation.isPending(Instant.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvitationRole> findOpenInvitation(long invitationId, String email) {
        return userInvitationRepository.findById(invitationId)
            .filter(userInvitation -> userInvitation.isPending(Instant.now()))
            .filter(userInvitation -> userInvitation.getEmail()
                .equalsIgnoreCase(email))
            .map(userInvitation -> new InvitationRole(userInvitation.getId(), userInvitation.getRoleName()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserInvitation> getPendingInvitations() {
        Instant now = Instant.now();

        return userInvitationRepository.findAllByAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedDateDesc()
            .stream()
            .filter(userInvitation -> userInvitation.isPending(now))
            .toList();
    }

    @Override
    public void markAccepted(long invitationId) {
        UserInvitation userInvitation = userInvitationRepository.findById(invitationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        userInvitation.setAcceptedAt(Instant.now());

        userInvitationRepository.save(userInvitation);
    }

    @Override
    public UserInvitationResult resendInvitation(long id) {
        UserInvitation userInvitation = userInvitationRepository.findById(id)
            .filter(invitation -> invitation.getAcceptedAt() == null && invitation.getRevokedAt() == null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        return saveInvitation(userInvitation, userInvitation.getEmail(), userInvitation.getRoleName());
    }

    @Override
    public void revokeInvitation(long id) {
        UserInvitation userInvitation = userInvitationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        userInvitation.setRevokedAt(Instant.now());

        userInvitationRepository.save(userInvitation);
    }

    private Optional<UserInvitation> findByToken(String token) {
        if (StringUtils.isBlank(token)) {
            return Optional.empty();
        }

        return userInvitationRepository.findByTokenHash(hashToken(token));
    }

    private String hashToken(String token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hash = messageDigest.digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of()
                .formatHex(hash);
        } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
            throw new IllegalStateException(noSuchAlgorithmException);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || !EMAIL_VALIDATOR.isValid(email)) {
            throw new InvalidEmailException(email);
        }

        return email.trim()
            .toLowerCase(Locale.ROOT);
    }

    private UserInvitation requireOpenInvitation(String token) {
        return fetchOpenInvitation(token).orElseThrow(
            () -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This invitation link is invalid or has expired."));
    }

    private String requireRole(String roleName) {
        String normalizedRole = StringUtils.defaultIfBlank(roleName, AuthorityConstants.USER);

        authorityService.getAuthorities()
            .stream()
            .map(Authority::getName)
            .filter(name -> Objects.equals(name, normalizedRole))
            .findFirst()
            .orElseThrow(() -> new ConfigurationException(
                "Invalid role: " + normalizedRole, UserErrorType.INVALID_ROLE));

        return normalizedRole;
    }

    private UserInvitationResult saveInvitation(UserInvitation userInvitation, String email, String roleName) {
        String token = newToken();
        Duration timeToLive = applicationProperties.getSignUp()
            .getInvitationTimeToLive();

        if (timeToLive == null || timeToLive.isNegative() || timeToLive.isZero()) {
            timeToLive = Duration.ofDays(7);
        }

        userInvitation.setEmail(email);
        userInvitation.setRoleName(roleName);
        userInvitation.setTokenHash(hashToken(token));
        userInvitation.setExpiresAt(Instant.now()
            .plus(timeToLive));
        userInvitation.setRevokedAt(null);

        UserInvitation savedInvitation = userInvitationRepository.save(userInvitation);
        String acceptUrl = acceptUrl(token);

        return new UserInvitationResult(savedInvitation, token, acceptUrl, false, null);
    }

    private String acceptUrl(String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String baseUrl = applicationProperties.getMail()
            .getBaseUrl();

        if (StringUtils.isBlank(baseUrl)) {
            return "/accept-invitation?token=" + encodedToken;
        }

        String trimmedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        return trimmedBaseUrl + "/accept-invitation?token=" + encodedToken;
    }

    private String newToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];

        secureRandom.nextBytes(tokenBytes);

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(tokenBytes);
    }
}
