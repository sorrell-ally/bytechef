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

package com.bytechef.platform.sso.service;

import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import com.bytechef.platform.audit.Audited;
import com.bytechef.platform.security.constant.AuthorityConstants;
import com.bytechef.platform.sso.audit.SsoProviderAuditEvents;
import com.bytechef.platform.sso.audit.SsoProviderAuditMapper;
import com.bytechef.platform.sso.domain.SsoProvider;
import com.bytechef.platform.sso.domain.SsoProviderType;
import com.bytechef.platform.sso.domain.SsoProviderVendor;
import com.bytechef.platform.sso.repository.SsoProviderRepository;
import com.bytechef.platform.sso.util.SsoEmailDomainUtils;
import com.bytechef.platform.sso.util.SsoMicrosoftEntraUtils;
import com.bytechef.platform.sso.util.SsoRemoteUrlValidator;
import com.bytechef.tenant.annotation.ConditionalOnSingleTenant;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates and persists SSO providers.
 *
 * <p>
 * Admin-only operations are guarded with {@code @PreAuthorize}; the {@code fetch*} lookups serve the unauthenticated
 * login path and are intentionally unguarded.
 */
@Service
@Transactional
@ConditionalOnCEVersion
@ConditionalOnSingleTenant
@ConditionalOnProperty(prefix = "bytechef.security.sso", name = "enabled", havingValue = "true")
public class SsoProviderServiceImpl implements SsoProviderService {

    static final String REGISTRATION_ID_PREFIX = "sso-";

    private static final String ADMIN_ONLY = "hasAuthority(\"" + AuthorityConstants.ADMIN + "\")";
    private static final Set<String> ALLOWED_DEFAULT_AUTHORITIES = Set.of(
        AuthorityConstants.USER, AuthorityConstants.ADMIN);
    private static final List<String> DEFAULT_OIDC_SCOPES = List.of("openid", "profile", "email");
    private static final int MAX_METADATA_XML_BYTES = 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 256;
    private static final String OPENID_SCOPE = "openid";
    private static final String PROFILE_SCOPE = "profile";
    private static final int REGISTRATION_ID_RANDOM_BYTES = 8;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SsoProviderRepository ssoProviderRepository;

    @SuppressFBWarnings("EI")
    public SsoProviderServiceImpl(SsoProviderRepository ssoProviderRepository) {
        this.ssoProviderRepository = ssoProviderRepository;
    }

    @Override
    @PreAuthorize(ADMIN_ONLY)
    @Audited(event = SsoProviderAuditEvents.SSO_PROVIDER_CREATED, mapper = SsoProviderAuditMapper.class)
    public SsoProvider create(SsoProvider ssoProvider) {
        if (ssoProvider.getId() != null) {
            throw new IllegalArgumentException("id must be null");
        }

        if (ssoProvider.getType() == null) {
            throw new IllegalArgumentException("type is required");
        }

        ssoProvider.setRegistrationId(generateRegistrationId());

        validateAndNormalize(ssoProvider, null);

        return ssoProviderRepository.save(ssoProvider);
    }

    @Override
    @PreAuthorize(ADMIN_ONLY)
    @Audited(event = SsoProviderAuditEvents.SSO_PROVIDER_DELETED, mapper = SsoProviderAuditMapper.class)
    public void delete(long id) {
        ssoProviderRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SsoProvider> fetchEnabledSsoProviderByDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return Optional.empty();
        }

        String normalizedDomain = domain.trim()
            .toLowerCase(Locale.ROOT);

        return ssoProviderRepository.findAllByEnabled(true)
            .stream()
            .filter(ssoProvider -> ssoProvider.getDomains()
                .contains(normalizedDomain))
            .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SsoProvider> fetchEnabledSsoProviderByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        String trimmedName = name.trim();

        return ssoProviderRepository.findAllByEnabled(true)
            .stream()
            .filter(ssoProvider -> trimmedName.equalsIgnoreCase(ssoProvider.getName()))
            .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SsoProvider> fetchSsoProviderByRegistrationId(String registrationId) {
        if (registrationId == null || !registrationId.startsWith(REGISTRATION_ID_PREFIX)) {
            return Optional.empty();
        }

        return ssoProviderRepository.findByRegistrationId(registrationId);
    }

