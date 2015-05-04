/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy.v1;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import com.bouncestorage.swiftproxy.BlobStoreLocator;
import com.bouncestorage.swiftproxy.BlobStoreResource;
import org.glassfish.grizzly.http.server.Request;

import java.util.Optional;

@Path("/auth/v1.0")
public final class AuthResource extends BlobStoreResource {
    @GET
    public Response auth(@HeaderParam("X-Auth-User") String user,
                         @HeaderParam("X-Auth-Key") String key,
                         @HeaderParam("X-Storage-User") String storageUser,
                         @HeaderParam("X-Storage-Pass") String storagePass,
                         @HeaderParam("Host") Optional<String> host,
                         @Context Request request) {
        if (user == null && storageUser != null) {
            user = storageUser;
        }

        String storageURL = host.orElseGet(() -> request.getLocalAddr() + ":" + request.getLocalPort());
        String scheme = request.getScheme();
        storageURL = scheme + "://" + storageURL + "/v1/" + user;

        String authToken = user + BlobStoreLocator.TOKEN_SEPARATOR + "foobar";
        return Response.ok()
                .header("x-storage-url", storageURL)
                .header("x-auth-token", authToken)
                .build();
    }
}
