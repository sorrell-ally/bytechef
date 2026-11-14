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

package com.bytechef.platform.sso.domain;

import com.bytechef.commons.data.jdbc.wrapper.EncryptedMapWrapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * An administrator-managed single sign-on identity provider (OIDC or SAML 2.0).
 *
 * <p>
 * The {@code registrationId} is a random, opaque identifier generated on creation. It is the {@code {registrationId}}
 * path segment of the Spring Security login URLs ({@code /oauth2/authorization/{registrationId}},
 * {@code /saml2/authenticate/{registrationId}}) and, upper-cased, the {@code auth_provider} recorded on users who sign
 * in through this provider. It is never derived from the database id, so provider ids cannot be guessed from the
 * sequence.
 *
 * <p>
 * The OIDC client secret is held in {@code secret_settings}, an {@link EncryptedMapWrapper} column encrypted at rest.
 * {@link #toString()} deliberately omits it.
 */
@Table("sso_provider")
public class SsoProvider {

    public static final String CLIENT_SECRET = "clientSecret";

    private static final String LIST_SEPARATOR = ",";

    @Column("auto_provision")
    private boolean autoProvision;

    @Column("client_id")
    private String clientId;

    @CreatedBy
    @Column("created_by")
    private String createdBy;

    @Column("created_date")
    @CreatedDate
    private Instant createdDate;

    @Column("default_authority")
    private String defaultAuthority;

    @Column
    private String domains;

    @Column
    private boolean enabled;

    @Column("enforce_sso")
    private boolean enforceSso;

    @Id
    private Long id;

    @Column("issuer_uri")
    private String issuerUri;

    @Column("last_modified_by")
    @LastModifiedBy
    private String lastModifiedBy;

    @Column("last_modified_date")
    @LastModifiedDate
    private Instant lastModifiedDate;

    @Column("metadata_url")
    private String metadataUrl;

    @Column("metadata_xml")
    private String metadataXml;

    @Column
    private String name;

    @Column("registration_id")
    private String registrationId;

    @Column
    private String scopes;

    @Column("secret_settings")
    private EncryptedMapWrapper secretSettings;

    @Column
    private String type;

    @Column
    private String vendor;

    @Version
    private int version;

    public SsoProvider() {
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof SsoProvider ssoProvider)) {
            return false;
        }

        return Objects.equals(id, ssoProvider.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * @return the user-facing authentication provider name stored in {@code user.auth_provider} for accounts linked to
     *         this provider (at most 20 characters, matching that column's width)
     */
    public String getAuthProvider() {
        if (registrationId == null) {
            return null;
        }

        return registrationId.toUpperCase(Locale.ROOT);
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * @return the decrypted OIDC client secret, or {@code null}; never expose this through an API
     */
    public String getClientSecret() {
        if (secretSettings == null) {
            return null;
        }

        Object clientSecret = secretSettings.get(CLIENT_SECRET);

        return clientSecret == null ? null : clientSecret.toString();
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public String getDefaultAuthority() {
        return defaultAuthority;
    }

    /**
     * @return the lower-cased email domains served by this provider
     */
    public List<String> getDomains() {
        return splitList(domains);
    }

    public Long getId() {
        return id;
    }

    public String getIssuerUri() {
        return issuerUri;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public String getMetadataUrl() {
        return metadataUrl;
    }

    public String getMetadataXml() {
        return metadataXml;
    }

    public String getName() {
        return name;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public List<String> getScopes() {
        return splitList(scopes);
    }

    public SsoProviderType getType() {
        if (type == null) {
            return null;
        }

        return SsoProviderType.valueOf(type);
    }

    /**
     * @return the identity-provider product; {@link SsoProviderVendor#GENERIC} when unset
     */
    public SsoProviderVendor getVendor() {
        if (vendor == null) {
            return SsoProviderVendor.GENERIC;
        }

        return SsoProviderVendor.valueOf(vendor);
    }

    public int getVersion() {
        return version;
    }

    public boolean isMicrosoftEntraId() {
        return getType() == SsoProviderType.OIDC && getVendor() == SsoProviderVendor.MICROSOFT_ENTRA_ID;
    }

    public boolean hasClientSecret() {
        String clientSecret = getClientSecret();

        return clientSecret != null && !clientSecret.isBlank();
    }

    public boolean isAutoProvision() {
        return autoProvision;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEnforceSso() {
        return enforceSso;
    }

    public void setAutoProvision(boolean autoProvision) {
        this.autoProvision = autoProvision;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setClientSecret(String clientSecret) {
        if (clientSecret == null) {
            this.secretSettings = null;

            return;
        }

        this.secretSettings = new EncryptedMapWrapper(Map.of(CLIENT_SECRET, clientSecret));
    }

    public void setDefaultAuthority(String defaultAuthority) {
        this.defaultAuthority = defaultAuthority;
    }

    public void setDomains(Collection<String> domains) {
        this.domains = joinList(domains);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setEnforceSso(boolean enforceSso) {
        this.enforceSso = enforceSso;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public void setMetadataUrl(String metadataUrl) {
        this.metadataUrl = metadataUrl;
    }

    public void setMetadataXml(String metadataXml) {
        this.metadataXml = metadataXml;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public void setScopes(Collection<String> scopes) {
        this.scopes = joinList(scopes);
    }

    public void setType(SsoProviderType type) {
        this.type = type == null ? null : type.name();
    }

    public void setVendor(SsoProviderVendor vendor) {
        this.vendor = vendor == null ? null : vendor.name();
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "SsoProvider{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", type='" + type + '\'' +
            ", vendor='" + vendor + '\'' +
            ", registrationId='" + registrationId + '\'' +
            ", enabled=" + enabled +
            ", domains='" + domains + '\'' +
            ", enforceSso=" + enforceSso +
            ", autoProvision=" + autoProvision +
            ", defaultAuthority='" + defaultAuthority + '\'' +
            ", issuerUri='" + issuerUri + '\'' +
            ", clientId='" + clientId + '\'' +
            ", metadataUrl='" + metadataUrl + '\'' +
            ", version=" + version +
            '}';
    }

    private static String joinList(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        return String.join(LIST_SEPARATOR, values);
    }

    private static List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();

        for (String item : Arrays.asList(value.split(LIST_SEPARATOR))) {
            String trimmedItem = item.trim();

            if (!trimmedItem.isEmpty()) {
                values.add(trimmedItem);
            }
        }

        return List.copyOf(values);
    }
}
