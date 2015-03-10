package com.bouncestorage.swiftproxy.v1;

import com.bouncestorage.swiftproxy.BlobStoreResource;

import javax.validation.constraints.NotNull;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

@Path("/v1/{account}/{container}")
public class ContainerResource extends BlobStoreResource {

    @POST
    public Response postContainer(@NotNull @PathParam("container") String container,
                                  @HeaderParam("X-Auth-Token") String authToken,
                                  @HeaderParam("X-Container-Read") String readACL,
                                  @HeaderParam("X-Container-write") String writeACL,
                                  @HeaderParam("X-Container-Sync-To") String syncTo,
                                  @HeaderParam("X-Container-Sync-Key") String syncKey,
                                  @HeaderParam("X-Versions-Location") String versionsLocation,
                                  @HeaderParam("Content-Type") String contentType,
                                  @HeaderParam("X-Detect-Content-Type") boolean detectContentType,
                                  @HeaderParam("If-None-Match") String ifNoneMatch) {
        getBlobStore().createContainerInLocation(null, container);
        return Response.ok().build();
    }
}
