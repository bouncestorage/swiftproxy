/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import java.net.URI;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import com.google.common.collect.ImmutableMap;

import org.glassfish.jersey.server.ResourceConfig;
import org.jclouds.blobstore.BlobStore;

public final class BounceResourceConfig extends ResourceConfig {
    private static final Map<String, MediaType> swiftFormatToMediaType = ImmutableMap.of(
            "json", MediaType.APPLICATION_JSON_TYPE,
            "xml", MediaType.APPLICATION_XML_TYPE,
            "plain", MediaType.TEXT_PLAIN_TYPE
    );

    private final BlobStore blobStore;
    private URI endPoint;
    private BlobStoreLocator locator;

    BounceResourceConfig(BlobStore blobStore, BlobStoreLocator locator) {
        if (blobStore == null && locator == null) {
            throw new NullPointerException("One of blobStore or locator must be set");
        }
        this.blobStore = blobStore;
        this.locator = locator;
        packages(getClass().getPackage().getName());
    }

    public BlobStore getBlobStore(String identity, String containerName, String blobName) {
        if (locator != null) {
            Map.Entry<String, BlobStore> entry = locator.locateBlobStore(identity, containerName, blobName);
            if (entry != null) {
                return entry.getValue();
            }
        }
        if (blobStore != null) {
            return blobStore;
        }
        throw new NullPointerException("Blob store not found for: " + identity + " " + containerName + " " + blobName);
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
