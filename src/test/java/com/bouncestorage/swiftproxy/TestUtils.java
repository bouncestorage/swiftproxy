/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.google.common.io.Resources;

public final class TestUtils {
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
}
