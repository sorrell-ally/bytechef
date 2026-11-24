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
import com.bytechef.platform.user.dto.AuthenticationPolicyDTO;

/**
 * Reads and stores the organization authentication policy. Callers that enforce the policy (registration, password
 * login) use this service. Administrator changes go through {@code AuthenticationPolicyFacade}, which audits them and
 * checks the SSO-only guards.
 *
 * @author Ivica Cardic
 */
public interface AuthenticationPolicyService {

    AuthenticationPolicyDTO getAuthenticationPolicy();

    /**
     * @return whether {@code bytechef.security.password-login-break-glass} is on
     */
    boolean isPasswordLoginBreakGlass();

    /**
     * @return the self sign-up value in effect: the saved choice, or {@code bytechef.sign-up.enabled} until an
     *         administrator saves one
     */
    boolean isSelfSignUpEnabled();

    boolean isSsoOnlyEnabled();

    /**
     * @return whether {@code user} is an activated administrator
     */
    boolean isActivatedAdmin(User user);

    void updateSelfSignUpEnabled(boolean enabled);

    void updateSsoOnlyEnabled(boolean enabled);
}
