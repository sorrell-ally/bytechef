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

package com.bytechef.platform.user.audit;

/**
 * @author Ivica Cardic
 */
public final class UserInvitationAuditEvents {

    public static final String USER_INVITATION_ACCEPTED = "USER_INVITATION_ACCEPTED";
    public static final String USER_INVITATION_CREATED = "USER_INVITATION_CREATED";
    public static final String USER_INVITATION_RESENT = "USER_INVITATION_RESENT";
    public static final String USER_INVITATION_REVOKED = "USER_INVITATION_REVOKED";

    private UserInvitationAuditEvents() {
    }
}
