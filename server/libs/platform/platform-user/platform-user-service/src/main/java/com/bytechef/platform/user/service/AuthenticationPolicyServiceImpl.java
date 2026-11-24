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

import com.bytechef.config.ApplicationProperties;
import com.bytechef.platform.security.constant.AuthorityConstants;
import com.bytechef.platform.user.domain.AuthenticationPolicy;
import com.bytechef.platform.user.domain.Authority;
import com.bytechef.platform.user.domain.User;
import com.bytechef.platform.user.dto.AuthenticationPolicyDTO;
import com.bytechef.platform.user.repository.AuthenticationPolicyRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The environment property {@code bytechef.sign-up.enabled} is the self sign-up value until an administrator saves a
 * choice. After that, the stored value is the only one that counts. Changing the environment property later does not
 * overwrite a saved choice.
 *
 * @author Ivica Cardic
 */
@Service
@Transactional(readOnly = true)
public class AuthenticationPolicyServiceImpl implements AuthenticationPolicyService {

    private final ApplicationProperties applicationProperties;
    private final AuthenticationPolicyRepository authenticationPolicyRepository;
    private final AuthorityService authorityService;

    @SuppressFBWarnings("EI")
    public AuthenticationPolicyServiceImpl(
        ApplicationProperties applicationProperties, AuthenticationPolicyRepository authenticationPolicyRepository,
        AuthorityService authorityService) {

        this.applicationProperties = applicationProperties;
        this.authenticationPolicyRepository = authenticationPolicyRepository;
        this.authorityService = authorityService;
    }

    @Override
    public AuthenticationPolicyDTO getAuthenticationPolicy() {
        Optional<AuthenticationPolicy> authenticationPolicy = fetchPolicy();

        boolean signUpOverridden = authenticationPolicy.map(AuthenticationPolicy::getSignUpEnabled)
            .isPresent();

        return new AuthenticationPolicyDTO(
            isPasswordLoginBreakGlass(), isSelfSignUpEnabled(authenticationPolicy), isEnvironmentSignUpEnabled(),
            signUpOverridden, isSsoOnlyEnabled(authenticationPolicy));
    }

    @Override
    public boolean isActivatedAdmin(User user) {
        if (user == null || !user.isActivated()) {
            return false;
        }

        for (Long authorityId : user.getAuthorityIds()) {
            Optional<String> authorityName = authorityService.fetchAuthority(authorityId)
                .map(Authority::getName);

            if (authorityName.isPresent() && AuthorityConstants.ADMIN.equals(authorityName.get())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isPasswordLoginBreakGlass() {
        ApplicationProperties.Security security = applicationProperties.getSecurity();

        return security != null && security.isPasswordLoginBreakGlass();
    }

    @Override
    public boolean isSelfSignUpEnabled() {
        return isSelfSignUpEnabled(fetchPolicy());
    }

    @Override
    public boolean isSsoOnlyEnabled() {
        return isSsoOnlyEnabled(fetchPolicy());
    }

    @Override
    @Transactional
    public void updateSelfSignUpEnabled(boolean enabled) {
        AuthenticationPolicy authenticationPolicy = getOrCreate();

        authenticationPolicy.markPersisted();
        authenticationPolicy.setSignUpEnabled(enabled);

        authenticationPolicyRepository.save(authenticationPolicy);
    }

    @Override
    @Transactional
    public void updateSsoOnlyEnabled(boolean enabled) {
        AuthenticationPolicy authenticationPolicy = getOrCreate();

        authenticationPolicy.markPersisted();
        authenticationPolicy.setSsoOnlyEnabled(enabled);

        authenticationPolicyRepository.save(authenticationPolicy);
    }

    private Optional<AuthenticationPolicy> fetchPolicy() {
        return authenticationPolicyRepository.findById(AuthenticationPolicy.SINGLETON_ID);
    }

    private AuthenticationPolicy getOrCreate() {
        Optional<AuthenticationPolicy> authenticationPolicy = fetchPolicy();

        if (authenticationPolicy.isPresent()) {
            return authenticationPolicy.get();
        }

        AuthenticationPolicy createdPolicy = new AuthenticationPolicy();

        createdPolicy.setId(AuthenticationPolicy.SINGLETON_ID);
        createdPolicy.setSsoOnlyEnabled(false);
        createdPolicy.markNew();

        return authenticationPolicyRepository.save(createdPolicy);
    }

    private boolean isEnvironmentSignUpEnabled() {
        return applicationProperties.getSignUp()
            .isEnabled();
    }

    private boolean isSelfSignUpEnabled(Optional<AuthenticationPolicy> authenticationPolicy) {
        if (authenticationPolicy.isEmpty()) {
            return isEnvironmentSignUpEnabled();
        }

        Boolean signUpEnabled = authenticationPolicy.get()
            .getSignUpEnabled();

        if (signUpEnabled == null) {
            return isEnvironmentSignUpEnabled();
        }

        return signUpEnabled;
    }

    private static boolean isSsoOnlyEnabled(Optional<AuthenticationPolicy> authenticationPolicy) {
        if (authenticationPolicy.isEmpty()) {
            return false;
        }

        return authenticationPolicy.get()
            .isSsoOnlyEnabled();
    }
}
