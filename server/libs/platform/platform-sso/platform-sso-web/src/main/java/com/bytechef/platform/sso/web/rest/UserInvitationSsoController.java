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

package com.bytechef.platform.sso.web.rest;

import static com.bytechef.platform.sso.web.authentication.SsoUserResolver.INVITATION_COOKIE;

import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.service.SsoProviderService;
import com.bytechef.platform.sso.util.SsoEmailDomainUtils;
import com.bytechef.platform.sso.util.SsoProviderPathUtils;
import com.bytechef.platform.user.domain.UserInvitation;
import com.bytechef.platform.user.service.UserInvitationService;
import com.bytechef.tenant.annotation.ConditionalOnSingleTenant;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Starts an SSO sign-in that can accept a pending invitation. The cookie holds the invitation id, not the secret token,
 * and only after the token has been checked.
 *
 * @author Ivica Cardic
 */
@RestController
@RequestMapping("/api")
@ConditionalOnCEVersion
@ConditionalOnSingleTenant
@ConditionalOnProperty(prefix = "bytechef.security.sso", name = "enabled", havingValue = "true")
public class UserInvitationSsoController {

    private final SsoProviderService ssoProviderService;
    private final UserInvitationService userInvitationService;

    @SuppressFBWarnings("EI")
    public UserInvitationSsoController(
        SsoProviderService ssoProviderService, UserInvitationService userInvitationService) {

        this.ssoProviderService = ssoProviderService;
        this.userInvitationService = userInvitationService;
    }

    @PostMapping("/user-invitations/sso")
    public SsoStart startSso(@RequestBody TokenRequest tokenRequest, HttpServletResponse httpServletResponse) {
        UserInvitation userInvitation = userInvitationService.fetchOpenInvitation(tokenRequest.token())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String domain = SsoEmailDomainUtils.extractDomain(userInvitation.getEmail())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        SsoProvider ssoProvider = ssoProviderService.fetchEnabledSsoProviderByDomain(domain)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "No enabled single sign-on provider serves this email domain."));

        Duration maxAge = Duration.between(Instant.now(), userInvitation.getExpiresAt());

        if (maxAge.isNegative() || maxAge.isZero()) {
            maxAge = Duration.ofMinutes(1);
        }

        ResponseCookie responseCookie = ResponseCookie.from(INVITATION_COOKIE, String.valueOf(userInvitation.getId()))
            .httpOnly(true)
            .path("/")
            .sameSite("Lax")
            .maxAge(maxAge)
            .build();

        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());

        return new SsoStart(SsoProviderPathUtils.getLoginPath(ssoProvider));
    }

    public record SsoStart(String redirectUrl) {
    }

    public record TokenRequest(String token) {
    }
}
