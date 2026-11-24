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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bytechef.config.ApplicationProperties;
import com.bytechef.platform.security.constant.AuthorityConstants;
import com.bytechef.platform.user.domain.Authority;
import com.bytechef.platform.user.domain.UserInvitation;
import com.bytechef.platform.user.dto.UserInvitationResult;
import com.bytechef.platform.user.repository.UserInvitationRepository;
import com.bytechef.platform.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/**
 * @author Ivica Cardic
 */
class UserInvitationServiceTest {

    private AuthenticationPolicyService authenticationPolicyService;
    private UserInvitationRepository userInvitationRepository;
    private UserInvitationService userInvitationService;
    private UserService userService;

    @BeforeEach
    void beforeEach() {
        ApplicationProperties applicationProperties = new ApplicationProperties();

        applicationProperties.getSignUp()
            .setInvitationTimeToLive(Duration.ofDays(7));

        authenticationPolicyService = mock(AuthenticationPolicyService.class);
        AuthorityService authorityService = mock(AuthorityService.class);
        userInvitationRepository = mock(UserInvitationRepository.class);
        userService = mock(UserService.class);

        when(authorityService.getAuthorities()).thenReturn(List.of(new Authority().name(AuthorityConstants.USER)));
        when(userInvitationRepository.findAllByAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedDateDesc())
            .thenReturn(List.of());
        when(userInvitationRepository.save(any(UserInvitation.class))).thenAnswer(invocation -> {
            UserInvitation userInvitation = invocation.getArgument(0);

            userInvitation.setId(4L);

            return userInvitation;
        });
        when(userService.fetchUserByEmail(any())).thenReturn(Optional.empty());

        userInvitationService = new UserInvitationServiceImpl(
            applicationProperties, authenticationPolicyService, authorityService, mock(PasswordEncoder.class),
            userInvitationRepository, mock(UserRepository.class), userService);
    }

    @Test
    void testCreateStoresAHashAndNotTheToken() {
        UserInvitationResult userInvitationResult = userInvitationService.createInvitation(
            "Person@Example.com", AuthorityConstants.USER);

        assertThat(userInvitationResult.token()).isNotBlank();
        assertThat(userInvitationResult.userInvitation()
            .getTokenHash()).isNotEqualTo(userInvitationResult.token());
        assertThat(userInvitationResult.userInvitation()
            .getTokenHash()).hasSize(64);
        assertThat(userInvitationResult.userInvitation()
            .getEmail()).isEqualTo("person@example.com");
        assertThat(userInvitationResult.acceptUrl()).contains("token=");
        assertThat(userInvitationResult.userInvitation()
            .getExpiresAt()).isAfter(Instant.now());

        verify(userInvitationRepository).save(any(UserInvitation.class));
    }

    @Test
    void testAnExpiredTokenCannotBePreviewed() {
        UserInvitationResult userInvitationResult = userInvitationService.createInvitation(
            "person@example.com", AuthorityConstants.USER);
        UserInvitation userInvitation = userInvitationResult.userInvitation();

        userInvitation.setExpiresAt(Instant.now()
            .minusSeconds(5));

        when(userInvitationRepository.findByTokenHash(userInvitation.getTokenHash()))
            .thenReturn(Optional.of(userInvitation));

        assertThat(userInvitationService.fetchOpenInvitation(userInvitationResult.token())).isEmpty();
    }

    @Test
    void testSsoOnlyRefusesPasswordAcceptance() {
        when(authenticationPolicyService.isSsoOnlyEnabled()).thenReturn(true);

        assertThatThrownBy(() -> userInvitationService.acceptWithPassword("token", "Password1", "A", "B"))
            .isInstanceOf(ResponseStatusException.class);

        verify(userService, never()).create(any());
    }
}
