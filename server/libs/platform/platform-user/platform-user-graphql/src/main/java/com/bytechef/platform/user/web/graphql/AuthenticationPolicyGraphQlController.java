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
import com.bytechef.platform.user.dto.AuthenticationPolicyDTO;
import com.bytechef.platform.user.facade.AuthenticationPolicyFacade;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * @author Ivica Cardic
 */
@Controller
@ConditionalOnCoordinator
public class AuthenticationPolicyGraphQlController {

    private final AuthenticationPolicyFacade authenticationPolicyFacade;

    @SuppressFBWarnings("EI")
    public AuthenticationPolicyGraphQlController(AuthenticationPolicyFacade authenticationPolicyFacade) {
        this.authenticationPolicyFacade = authenticationPolicyFacade;
    }

    @QueryMapping
    public AuthenticationPolicyDTO authenticationPolicy() {
        return authenticationPolicyFacade.getAuthenticationPolicy();
    }

    @MutationMapping
    public AuthenticationPolicyDTO updateSelfSignUpEnabled(@Argument boolean enabled) {
        return authenticationPolicyFacade.updateSelfSignUpEnabled(enabled);
    }

    @MutationMapping
    public AuthenticationPolicyDTO updateSsoOnlyEnabled(@Argument boolean enabled) {
        return authenticationPolicyFacade.updateSsoOnlyEnabled(enabled);
    }
}
