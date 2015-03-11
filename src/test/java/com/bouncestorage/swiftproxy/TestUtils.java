/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import com.google.common.io.Resources;

public final class TestUtils {
    public static final String ACCOUNT_PATH = "/v1/testAccount";

    private TestUtils() {
        // Hide the constructor for a Utils class
    }

    public static SwiftProxy setupAndStartProxy() throws IOException {
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

    public static Response deleteContainer(WebTarget target, String container) {
        return target.path(ACCOUNT_PATH + "/" + container)
                .request().delete();
    }

    public static void createContainer(WebTarget target, String container) {
        Response resp = target.path(ACCOUNT_PATH + "/" + container)
                .request().post(null);
        assertThat(resp.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    }
}
