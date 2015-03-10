package com.bouncestorage.swiftproxy;

import com.google.common.collect.ImmutableMap;
import org.glassfish.jersey.server.ResourceConfig;
import org.jclouds.blobstore.BlobStore;

import javax.ws.rs.core.MediaType;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class BounceResourceConfig extends ResourceConfig{
    private final BlobStore blobStore;
    private static final Map<String, MediaType> swiftFormatToMediaType = ImmutableMap.of(
            "json", MediaType.APPLICATION_JSON_TYPE,
            "xml", MediaType.APPLICATION_XML_TYPE,
            "plain", MediaType.TEXT_PLAIN_TYPE
    );

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
}