    @Override
    @PreAuthorize(ADMIN_ONLY)
    @Transactional(readOnly = true)
    public SsoProvider getSsoProvider(long id) {
        return ssoProviderRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("SSO provider " + id + " does not exist"));
    }

    @Override
    @PreAuthorize(ADMIN_ONLY)
    @Transactional(readOnly = true)
    public List<SsoProvider> getSsoProviders() {
        return ssoProviderRepository.findAllByOrderByNameAsc();
    }

    @Override
    @PreAuthorize(ADMIN_ONLY)
    @Audited(event = SsoProviderAuditEvents.SSO_PROVIDER_UPDATED, mapper = SsoProviderAuditMapper.class)
    public SsoProvider update(SsoProvider ssoProvider) {
        Long id = Objects.requireNonNull(ssoProvider.getId(), "id is required");

        SsoProvider currentSsoProvider = ssoProviderRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("SSO provider " + id + " does not exist"));

        currentSsoProvider.setAutoProvision(ssoProvider.isAutoProvision());
        currentSsoProvider.setClientId(ssoProvider.getClientId());
        currentSsoProvider.setDefaultAuthority(ssoProvider.getDefaultAuthority());
        currentSsoProvider.setDomains(ssoProvider.getDomains());
        currentSsoProvider.setEnabled(ssoProvider.isEnabled());
        currentSsoProvider.setEnforceSso(ssoProvider.isEnforceSso());
        currentSsoProvider.setIssuerUri(ssoProvider.getIssuerUri());
        currentSsoProvider.setMetadataUrl(ssoProvider.getMetadataUrl());
        currentSsoProvider.setMetadataXml(ssoProvider.getMetadataXml());
        currentSsoProvider.setName(ssoProvider.getName());
        currentSsoProvider.setScopes(ssoProvider.getScopes());
        currentSsoProvider.setVendor(ssoProvider.getVendor());

        if (ssoProvider.hasClientSecret()) {
            currentSsoProvider.setClientSecret(ssoProvider.getClientSecret());
        }

        validateAndNormalize(currentSsoProvider, id);

        return ssoProviderRepository.save(currentSsoProvider);
    }

    private String generateRegistrationId() {
        byte[] randomBytes = new byte[REGISTRATION_ID_RANDOM_BYTES];

        secureRandom.nextBytes(randomBytes);

        HexFormat hexFormat = HexFormat.of();

        return REGISTRATION_ID_PREFIX + hexFormat.formatHex(randomBytes);
    }

    private void validateAndNormalize(SsoProvider ssoProvider, Long currentId) {
        String name = ssoProvider.getName();

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }

        String trimmedName = name.trim();

        if (trimmedName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("name must be at most " + MAX_NAME_LENGTH + " characters");
        }

        ssoProvider.setName(trimmedName);

        Set<String> normalizedDomains = new LinkedHashSet<>();

        for (String domain : ssoProvider.getDomains()) {
            normalizedDomains.add(SsoEmailDomainUtils.normalizeDomain(domain));
        }

        if (ssoProvider.isEnforceSso() && normalizedDomains.isEmpty()) {
            throw new IllegalArgumentException("At least one domain is required when SSO is enforced");
        }

        ssoProvider.setDomains(new ArrayList<>(normalizedDomains));

        String defaultAuthority = ssoProvider.getDefaultAuthority();

        if (defaultAuthority == null || defaultAuthority.isBlank()) {
            ssoProvider.setDefaultAuthority(AuthorityConstants.USER);
        } else if (!ALLOWED_DEFAULT_AUTHORITIES.contains(defaultAuthority)) {
            throw new IllegalArgumentException("defaultAuthority must be one of " + ALLOWED_DEFAULT_AUTHORITIES);
        }

        validateUniqueness(trimmedName, normalizedDomains, currentId);

        if (ssoProvider.getType() == SsoProviderType.OIDC) {
            validateOidc(ssoProvider);
        } else {
            validateSaml(ssoProvider);
        }
    }

    private void validateOidc(SsoProvider ssoProvider) {
        String issuerUri = ssoProvider.getIssuerUri();

        if (ssoProvider.isMicrosoftEntraId()) {
            issuerUri = SsoMicrosoftEntraUtils.normalizeIssuer(issuerUri);
        }

        SsoRemoteUrlValidator.validate(issuerUri);

        ssoProvider.setIssuerUri(issuerUri.trim());

        String clientId = ssoProvider.getClientId();

        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId is required for an OIDC provider");
        }

        ssoProvider.setClientId(clientId.trim());

        if (!ssoProvider.hasClientSecret()) {
            throw new IllegalArgumentException("clientSecret is required for an OIDC provider");
        }

        Set<String> scopes = new LinkedHashSet<>();

        scopes.add(OPENID_SCOPE);

        List<String> requestedScopes = ssoProvider.getScopes();

        if (requestedScopes.isEmpty()) {
            requestedScopes = DEFAULT_OIDC_SCOPES;
        }

        for (String scope : requestedScopes) {
            String trimmedScope = scope.trim();

            if (!trimmedScope.matches("^[\\x21\\x23-\\x5B\\x5D-\\x7E]+$")) {
                throw new IllegalArgumentException("Invalid scope: " + scope);
            }

            scopes.add(trimmedScope);
        }

        if (ssoProvider.isMicrosoftEntraId()) {
            // Entra emits oid, preferred_username and the name claims only with the profile scope
            scopes.add(PROFILE_SCOPE);
        }

        ssoProvider.setScopes(new ArrayList<>(scopes));
        ssoProvider.setMetadataUrl(null);
        ssoProvider.setMetadataXml(null);
    }

    private void validateSaml(SsoProvider ssoProvider) {
        String metadataUrl = ssoProvider.getMetadataUrl();
        String metadataXml = ssoProvider.getMetadataXml();

        boolean hasMetadataUrl = metadataUrl != null && !metadataUrl.isBlank();
        boolean hasMetadataXml = metadataXml != null && !metadataXml.isBlank();

        if (hasMetadataUrl == hasMetadataXml) {
            throw new IllegalArgumentException(
                "Exactly one of metadataUrl or metadataXml is required for a SAML provider");
        }

        if (hasMetadataUrl) {
            SsoRemoteUrlValidator.validate(metadataUrl);

            ssoProvider.setMetadataUrl(metadataUrl.trim());
            ssoProvider.setMetadataXml(null);
        } else {
            byte[] metadataXmlBytes = metadataXml.getBytes(StandardCharsets.UTF_8);

            if (metadataXmlBytes.length > MAX_METADATA_XML_BYTES) {
                throw new IllegalArgumentException("metadataXml must be at most 1 MB");
            }

            if (metadataXml.toUpperCase(Locale.ROOT)
                .contains("<!DOCTYPE")) {

                throw new IllegalArgumentException("metadataXml must not contain a DOCTYPE declaration");
            }

            ssoProvider.setMetadataUrl(null);
        }

        ssoProvider.setClientId(null);
        ssoProvider.setClientSecret(null);
        ssoProvider.setIssuerUri(null);
        ssoProvider.setScopes(List.of());
        ssoProvider.setVendor(SsoProviderVendor.GENERIC);
    }

    private void validateUniqueness(String name, Set<String> domains, Long currentId) {
        for (SsoProvider existingSsoProvider : ssoProviderRepository.findAll()) {
            if (Objects.equals(existingSsoProvider.getId(), currentId)) {
                continue;
            }

            if (name.equalsIgnoreCase(existingSsoProvider.getName())) {
                throw new IllegalArgumentException("Another SSO provider is already named " + name);
            }

            for (String domain : existingSsoProvider.getDomains()) {
                if (domains.contains(domain)) {
                    throw new IllegalArgumentException(
                        "Domain " + domain + " is already served by SSO provider " + existingSsoProvider.getName());
                }
            }
        }
    }
}
