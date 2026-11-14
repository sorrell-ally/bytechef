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

package com.bytechef.platform.sso.web.filter;

import com.bytechef.platform.security.constant.AuthorityConstants;
import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.service.SsoProviderService;
import com.bytechef.platform.sso.util.SsoEmailDomainUtils;
import com.bytechef.platform.sso.util.SsoProviderPathUtils;
import com.bytechef.platform.user.constant.UserConstants;
import com.bytechef.platform.user.domain.Authority;
import com.bytechef.platform.user.domain.User;
import com.bytechef.platform.user.service.AuthorityService;
import com.bytechef.platform.user.service.UserService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JsonWriter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses password login ({@code POST /api/authentication}) for users whose email domain is served by an enabled SSO
 * provider with {@code enforceSso} on.
 *
 * <p>
 * <b>Break-glass exception:</b> an activated user that holds {@code ROLE_ADMIN} and whose {@code auth_provider} is
 * still {@code LOCAL} (the account has never been linked to an SSO identity) may keep using a password, so a
 * misconfigured provider cannot lock every administrator out. Once such an admin signs in through SSO the account is
 * linked and the exception no longer applies to it. The last-resort recovery is operational: set
 * {@code bytechef.security.sso.enabled=false} and restart, which removes this filter entirely.
 *
 * <p>
 * The refusal is {@code 403} with a JSON body the login page can show and act on:
 * {@code {"error":"sso_required","message":"...","providerName":"...","redirectUrl":"/oauth2/authorization/sso-..."}}.
 * It does not depend on whether the account exists, so it reveals nothing beyond what {@code /api/sso/discover} already
 * does for the domain (only the break-glass admins fall through to the normal password check).
 */
public class SsoPasswordLoginEnforcementFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE = "sso_required";
    public static final String LOGIN_PROCESSING_PATH = "/api/authentication";

    private static final Logger log = LoggerFactory.getLogger(SsoPasswordLoginEnforcementFilter.class);

    private static final String USERNAME_PARAMETER = "username";

    private final AuthorityService authorityService;
    private final SsoProviderService ssoProviderService;
    private final UserService userService;

    @SuppressFBWarnings("EI2")
    public SsoPasswordLoginEnforcementFilter(
        AuthorityService authorityService, SsoProviderService ssoProviderService, UserService userService) {

        this.authorityService = authorityService;
        this.ssoProviderService = ssoProviderService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String username = request.getParameter(USERNAME_PARAMETER);

        Optional<SsoProvider> enforcingSsoProvider = findEnforcingSsoProvider(username);

        if (enforcingSsoProvider.isEmpty()) {
            filterChain.doFilter(request, response);

            return;
        }

        SsoProvider ssoProvider = enforcingSsoProvider.get();

        Optional<User> user = findUser(username);

        if (user.isPresent() && isBreakGlassUser(user.get())) {
            log.info(
                "Allowing password login despite enforced SSO provider {}: local administrator break-glass",
                ssoProvider.getRegistrationId());

            filterChain.doFilter(request, response);

            return;
        }

        writeSsoRequiredResponse(response, ssoProvider);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) ||
            !LOGIN_PROCESSING_PATH.equals(request.getServletPath());
    }

    Optional<SsoProvider> findEnforcingSsoProvider(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        String email = username;

        if (!username.contains("@")) {
            Optional<User> user = userService.fetchUserByLogin(username.trim());

            if (user.isEmpty()) {
                return Optional.empty();
            }

            email = user.get()
                .getEmail();
        }

        return SsoEmailDomainUtils.extractDomain(email)
            .flatMap(ssoProviderService::fetchEnabledSsoProviderByDomain)
            .filter(SsoProvider::isEnforceSso);
    }

    boolean isBreakGlassUser(User user) {
        String authProvider = user.getAuthProvider();

        if (!user.isActivated() ||
            (authProvider != null && !UserConstants.AUTH_PROVIDER_LOCAL.equals(authProvider))) {

            return false;
        }

        for (Long authorityId : user.getAuthorityIds()) {
            Optional<String> authorityName = authorityService.fetchAuthority(authorityId)
                .map(Authority::getName);

            if (authorityName.isPresent() && AuthorityConstants.ADMIN.equals(authorityName.get())) {
                return true;
            }
        }

        return false;
    }

    private Optional<User> findUser(String username) {
        String trimmedUsername = username.trim();

        if (trimmedUsername.contains("@")) {
            return userService.fetchUserByEmail(trimmedUsername);
        }

        return userService.fetchUserByLogin(trimmedUsername);
    }

    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "application/json body serialized by JsonWriter")
    private static void writeSsoRequiredResponse(HttpServletResponse response, SsoProvider ssoProvider)
        throws IOException {

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("error", ERROR_CODE);
        body.put("message", "Your organization requires signing in with " + ssoProvider.getName() + ".");
        body.put("providerName", ssoProvider.getName());
        body.put("redirectUrl", SsoProviderPathUtils.getLoginPath(ssoProvider));

        JsonWriter<Object> jsonWriter = JsonWriter.standard();

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        response.getWriter()
            .write(jsonWriter.writeToString(body));
    }
}
