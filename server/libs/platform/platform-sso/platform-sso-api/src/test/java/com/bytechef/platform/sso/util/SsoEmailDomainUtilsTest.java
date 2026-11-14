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

package com.bytechef.platform.sso.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SsoEmailDomainUtilsTest {

    @Test
    void testExtractDomain() {
        assertThat(SsoEmailDomainUtils.extractDomain(" Jane.Doe@Example.COM ")).contains("example.com");
        assertThat(SsoEmailDomainUtils.extractDomain("jane@sub.example.co.uk")).contains("sub.example.co.uk");
    }

    @Test
    void testExtractDomainRejectsMalformedEmail() {
        assertThat(SsoEmailDomainUtils.extractDomain(null)).isEmpty();
        assertThat(SsoEmailDomainUtils.extractDomain("no-at-sign")).isEmpty();
        assertThat(SsoEmailDomainUtils.extractDomain("@example.com")).isEmpty();
        assertThat(SsoEmailDomainUtils.extractDomain("jane@")).isEmpty();
        assertThat(SsoEmailDomainUtils.extractDomain("jane@evil.com@example.com")).isEmpty();
        assertThat(SsoEmailDomainUtils.extractDomain("jane@localhost")).isEmpty();
        assertThat(SsoEmailDomainUtils.extractDomain("jane@exa mple.com")).isEmpty();
    }

    @Test
    void testNormalizeDomain() {
        assertThat(SsoEmailDomainUtils.normalizeDomain(" @Example.COM ")).isEqualTo("example.com");
    }

    @Test
    void testNormalizeDomainRejectsInvalidDomain() {
        assertThatThrownBy(() -> SsoEmailDomainUtils.normalizeDomain("*.example.com"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SsoEmailDomainUtils.normalizeDomain("example"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SsoEmailDomainUtils.normalizeDomain(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
