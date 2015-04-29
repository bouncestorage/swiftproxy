/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy.v1;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import com.bouncestorage.swiftproxy.BlobStoreResource;
import com.bouncestorage.swiftproxy.BounceResourceConfig;

@Path("/auth/v1.0")
public final class AuthResource extends BlobStoreResource {
    @GET
    public Response auth(@HeaderParam("X-Auth-User") String user,
                         @HeaderParam("X-Auth-Key") String key,
                         @HeaderParam("X-Storage-User") String storageUser,
                         @HeaderParam("X-Storage-Pass") String storagePass) {
        if (user == null && storageUser != null) {
            user = storageUser;
        }
        String endpoint = ((BounceResourceConfig) application).getEndPoint().toString();
        endpoint += "/v1/" + user;
        return Response.ok()
                .header("x-storage-url", endpoint)
                .header("x-auth-token", "foobar")
                .build();
    }
}
