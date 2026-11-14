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

package com.bytechef.automation.configuration.gitsync.sync;

import com.bytechef.automation.configuration.gitsync.git.ProjectGitSyncException;
import com.bytechef.platform.annotation.ConditionalOnCEVersion;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Serializes Git sync operations (push, pull, manual push) of the same project within this JVM, so two publishes of one
 * project cannot interleave their fetch/commit/push steps. Across nodes, and across projects sharing a branch,
 * correctness comes from the push being rejected as non-fast-forward and retried from the new tip.
 */
@Component
@ConditionalOnCEVersion
public class ProjectGitSyncLocks {

    private static final long LOCK_TIMEOUT_MINUTES = 5;

    private final ConcurrentMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(long projectId, Supplier<T> supplier) {
        ReentrantLock lock = locks.computeIfAbsent(projectId, key -> new ReentrantLock(true));

        boolean acquired;

        try {
            acquired = lock.tryLock(LOCK_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread()
                .interrupt();

            throw new ProjectGitSyncException("Interrupted while waiting for another Git sync of this project");
        }

        if (!acquired) {
            throw new ProjectGitSyncException("Another Git sync of this project is still running; try again later");
        }

        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }
}
