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

package com.bytechef.platform.user.web.graphql;

import com.bytechef.atlas.coordinator.annotation.ConditionalOnCoordinator;
import com.bytechef.platform.user.domain.UserInvitation;
import com.bytechef.platform.user.dto.UserInvitationResult;
import com.bytechef.platform.user.facade.UserInvitationManagementFacade;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * @author Ivica Cardic
 */
@Controller
@ConditionalOnCoordinator
public class UserInvitationGraphQlController {

    private static final String TYPE_NAME = "UserInvitation";

    private final UserInvitationManagementFacade userInvitationManagementFacade;

    @SuppressFBWarnings("EI")
    public UserInvitationGraphQlController(UserInvitationManagementFacade userInvitationManagementFacade) {
        this.userInvitationManagementFacade = userInvitationManagementFacade;
    }

    @SchemaMapping(typeName = TYPE_NAME, field = "createdDate")
    public Long createdDate(UserInvitation userInvitation) {
        return toEpochMilli(userInvitation.getCreatedDate());
    }

    @MutationMapping
    public UserInvitationResult createUserInvitation(@Argument String email, @Argument String role) {
        return userInvitationManagementFacade.createUserInvitation(email, role);
    }

    @SchemaMapping(typeName = TYPE_NAME, field = "expiresAt")
    public Long expiresAt(UserInvitation userInvitation) {
        return toEpochMilli(userInvitation.getExpiresAt());
    }

    @SchemaMapping(typeName = "UserInvitationResult", field = "invitation")
    public UserInvitation invitation(UserInvitationResult userInvitationResult) {
        return userInvitationResult.userInvitation();
    }

    @QueryMapping
    public List<UserInvitation> pendingUserInvitations() {
        return userInvitationManagementFacade.getPendingUserInvitations();
    }

    @MutationMapping
    public UserInvitationResult resendUserInvitation(@Argument long id) {
        return userInvitationManagementFacade.resendUserInvitation(id);
    }

    @SchemaMapping(typeName = TYPE_NAME, field = "role")
    public String role(UserInvitation userInvitation) {
        return userInvitation.getRoleName();
    }

    @MutationMapping
    public boolean revokeUserInvitation(@Argument long id) {
        return userInvitationManagementFacade.revokeUserInvitation(id);
    }

    private static Long toEpochMilli(Instant instant) {
        if (instant == null) {
            return null;
        }

        return instant.toEpochMilli();
    }
}
