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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;

import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;

/**
 * Main class.
 *
 */
public final class Main {
    private Main() {
        // Hide the main method
    }

    /**
     * Main method.
     * @param args
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--version")) {
            System.err.println(
                    Main.class.getPackage().getImplementationVersion());
            System.exit(0);
        } else if (args.length != 2) {
            System.err.println("Usage: swiftproxy --properties FILE");
            System.exit(1);
        }

        Properties properties = new Properties();
        try (InputStream is = new FileInputStream(new File(args[1]))) {
            properties.load(is);
        }
        properties.putAll(System.getProperties());

        String provider = properties.getProperty(Constants.PROPERTY_PROVIDER);
        String identity = properties.getProperty(Constants.PROPERTY_IDENTITY);
        String credential = properties.getProperty(Constants.PROPERTY_CREDENTIAL);
        String endpoint = properties.getProperty(Constants.PROPERTY_ENDPOINT);
        String proxyEndpoint = properties.getProperty(SwiftProxy.PROPERTY_ENDPOINT);
        if (provider == null || identity == null || credential == null || proxyEndpoint == null) {
            System.err.println("Properties file must contain:%n" +
                    Constants.PROPERTY_PROVIDER + "%n" +
                    Constants.PROPERTY_IDENTITY + "%n" +
                    Constants.PROPERTY_CREDENTIAL + "%n" +
                    SwiftProxy.PROPERTY_ENDPOINT + "%n");
            System.exit(1);
        }

        ContextBuilder builder = ContextBuilder
                .newBuilder(provider)
                .credentials(identity, credential)
                .modules(ImmutableList.<Module>of(new SLF4JLoggingModule()))
                .overrides(properties);
        if (endpoint != null) {
            builder = builder.endpoint(endpoint);
        }
        BlobStoreContext context = builder.build(BlobStoreContext.class);

        SwiftProxy proxy = SwiftProxy.Builder.builder()
                .overrides(properties)
                .endpoint(new URI(proxyEndpoint))
                .build();
        proxy.start();
        System.out.format("Swift proxy listening on port %d%n", proxy.getPort());
        Thread.currentThread().join();
    }
}

