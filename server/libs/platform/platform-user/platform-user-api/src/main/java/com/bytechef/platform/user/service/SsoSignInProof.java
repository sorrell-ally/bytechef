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

package com.bytechef.platform.user.service;

/**
 * What the SSO feature can prove about the current administrator. Present only when single sign-on is enabled. A
 * completed sign-in through a provider is the test of that provider: it links the account's {@code auth_provider}.
 *
 * @author Ivica Cardic
 */
public interface SsoSignInProof {

    /**
     * @return whether the acting administrator's account is linked to an enabled SSO provider
     */
    boolean actingAdminSignedInThroughEnabledProvider();

    /**
     * @return whether at least one SSO provider is enabled
     */
    boolean enabledProviderExists();
}
