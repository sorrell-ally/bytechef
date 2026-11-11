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

package com.bytechef.platform.configuration.service;

import com.bytechef.platform.configuration.domain.GettingStarted;
import com.bytechef.platform.configuration.domain.GettingStarted.CompletionType;
import com.bytechef.platform.configuration.repository.GettingStartedRepository;
import com.bytechef.platform.constant.PlatformType;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * @author Ivica Cardic
 */
@Service
@Transactional
public class GettingStartedServiceImpl implements GettingStartedService {

    private final GettingStartedRepository gettingStartedRepository;

    @SuppressFBWarnings("EI")
    public GettingStartedServiceImpl(GettingStartedRepository gettingStartedRepository) {
        this.gettingStartedRepository = gettingStartedRepository;
    }

    @Override
    public void complete(PlatformType type, CompletionType completionType) {
        Assert.notNull(type, "'type' must not be null");
        Assert.notNull(completionType, "'completionType' must not be null");

        gettingStartedRepository.insertIfAbsent(type.ordinal(), completionType.ordinal(), Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GettingStarted> fetchGettingStarted(PlatformType type) {
        Assert.notNull(type, "'type' must not be null");

        return gettingStartedRepository.findByType(type.ordinal());
    }
}
