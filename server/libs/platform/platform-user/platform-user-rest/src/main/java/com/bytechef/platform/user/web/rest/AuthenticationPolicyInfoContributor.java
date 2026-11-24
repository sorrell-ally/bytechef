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

import com.bytechef.platform.user.service.AuthenticationPolicyService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * Publishes the effective authentication policy on {@code /actuator/info} under {@code authenticationPolicy}. Values
 * are strings, matching the other info properties the client reads. A failure leaves the environment sign-up property
 * in place.
 *
 * @author Ivica Cardic
 */
@Component
public class AuthenticationPolicyInfoContributor implements InfoContributor {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationPolicyInfoContributor.class);

    private final AuthenticationPolicyService authenticationPolicyService;

    @SuppressFBWarnings("EI")
    public AuthenticationPolicyInfoContributor(AuthenticationPolicyService authenticationPolicyService) {
        this.authenticationPolicyService = authenticationPolicyService;
    }

    @Override
    public void contribute(Info.Builder builder) {
        try {
            Map<String, String> authenticationPolicy = new LinkedHashMap<>();

            authenticationPolicy.put(
                "signUpEnabled", Boolean.toString(authenticationPolicyService.isSelfSignUpEnabled()));
            authenticationPolicy.put("ssoOnly", Boolean.toString(authenticationPolicyService.isSsoOnlyEnabled()));

            builder.withDetail("authenticationPolicy", authenticationPolicy);
        } catch (RuntimeException runtimeException) {
            log.warn("Authentication policy could not be added to actuator info", runtimeException);
        }
    }
}
