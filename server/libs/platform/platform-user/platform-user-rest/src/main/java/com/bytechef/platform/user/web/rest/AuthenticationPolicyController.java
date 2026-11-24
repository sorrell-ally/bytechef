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
import com.bytechef.platform.user.dto.AuthenticationPolicyDTO;
import com.bytechef.platform.user.service.AuthenticationPolicyService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read of the values the login and registration screens need. Administrator changes are on the GraphQL API.
 *
 * @author Ivica Cardic
 */
@RestController
@RequestMapping("/api")
@ConditionalOnCoordinator
public class AuthenticationPolicyController {

    private final AuthenticationPolicyService authenticationPolicyService;

    @SuppressFBWarnings("EI")
    public AuthenticationPolicyController(AuthenticationPolicyService authenticationPolicyService) {
        this.authenticationPolicyService = authenticationPolicyService;
    }

    @GetMapping("/authentication-policy")
    public AuthenticationPolicyResponse getAuthenticationPolicy() {
        AuthenticationPolicyDTO authenticationPolicy = authenticationPolicyService.getAuthenticationPolicy();

        return new AuthenticationPolicyResponse(
            authenticationPolicy.signUpEnabled(), authenticationPolicy.ssoOnlyEnabled());
    }

    public record AuthenticationPolicyResponse(boolean signUpEnabled, boolean ssoOnly) {
    }
}
