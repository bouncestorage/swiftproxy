/*
 * Copyright 2015 Bounce Storage, Inc. <info@bouncestorage.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bouncestorage.swiftproxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import com.google.common.io.Resources;

import org.jclouds.Constants;

public final class TestUtils {
    public static final String ACCOUNT_PATH = "/v1/testAccount";

    private TestUtils() {
        // Hide the constructor for a Utils class
    }

    public static SwiftProxy setupAndStartProxy() throws Exception {
        Properties properties = new Properties();
        try (InputStream is = Resources.asByteSource(Resources.getResource(
                "swiftproxy.conf")).openStream()) {
            properties.load(is);
        }

        String provider = properties.getProperty(Constants.PROPERTY_PROVIDER);
        String credential = properties.getProperty(Constants.PROPERTY_CREDENTIAL);
        if (provider != null && credential != null && provider.equals("google-cloud-storage")) {
            FileSystem fs = FileSystems.getDefault();
            Path credentialPath = fs.getPath(credential);
            if (Files.exists(credentialPath)) {
                credential = new String(Files.readAllBytes(credentialPath),
                        StandardCharsets.UTF_8);
            }
            properties.put(Constants.PROPERTY_CREDENTIAL, credential);
        }

        SwiftProxy proxy = SwiftProxy.Builder.builder()
                .overrides(properties)
                .build();
        proxy.start();
        return proxy;
    }

    public static String getAuthToken(WebTarget target) {
        Properties properties = new Properties();
        try (InputStream is = Resources.asByteSource(Resources.getResource(
                "swiftproxy.conf")).openStream()) {
            properties.load(is);
        } catch (IOException e) {
            return null;
        }

        Response resp = target.path("/auth/v1.0").request()
                .header("X-auth-user", properties.getProperty(Constants.PROPERTY_IDENTITY))
                .header("X-auth-key", properties.getProperty(Constants.PROPERTY_CREDENTIAL))
                .get();
        return resp.getHeaderString("x-auth-token");
    }

    public static Response deleteContainer(WebTarget target, String container) throws Exception {
        return deleteContainer(target, container, Optional.empty());
    }

    public static Response deleteContainer(WebTarget target, String container,
                                           Optional<String> authToken) throws Exception {
        return target.path(ACCOUNT_PATH + "/" + container).request()
                .header("x-auth-token", authToken.orElseGet(() -> getAuthToken(target)))
                .delete();
    }

    public static String createContainer(WebTarget target, String container) throws Exception {
        String authToken = getAuthToken(target);
        Response resp = target.path(ACCOUNT_PATH + "/" + container).request()
                .header("x-auth-token", authToken)
                .post(null);
        assertThat(resp.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
        return authToken;
    }
}
