/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
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

