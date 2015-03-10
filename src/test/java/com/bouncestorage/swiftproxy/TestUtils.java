package com.bouncestorage.swiftproxy;

import com.google.common.io.Resources;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class TestUtils {
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
