/*
 * Copyright 2025 ByteChef
 *
 * Licensed under the ByteChef Enterprise license (the "Enterprise License");
 * you may not use this file except in compliance with the Enterprise License.
 */

package com.bytechef.ee.platform.configuration.remote.client.service;

import com.bytechef.platform.configuration.domain.GettingStarted;
import com.bytechef.platform.configuration.domain.GettingStarted.CompletionType;
import com.bytechef.platform.configuration.service.GettingStartedService;
import com.bytechef.platform.constant.PlatformType;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * @version ee
 *
 * @author Ivica Cardic
 */
@Component
public class RemoteGettingStartedServiceClient implements GettingStartedService {

    @Override
    public void complete(PlatformType type, CompletionType completionType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<GettingStarted> fetchGettingStarted(PlatformType type) {
        throw new UnsupportedOperationException();
    }
}
