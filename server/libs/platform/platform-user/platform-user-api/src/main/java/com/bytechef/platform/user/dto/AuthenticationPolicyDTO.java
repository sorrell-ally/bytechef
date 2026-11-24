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

package com.bytechef.platform.user.dto;

/**
 * The authentication policy an administrator sees and that the login screen reads.
 *
 * @param passwordLoginBreakGlass  whether the environment break-glass currently allows administrator password login
 * @param signUpEnabled            the value in effect, after applying the environment default
 * @param signUpEnvironmentDefault {@code bytechef.sign-up.enabled}
 * @param signUpOverridden         whether an administrator has saved a self sign-up choice
 * @param ssoOnlyEnabled           whether password login and password registration are turned off
 *
 * @author Ivica Cardic
 */
public record AuthenticationPolicyDTO(
    boolean passwordLoginBreakGlass, boolean signUpEnabled, boolean signUpEnvironmentDefault,
    boolean signUpOverridden, boolean ssoOnlyEnabled) {
}
