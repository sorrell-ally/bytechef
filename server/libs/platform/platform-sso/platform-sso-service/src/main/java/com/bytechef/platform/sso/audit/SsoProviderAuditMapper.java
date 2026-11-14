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

package com.bytechef.platform.sso.audit;

import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import com.bytechef.platform.audit.AuditInvocation;
import com.bytechef.platform.audit.AuditMapper;
import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.domain.SsoProviderType;
import com.bytechef.platform.sso.repository.SsoProviderRepository;
import com.bytechef.tenant.annotation.ConditionalOnSingleTenant;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Records which provider changed and its security-relevant switches. Secrets, metadata and client ids are never
 * included.
 */
@Component
@ConditionalOnCEVersion
@ConditionalOnSingleTenant
@ConditionalOnProperty(prefix = "bytechef.security.sso", name = "enabled", havingValue = "true")
public class SsoProviderAuditMapper implements AuditMapper {

    private final SsoProviderRepository ssoProviderRepository;

    @SuppressFBWarnings("EI")
    public SsoProviderAuditMapper(SsoProviderRepository ssoProviderRepository) {
        this.ssoProviderRepository = ssoProviderRepository;
    }

    @Override
    public @Nullable Object capture(AuditInvocation auditInvocation) {
        if (!SsoProviderAuditEvents.SSO_PROVIDER_DELETED.equals(auditInvocation.event())) {
            return null;
        }

        long ssoProviderId = (Long) auditInvocation.requireArgument("id");

        return ssoProviderRepository.findById(ssoProviderId)
            .map(SsoProvider::getName)
            .orElse(null);
    }

    @Override
    public Map<String, String> map(AuditInvocation auditInvocation) {
        Map<String, String> data = new LinkedHashMap<>();

        if (SsoProviderAuditEvents.SSO_PROVIDER_DELETED.equals(auditInvocation.event())) {
            data.put("ssoProviderId", String.valueOf(auditInvocation.requireArgument("id")));

            if (auditInvocation.captured() instanceof String name) {
                data.put("name", name);
            }

            return data;
        }

        SsoProvider ssoProvider = auditInvocation.result() instanceof SsoProvider savedSsoProvider
            ? savedSsoProvider : (SsoProvider) auditInvocation.requireArgument("ssoProvider");

        Long ssoProviderId = ssoProvider.getId();

        if (ssoProviderId != null) {
            data.put("ssoProviderId", String.valueOf(ssoProviderId));
        }

        String name = ssoProvider.getName();

        if (name != null) {
            data.put("name", name);
        }

        SsoProviderType type = ssoProvider.getType();

        if (type != null) {
            data.put("type", type.name());
        }

        data.put("vendor", String.valueOf(ssoProvider.getVendor()));
        data.put("enabled", String.valueOf(ssoProvider.isEnabled()));
        data.put("enforceSso", String.valueOf(ssoProvider.isEnforceSso()));
        data.put("autoProvision", String.valueOf(ssoProvider.isAutoProvision()));
        data.put("defaultAuthority", String.valueOf(ssoProvider.getDefaultAuthority()));
        data.put("domains", String.join(",", ssoProvider.getDomains()));

        return data;
    }
}
