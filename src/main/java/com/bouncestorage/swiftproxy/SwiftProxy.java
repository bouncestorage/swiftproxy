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

import static java.util.Objects.requireNonNull;

import static com.google.common.base.Throwables.propagate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import javax.ws.rs.ext.RuntimeDelegate;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SwiftProxy {
    public static final String PROPERTY_ENDPOINT = "swiftproxy.endpoint";
    private Logger logger = LoggerFactory.getLogger(getClass());
    private HttpServer server;
    private URI endpoint;
    private final BounceResourceConfig rc;

    public SwiftProxy(Properties properties, BlobStoreLocator locator, URI endpoint) {
        this.endpoint = requireNonNull(endpoint);

        rc = new BounceResourceConfig(properties, locator);
        if (logger.isDebugEnabled()) {
            rc.register(new LoggingFilter(java.util.logging.Logger.getGlobal(), true));
        }
        server = GrizzlyHttpServerFactory.createHttpServer(endpoint, rc, false);
        RuntimeDelegate.setInstance(new RuntimeDelegateImpl(RuntimeDelegate.getInstance()));
    }

    public void setBlobStoreLocator(BlobStoreLocator locator) {
        rc.setBlobStoreLocator(locator);
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public void start() throws IOException, URISyntaxException {
        server.start();
        endpoint = new URI(endpoint.getScheme(), endpoint.getUserInfo(), endpoint.getHost(),
                getPort(), endpoint.getPath(), endpoint.getQuery(), endpoint.getFragment());
        rc.setEndPoint(endpoint);
    }

    public void stop() {
        server.shutdownNow();
    }

    public int getPort() {
        return server.getListeners().stream().findAny().map(n -> n.getPort()).orElse(0);
    }

    public boolean isStarted() {
        return server.isStarted();
    }

    public static final class Builder {
        private BlobStoreLocator locator;
        private URI endpoint;
        private Properties properties;

        Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder endpoint(URI newEndpoint) {
            this.endpoint = requireNonNull(newEndpoint);
            return this;
        }

        public Builder locator(BlobStoreLocator newLocator) {
            this.locator = newLocator;
            return this;
        }

        public Builder overrides(Properties prop) {
            this.properties = requireNonNull(prop);

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
            return new SwiftProxy(properties, locator, endpoint);
        }
    }
}
