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

package com.bytechef.platform.user.facade;

import com.bytechef.atlas.coordinator.annotation.ConditionalOnCoordinator;
import com.bytechef.platform.audit.AuditData;
import com.bytechef.platform.audit.Audited;
import com.bytechef.platform.mail.MailService;
import com.bytechef.platform.security.constant.AuthorityConstants;
import com.bytechef.platform.user.audit.UserInvitationAuditEvents;
import com.bytechef.platform.user.domain.UserInvitation;
import com.bytechef.platform.user.dto.UserInvitationResult;
import com.bytechef.platform.user.service.UserInvitationService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import org.springframework.mail.MailException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Creates invitations even when mail cannot be sent. The raw link is returned to the administrator so they can copy it.
 * The dev deploy has no SMTP server until {@code BYTECHEF_MAIL_HOST} and the related mail properties are set.
 *
 * @author Ivica Cardic
 */
@Service
@ConditionalOnCoordinator
public class UserInvitationManagementFacadeImpl implements UserInvitationManagementFacade {

    static final String MAIL_NOT_CONFIGURED =
        "No mail server is configured. Copy the invitation link and send it yourself.";
    static final String MAIL_NOT_SENT =
        "The invitation was saved, but the email could not be sent. Copy the invitation link.";

    private final MailService mailService;
    private final UserInvitationService userInvitationService;

    @SuppressFBWarnings("EI")
    public UserInvitationManagementFacadeImpl(MailService mailService, UserInvitationService userInvitationService) {
        this.mailService = mailService;
        this.userInvitationService = userInvitationService;
    }

    @Override
    @Audited(event = UserInvitationAuditEvents.USER_INVITATION_CREATED, data = {
        @AuditData(key = "email", value = "#email"),
        @AuditData(key = "role", value = "#role")
    })
    @PreAuthorize("hasAuthority(\"" + AuthorityConstants.ADMIN + "\")")
    public UserInvitationResult createUserInvitation(String email, String role) {
        return deliver(userInvitationService.createInvitation(email, role));
    }

    @Override
    @PreAuthorize("hasAuthority(\"" + AuthorityConstants.ADMIN + "\")")
    public List<UserInvitation> getPendingUserInvitations() {
        return userInvitationService.getPendingInvitations();
    }

    @Override
    @Audited(event = UserInvitationAuditEvents.USER_INVITATION_RESENT, data = {
        @AuditData(key = "id", value = "#id")
    })
    @PreAuthorize("hasAuthority(\"" + AuthorityConstants.ADMIN + "\")")
    public UserInvitationResult resendUserInvitation(long id) {
        return deliver(userInvitationService.resendInvitation(id));
    }

    @Override
    @Audited(event = UserInvitationAuditEvents.USER_INVITATION_REVOKED, data = {
        @AuditData(key = "id", value = "#id")
    })
    @PreAuthorize("hasAuthority(\"" + AuthorityConstants.ADMIN + "\")")
    public boolean revokeUserInvitation(long id) {
        userInvitationService.revokeInvitation(id);

        return true;
    }

    private UserInvitationResult deliver(UserInvitationResult userInvitationResult) {
        if (!mailService.isMailConfigured()) {
            return withDelivery(userInvitationResult, false, MAIL_NOT_CONFIGURED);
        }

        try {
            mailService.sendUserInvitationEmail(
                userInvitationResult.userInvitation()
                    .getEmail(),
                userInvitationResult.acceptUrl());

            return withDelivery(userInvitationResult, true, null);
        } catch (MailException | IllegalStateException exception) {
            return withDelivery(userInvitationResult, false, MAIL_NOT_SENT);
        }
    }

    private static UserInvitationResult withDelivery(
        UserInvitationResult userInvitationResult, boolean mailed, String deliveryError) {

        return new UserInvitationResult(
            userInvitationResult.userInvitation(), userInvitationResult.token(), userInvitationResult.acceptUrl(),
            mailed, deliveryError);
    }
}
