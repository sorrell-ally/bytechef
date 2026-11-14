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

package com.bytechef.automation.configuration.gitsync.repository;

import com.bytechef.automation.configuration.gitsync.domain.ProjectGitSync;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectGitSyncRepository extends ListCrudRepository<ProjectGitSync, Long> {

    Optional<ProjectGitSync> findByProjectId(long projectId);

    List<ProjectGitSync> findAllByProjectIdIn(Collection<Long> projectIds);

    List<ProjectGitSync> findAllByRepositoryUrlAndBranch(String repositoryUrl, String branch);

    /**
     * Records the outcome of a sync without touching the optimistic-locking version, so a concurrent settings edit
     * cannot make the state write fail (and vice versa).
     */
    @Modifying
    @Query("""
        UPDATE project_git_sync
        SET last_sync_commit_hash = COALESCE(:commitHash, last_sync_commit_hash),
            last_sync_date = :syncDate,
            last_sync_direction = :direction,
            last_sync_status = :status,
            last_sync_error_message = :errorMessage,
            last_pushed_project_version = COALESCE(:pushedProjectVersion, last_pushed_project_version)
        WHERE project_id = :projectId
        """)
    int updateSyncState(
        @Param("projectId") long projectId, @Param("commitHash") @Nullable String commitHash,
        @Param("syncDate") Instant syncDate, @Param("direction") int direction, @Param("status") int status,
        @Param("errorMessage") @Nullable String errorMessage,
        @Param("pushedProjectVersion") @Nullable Integer pushedProjectVersion);
}
