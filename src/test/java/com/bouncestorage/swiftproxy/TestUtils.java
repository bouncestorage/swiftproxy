/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
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
