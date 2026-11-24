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

import java.util.Optional;

/**
 * Lets single sign-on accept a pending invitation for the asserted email. The invitation id comes from the cookie set
 * when the invitee presented the secret token.
 *
 * @author Ivica Cardic
 */
public interface PendingUserInvitation {

    Optional<InvitationRole> findOpenInvitation(long invitationId, String email);

    void markAccepted(long invitationId);

    record InvitationRole(long id, String roleName) {
    }
}
