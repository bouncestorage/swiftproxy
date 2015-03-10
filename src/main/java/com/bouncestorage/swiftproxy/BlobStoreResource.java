package com.bouncestorage.swiftproxy;

import org.jclouds.blobstore.BlobStore;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;

public abstract class BlobStoreResource {
    @Context
    protected Application application;

    protected BlobStore getBlobStore() {
        return ((BounceResourceConfig)application).getBlobStore();
    }
}
