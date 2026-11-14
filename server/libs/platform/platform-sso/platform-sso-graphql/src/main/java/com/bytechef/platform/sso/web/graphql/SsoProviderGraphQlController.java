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

package com.bytechef.platform.sso.web.graphql;

import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import com.bytechef.platform.security.constant.AuthorityConstants;
import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.service.SsoProviderService;
import com.bytechef.platform.sso.util.SsoProviderPathUtils;
import com.bytechef.tenant.annotation.ConditionalOnSingleTenant;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

/**
 * Admin-only GraphQL API over {@link SsoProvider}. The OIDC client secret is write-only: the {@code SsoProvider} type
 * has no field for it, only {@code clientSecretSet}.
 */
@Controller
@ConditionalOnCEVersion
@ConditionalOnSingleTenant
@ConditionalOnProperty(prefix = "bytechef.security.sso", name = "enabled", havingValue = "true")
@PreAuthorize("hasAuthority(\"" + AuthorityConstants.ADMIN + "\")")
public class SsoProviderGraphQlController {

    private static final String TYPE_NAME = "SsoProvider";

    private final SsoProviderService ssoProviderService;

    @SuppressFBWarnings("EI")
    public SsoProviderGraphQlController(SsoProviderService ssoProviderService) {
        this.ssoProviderService = ssoProviderService;
    }

    @SchemaMapping(typeName = TYPE_NAME, field = "callbackPath")
    public String callbackPath(SsoProvider ssoProvider) {
        return SsoProviderPathUtils.getCallbackPath(ssoProvider);
    }

    @SchemaMapping(typeName = TYPE_NAME, field = "clientSecretSet")
    public boolean clientSecretSet(SsoProvider ssoProvider) {
        return ssoProvider.hasClientSecret();
    }

    @MutationMapping
    public SsoProvider createSsoProvider(@Argument SsoProviderInput input) {
        return ssoProviderService.create(input.toSsoProvider());
    }

    @SchemaMapping(typeName = TYPE_NAME, field = "createdDate")
    public Long createdDate(SsoProvider ssoProvider) {
        return toEpochMilli(ssoProvider.getCreatedDate());
    }

    @MutationMapping
    public boolean deleteSsoProvider(@Argument long id) {
        ssoProviderService.delete(id);

        return true;
    }

    @SchemaMapping(typeName = TYPE_NAME, field = "lastModifiedDate")
    public Long lastModifiedDate(SsoProvider ssoProvider) {
        return toEpochMilli(ssoProvider.getLastModifiedDate());
    }

    @SchemaMapping(typeName = TYPE_NAME, field = "loginPath")
    public String loginPath(SsoProvider ssoProvider) {
        return SsoProviderPathUtils.getLoginPath(ssoProvider);
    }

    @SchemaMapping(typeName = TYPE_NAME, field = "serviceProviderMetadataPath")
    public String serviceProviderMetadataPath(SsoProvider ssoProvider) {
        return SsoProviderPathUtils.getServiceProviderMetadataPath(ssoProvider);
    }

    @QueryMapping
    public SsoProvider ssoProvider(@Argument long id) {
        return ssoProviderService.getSsoProvider(id);
    }

    @QueryMapping
    public List<SsoProvider> ssoProviders() {
        return ssoProviderService.getSsoProviders();
    }

    @MutationMapping
    public SsoProvider updateSsoProvider(@Argument long id, @Argument SsoProviderInput input) {
        SsoProvider ssoProvider = input.toSsoProvider();

        ssoProvider.setId(id);

        return ssoProviderService.update(ssoProvider);
    }

    private static Long toEpochMilli(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
