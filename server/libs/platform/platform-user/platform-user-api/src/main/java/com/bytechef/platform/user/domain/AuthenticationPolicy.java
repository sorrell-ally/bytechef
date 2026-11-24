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

package com.bytechef.platform.user.domain;

import java.time.Instant;
import java.util.Objects;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The single organization authentication policy. A null {@code signUpEnabled} means the environment property is still
 * the value in effect.
 *
 * @author Ivica Cardic
 */
@Table("authentication_policy")
public class AuthenticationPolicy implements Persistable<Long> {

    public static final long SINGLETON_ID = 1L;

    @CreatedBy
    @Column("created_by")
    private String createdBy;

    @Column("created_date")
    @CreatedDate
    private Instant createdDate;

    @Id
    private Long id;

    @Column("last_modified_by")
    @LastModifiedBy
    private String lastModifiedBy;

    @Column("last_modified_date")
    @LastModifiedDate
    private Instant lastModifiedDate;

    @Transient
    private boolean newPolicy;

    @Column("sign_up_enabled")
    private Boolean signUpEnabled;

    @Column("sso_only_enabled")
    private boolean ssoOnlyEnabled;

    @Version
    private int version;

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof AuthenticationPolicy authenticationPolicy)) {
            return false;
        }

        return Objects.equals(id, authenticationPolicy.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    @Override
    public Long getId() {
        return id;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    /**
     * @return the stored self sign-up choice, or {@code null} when the environment property is still the default
     */
    public Boolean getSignUpEnabled() {
        return signUpEnabled;
    }

    public int getVersion() {
        return version;
    }

    public boolean isSsoOnlyEnabled() {
        return ssoOnlyEnabled;
    }

    @Override
    public boolean isNew() {
        return newPolicy || id == null;
    }

    public void markNew() {
        newPolicy = true;
    }

    public void markPersisted() {
        newPolicy = false;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public void setLastModifiedDate(Instant lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public void setSignUpEnabled(Boolean signUpEnabled) {
        this.signUpEnabled = signUpEnabled;
    }

    public void setSsoOnlyEnabled(boolean ssoOnlyEnabled) {
        this.ssoOnlyEnabled = ssoOnlyEnabled;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
