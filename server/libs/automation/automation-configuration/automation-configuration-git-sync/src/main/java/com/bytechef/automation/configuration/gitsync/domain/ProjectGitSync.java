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

package com.bytechef.automation.configuration.gitsync.domain;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Per-project Git sync settings plus the state of the last sync. The access token is stored encrypted and is never
 * exposed through {@link #toString()} or any API type.
 *
 * <p>
 * Sync state columns are written with a dedicated repository update (see
 * {@code ProjectGitSyncRepository#updateSyncState}) so that recording a sync result never conflicts with a concurrent
 * settings edit through the optimistic-locking {@link #version}.
 */
@Table("project_git_sync")
public class ProjectGitSync {

    @Column("access_token")
    private @Nullable String accessToken;

    @Column
    private String branch;

    @CreatedBy
    @Column("created_by")
    private String createdBy;

    @Column("created_date")
    @CreatedDate
    private Instant createdDate;

    @Column
    private boolean enabled;

    @Id
    private Long id;

    @Column("last_modified_by")
    @LastModifiedBy
    private String lastModifiedBy;

    @Column("last_modified_date")
    @LastModifiedDate
    private Instant lastModifiedDate;

    @Column("last_pushed_project_version")
    private @Nullable Integer lastPushedProjectVersion;

    @Column("last_sync_commit_hash")
    private @Nullable String lastSyncCommitHash;

    @Column("last_sync_date")
    private @Nullable Instant lastSyncDate;

    @Column("last_sync_direction")
    private @Nullable Integer lastSyncDirection;

    @Column("last_sync_error_message")
    private @Nullable String lastSyncErrorMessage;

    @Column("last_sync_status")
    private @Nullable Integer lastSyncStatus;

    @Column("project_id")
    private long projectId;

    @Column("repository_url")
    private String repositoryUrl;

    @Column("sub_directory")
    private @Nullable String subDirectory;

    @Column
    private @Nullable String username;

    @Version
    private int version;

    public ProjectGitSync() {
    }

    public ProjectGitSync(long projectId) {
        this.projectId = projectId;
    }

    /**
     * @return the encrypted access token, or {@code null} when none is stored
     */
    public @Nullable String getAccessToken() {
        return accessToken;
    }

    public String getBranch() {
        return branch;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public Long getId() {
        return id;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public @Nullable Integer getLastPushedProjectVersion() {
        return lastPushedProjectVersion;
    }

    public @Nullable String getLastSyncCommitHash() {
        return lastSyncCommitHash;
    }

    public @Nullable Instant getLastSyncDate() {
        return lastSyncDate;
    }

    public @Nullable ProjectGitSyncDirection getLastSyncDirection() {
        return lastSyncDirection == null ? null : ProjectGitSyncDirection.values()[lastSyncDirection];
    }

    public @Nullable String getLastSyncErrorMessage() {
        return lastSyncErrorMessage;
    }

    public @Nullable ProjectGitSyncStatus getLastSyncStatus() {
        return lastSyncStatus == null ? null : ProjectGitSyncStatus.values()[lastSyncStatus];
    }

    public long getProjectId() {
        return projectId;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public @Nullable String getSubDirectory() {
        return subDirectory;
    }

    public @Nullable String getUsername() {
        return username;
    }

    public int getVersion() {
        return version;
    }

    public boolean hasAccessToken() {
        return accessToken != null && !accessToken.isEmpty();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param accessToken the already encrypted access token, or {@code null} to clear it
     */
    public void setAccessToken(@Nullable String accessToken) {
        this.accessToken = accessToken;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public void setSubDirectory(@Nullable String subDirectory) {
        this.subDirectory = subDirectory;
    }

    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        ProjectGitSync projectGitSync = (ProjectGitSync) object;

        return Objects.equals(id, projectGitSync.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ProjectGitSync{" +
            "id=" + id +
            ", projectId=" + projectId +
            ", repositoryUrl='" + repositoryUrl + '\'' +
            ", branch='" + branch + '\'' +
            ", subDirectory='" + subDirectory + '\'' +
            ", username='" + username + '\'' +
            ", accessToken=" + (hasAccessToken() ? "[REDACTED]" : "null") +
            ", enabled=" + enabled +
            ", lastSyncCommitHash='" + lastSyncCommitHash + '\'' +
            ", lastSyncDate=" + lastSyncDate +
            ", lastSyncDirection=" + lastSyncDirection +
            ", lastSyncStatus=" + lastSyncStatus +
            ", version=" + version +
            '}';
    }
}
