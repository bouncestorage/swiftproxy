/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;

import org.jclouds.blobstore.BlobStore;

public abstract class BlobStoreResource {
    @Context
    protected Application application;

    protected final BlobStore getBlobStore() {
        return ((BounceResourceConfig) application).getBlobStore();
    }
}
