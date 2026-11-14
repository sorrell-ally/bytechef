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

import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.service.SsoProviderService;
import com.bytechef.platform.sso.util.SsoEmailDomainUtils;
import com.bytechef.platform.sso.util.SsoProviderPathUtils;
import com.bytechef.tenant.annotation.ConditionalOnSingleTenant;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public SSO discovery used by the login page (both paths are already permit-all in
 * {@code UserAuthorizeHttpRequestContributor}).
 *
 * <ul>
 * <li>{@code POST /api/sso/discover} with {@code {"email": "..."}} (CSRF-protected like every {@code /api/**}
 * POST)</li>
 * <li>{@code GET /api/sso/discover-by-name?company=...} (exact, case-insensitive provider name)</li>
 * </ul>
 *
 * Both always answer {@code 200}: {@code {"providerName": ..., "redirectUrl": ..., "enforced": ...}} when an enabled
 * provider matches, otherwise all fields {@code null}/{@code false}. {@code redirectUrl} is a server-relative path
 * built from the provider's server-generated registration id, never from request input, so it cannot be an open
 * redirect.
 */
@RestController
@RequestMapping("/api/sso")
@ConditionalOnCEVersion
@ConditionalOnSingleTenant
@ConditionalOnProperty(prefix = "bytechef.security.sso", name = "enabled", havingValue = "true")
public class SsoProviderDiscoveryController {

    private static final int MAX_INPUT_LENGTH = 320;

    private final SsoProviderService ssoProviderService;

    @SuppressFBWarnings("EI")
    public SsoProviderDiscoveryController(SsoProviderService ssoProviderService) {
        this.ssoProviderService = ssoProviderService;
    }

    @PostMapping("/discover")
    public SsoDiscoveryResponse discover(@RequestBody(required = false) SsoDiscoveryRequest ssoDiscoveryRequest) {
        if (ssoDiscoveryRequest == null || ssoDiscoveryRequest.email() == null ||
            ssoDiscoveryRequest.email()
                .length() > MAX_INPUT_LENGTH) {

            return SsoDiscoveryResponse.NONE;
        }

        Optional<SsoProvider> ssoProvider = SsoEmailDomainUtils.extractDomain(ssoDiscoveryRequest.email())
            .flatMap(ssoProviderService::fetchEnabledSsoProviderByDomain);

        return toResponse(ssoProvider);
    }

    @GetMapping("/discover-by-name")
    public SsoDiscoveryResponse discoverByName(@RequestParam(name = "company", required = false) String company) {
        if (company == null || company.length() > MAX_INPUT_LENGTH) {
            return SsoDiscoveryResponse.NONE;
        }

        return toResponse(ssoProviderService.fetchEnabledSsoProviderByName(company));
    }

    private static SsoDiscoveryResponse toResponse(Optional<SsoProvider> optionalSsoProvider) {
        if (optionalSsoProvider.isEmpty()) {
            return SsoDiscoveryResponse.NONE;
        }

        SsoProvider ssoProvider = optionalSsoProvider.get();

        return new SsoDiscoveryResponse(
            ssoProvider.getName(), SsoProviderPathUtils.getLoginPath(ssoProvider), ssoProvider.isEnforceSso());
    }

    public record SsoDiscoveryRequest(String email) {
    }

    public record SsoDiscoveryResponse(String providerName, String redirectUrl, boolean enforced) {

        static final SsoDiscoveryResponse NONE = new SsoDiscoveryResponse(null, null, false);
    }
}
