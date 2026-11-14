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

package com.bytechef.platform.sso.web.authentication;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The principal of an SSO session. It is a {@link UserDetails} whose username is the ByteChef login, so
 * {@code SecurityUtils.fetchCurrentUserLogin()} (and everything built on it, such as {@code GET /api/account}) resolves
 * the signed-in user exactly as it does after a password login.
 */
public class SsoUserPrincipal implements AuthenticatedPrincipal, UserDetails {

    private static final long serialVersionUID = 1L;

    private final List<GrantedAuthority> authorities;
    private final String login;
    private final String registrationId;

    public SsoUserPrincipal(String login, Collection<? extends GrantedAuthority> authorities, String registrationId) {
        this.authorities = List.copyOf(authorities);
        this.login = login;
        this.registrationId = registrationId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return login;
    }

    /**
     * SSO sessions carry no password.
     */
    @Override
    public String getPassword() {
        return null;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public String toString() {
        return "SsoUserPrincipal{login='" + login + "', registrationId='" + registrationId + "'}";
    }
}
