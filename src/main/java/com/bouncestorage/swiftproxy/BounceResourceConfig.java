/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import com.google.common.collect.ImmutableMap;

import org.glassfish.jersey.server.ResourceConfig;
import org.jclouds.blobstore.BlobStore;

public final class BounceResourceConfig extends ResourceConfig {
    private static final Map<String, MediaType> swiftFormatToMediaType = ImmutableMap.of(
            "json", MediaType.APPLICATION_JSON_TYPE,
            "application/json", MediaType.APPLICATION_JSON_TYPE,
            "xml", MediaType.APPLICATION_XML_TYPE,
            "plain", MediaType.TEXT_PLAIN_TYPE
    );

    private final BlobStore blobStore;
    private URI endPoint;

    BounceResourceConfig(BlobStore blobStore) {
        this.blobStore = checkNotNull(blobStore);
        packages(getClass().getPackage().getName());
    }

    public BlobStore getBlobStore() {
        return blobStore;
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
}
