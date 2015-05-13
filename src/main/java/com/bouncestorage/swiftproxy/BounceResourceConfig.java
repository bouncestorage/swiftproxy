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

import static com.google.common.base.Throwables.propagate;

import java.net.URI;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.MediaType;

import com.bouncestorage.swiftproxy.v1.InfoResource;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;

import org.apache.commons.lang3.RandomStringUtils;

import org.glassfish.jersey.server.ResourceConfig;

import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BounceResourceConfig extends ResourceConfig {
    private static final Map<String, MediaType> swiftFormatToMediaType = ImmutableMap.of(
            "json", MediaType.APPLICATION_JSON_TYPE,
            "application/json", MediaType.APPLICATION_JSON_TYPE,
            "xml", MediaType.APPLICATION_XML_TYPE,
            "plain", MediaType.TEXT_PLAIN_TYPE
    );

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Properties properties;
    private URI endPoint;
    private BlobStoreLocator locator;
    private Cache<String, BlobStore> tokensToBlobStore = CacheBuilder.newBuilder()
            .expireAfterWrite(InfoResource.CONFIG.tempauth.token_life, TimeUnit.SECONDS)
            .build();

    BounceResourceConfig(Properties properties, BlobStoreLocator locator) {
        if (properties == null && locator == null) {
            throw new NullPointerException("One of properties or locator must be set");
        }
        this.properties = properties;
        this.locator = locator;
        packages(getClass().getPackage().getName());
    }

    public String authenticate(String identity, String credential) {
        BlobStore blobStore = tryAuthenticate(identity, credential);
        if (blobStore != null) {
            String token = "AUTH_tk" + RandomStringUtils.randomAlphanumeric(32);
            tokensToBlobStore.put(token, blobStore);
            return token;
        }

        return null;
    }

    private BlobStore tryAuthenticate(String identity, String credential) {
        if (locator != null) {
            Map.Entry<String, BlobStore> entry = locator.locateBlobStore(identity, null, null);
            if (entry != null && entry.getKey().equals(credential)) {
                logger.debug("blob store for {} found", identity);
                return entry.getValue();
            } else {
                logger.debug("blob store for {} not found", identity);
            }
        }

        logger.debug("fallback to authenticate with configured provider");
        try {
            BlobStoreContext context = ContextBuilder
                    .newBuilder(properties.getProperty(Constants.PROPERTY_PROVIDER))
                    .overrides(properties)
                    .credentials(identity, credential)
                    .build(BlobStoreContext.class);
            return context.getBlobStore();
        } catch (Throwable e) {
            throw propagate(e);
        }
    }

    public BlobStore getBlobStore(String authToken) {
        return tokensToBlobStore.getIfPresent(authToken);
    }

    public static MediaType getMediaType(String format) {
        return swiftFormatToMediaType.get(format);
    }

    public void setEndPoint(URI endPoint) {
        this.endPoint = endPoint;
    }

    public URI getEndPoint() {
        return endPoint;
    }

    public boolean isLocatorSet() {
        return locator != null;
    }

    public void setBlobStoreLocator(BlobStoreLocator newLocator) {
        locator = newLocator;
    }
}
