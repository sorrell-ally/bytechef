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

package com.bytechef.platform.sso.web.registration;

import com.bytechef.platform.sso.util.SsoRemoteUrlValidator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches an administrator-configured remote document (OIDC discovery, SAML metadata) with SSRF guards: the URL is
 * re-validated with DNS resolution right before the request, redirects are not followed, and the connect time, total
 * time and response size are bounded.
 */
public class SsoRemoteDocumentFetcher {

    static final int MAX_DOCUMENT_BYTES = 1024 * 1024;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    public SsoRemoteDocumentFetcher() {
        this(
            HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    SsoRemoteDocumentFetcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * @throws IllegalArgumentException when the URL is not allowed
     * @throws UncheckedIOException     when the document cannot be fetched or is too large
     */
    public byte[] fetch(String url, String accept) {
        URI uri = SsoRemoteUrlValidator.validateResolved(url);

        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", accept)
            .GET()
            .build();

        try {
            HttpResponse<InputStream> httpResponse = httpClient.send(
                httpRequest, HttpResponse.BodyHandlers.ofInputStream());

            try (InputStream inputStream = httpResponse.body()) {
                if (httpResponse.statusCode() != 200) {
                    throw new IOException("Unexpected HTTP status " + httpResponse.statusCode() + " from " + uri);
                }

                return readBounded(inputStream);
            }
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread()
                .interrupt();

            throw new IllegalStateException("Interrupted while fetching " + uri, interruptedException);
        }
    }

    static byte[] readBounded(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int readCount;

        while ((readCount = inputStream.read(buffer)) != -1) {
            if (byteArrayOutputStream.size() + readCount > MAX_DOCUMENT_BYTES) {
                throw new IOException("Remote document exceeds " + MAX_DOCUMENT_BYTES + " bytes");
            }

            byteArrayOutputStream.write(buffer, 0, readCount);
        }

        return byteArrayOutputStream.toByteArray();
    }
}
