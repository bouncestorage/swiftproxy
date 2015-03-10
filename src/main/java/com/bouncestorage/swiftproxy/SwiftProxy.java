package com.bouncestorage.swiftproxy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Module;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;

public class SwiftProxy {
    public final static String PROPERTY_ENDPOINT = "swiftproxy.endpoint";
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
        server.stop();
    }

    public final static class Builder {
        private BlobStore blobStore;
        private URI endpoint;

        Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder blobStore(BlobStore blobStore) {
            this.blobStore = checkNotNull(blobStore);
            return this;
        }

        public Builder endpoint(URI endpoint) {
            this.endpoint = checkNotNull(endpoint);
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
