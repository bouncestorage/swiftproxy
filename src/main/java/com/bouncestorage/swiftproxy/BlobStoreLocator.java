/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import java.util.Map;

import org.jclouds.blobstore.BlobStore;

public interface BlobStoreLocator {
    String TOKEN_SEPARATOR = "TOKEN";

    Map.Entry<String, BlobStore> locateBlobStore(String identity, String container, String blob);
}
