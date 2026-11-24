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

package com.bytechef.platform.user.web.rest;

import com.bytechef.atlas.coordinator.annotation.ConditionalOnCoordinator;
import com.bytechef.platform.user.domain.UserInvitation;
import com.bytechef.platform.user.service.AuthenticationPolicyService;
import com.bytechef.platform.user.service.UserInvitationService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public invitation acceptance. Self sign-up being off does not block an invitation.
 *
 * @author Ivica Cardic
 */
@RestController
@RequestMapping("/api")
@ConditionalOnCoordinator
public class UserInvitationController {

    private final AuthenticationPolicyService authenticationPolicyService;
    private final UserInvitationService userInvitationService;

    @SuppressFBWarnings("EI")
    public UserInvitationController(
        AuthenticationPolicyService authenticationPolicyService, UserInvitationService userInvitationService) {

        this.authenticationPolicyService = authenticationPolicyService;
        this.userInvitationService = userInvitationService;
    }

    @PostMapping("/account/accept-user-invitation")
    public void acceptUserInvitation(@RequestBody AcceptUserInvitationRequest acceptUserInvitationRequest) {
        userInvitationService.acceptWithPassword(
            acceptUserInvitationRequest.token(), acceptUserInvitationRequest.password(),
            acceptUserInvitationRequest.firstName(), acceptUserInvitationRequest.lastName());
    }

    @GetMapping("/user-invitations/preview")
    public UserInvitationPreview getUserInvitationPreview(@RequestParam("token") String token) {
        UserInvitation userInvitation = userInvitationService.fetchOpenInvitation(token)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        return new UserInvitationPreview(
            userInvitation.getEmail(), authenticationPolicyService.isSsoOnlyEnabled(),
            userInvitation.getExpiresAt());
    }

    public record AcceptUserInvitationRequest(String token, String password, String firstName, String lastName) {
    }

    public record UserInvitationPreview(String email, boolean ssoOnly, Instant expiresAt) {
    }
}
