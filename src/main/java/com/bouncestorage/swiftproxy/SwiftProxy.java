/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.jclouds.Constants;
import org.jclouds.blobstore.BlobStore;

public final class SwiftProxy {
    public static final String PROPERTY_ENDPOINT = "swiftproxy.endpoint";
    private HttpServer server;
    private BlobStore blobStore;
    private URI endpoint;

    public SwiftProxy(BlobStore blobStore, URI endpoint) {
        this.blobStore = checkNotNull(blobStore);
        this.endpoint = checkNotNull(endpoint);

        final ResourceConfig rc = new BounceResourceConfig(blobStore);
        server = GrizzlyHttpServerFactory.createHttpServer(endpoint, rc, false);
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public void start() throws IOException {
        server.start();
    }

    public void stop() {
        server.shutdownNow();
    }

    public static final class Builder {
        private BlobStore blobStore;
        private URI endpoint;

        Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder blobStore(BlobStore newBlobStore) {
            this.blobStore = checkNotNull(newBlobStore);
            return this;
        }

        public Builder endpoint(URI newEndpoint) {
            this.endpoint = checkNotNull(newEndpoint);
            return this;
        }

        public Builder overrides(Properties properties) {
            String provider = properties.getProperty(Constants.PROPERTY_PROVIDER);
            if (provider != null) {
                blobStore(Utils.storeFromProperties(properties));
            }

            String proxyEndpoint = properties.getProperty(SwiftProxy.PROPERTY_ENDPOINT);
            if (proxyEndpoint != null) {
                try {
                    endpoint(new URI(proxyEndpoint));
                } catch (URISyntaxException e) {
                    throw propagate(e);
                }
            }

            return this;
        }

        public SwiftProxy build() {
            return new SwiftProxy(blobStore, endpoint);
        }
    }
}
