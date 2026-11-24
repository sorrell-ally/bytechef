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

import com.bytechef.platform.user.domain.User;
import com.bytechef.platform.user.domain.UserInvitation;
import com.bytechef.platform.user.dto.UserInvitationResult;
import java.util.List;
import java.util.Optional;

/**
 * @author Ivica Cardic
 */
public interface UserInvitationService extends PendingUserInvitation {

    User acceptWithPassword(String token, String password, String firstName, String lastName);

    UserInvitationResult createInvitation(String email, String roleName);

    List<UserInvitation> getPendingInvitations();

    Optional<UserInvitation> fetchOpenInvitation(String token);

    UserInvitationResult resendInvitation(long id);

    void revokeInvitation(long id);
}
