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

package com.bouncestorage.swiftproxy.v1;

import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import com.bouncestorage.swiftproxy.BlobStoreResource;
import com.bouncestorage.swiftproxy.BounceResourceConfig;

import org.glassfish.grizzly.http.server.Request;

/**
 * Implements TempAuth (V1 Auth) for Swift. Documentations:
 * http://docs.openstack.org/developer/swift/overview_auth.html
 * https://swiftstack.com/docs/cookbooks/swift_usage/auth.html
 * http://docs.openstack.org/developer/swift/deployment_guide.html
 */
@Path("/auth/v1.0")
public final class AuthResource extends BlobStoreResource {
    @GET
    public Response auth(@HeaderParam("X-Auth-User") Optional<String> authUser,
                         @HeaderParam("X-Auth-Key") Optional<String> authKey,
                         @HeaderParam("X-Storage-User") Optional<String> storageUser,
                         @HeaderParam("X-Storage-Pass") Optional<String> storagePass,
                         @HeaderParam("Host") Optional<String> host,
                         @Context Request request) {
        String identity = authUser.orElseGet(storageUser::get);
        String credential = authKey.orElseGet(storagePass::get);
        String authToken = null;
        try {
            authToken = ((BounceResourceConfig) application).authenticate(identity, credential);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (authToken == null) {
            return notAuthorized();
        }

        String storageURL = host.orElseGet(() -> request.getLocalAddr() + ":" + request.getLocalPort());
        String scheme = request.getScheme();
        storageURL = scheme + "://" + storageURL + "/v1/AUTH_" + identity;

        return Response.ok()
                .header("x-storage-url", storageURL)
                .header("x-auth-token", authToken)
                .header("x-storage-token", authToken)
                .build();
    }
}
