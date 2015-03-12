/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy.v1;

import javax.validation.constraints.NotNull;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import com.bouncestorage.swiftproxy.BlobStoreResource;

import org.jclouds.blobstore.BlobStore;

@Path("/v1/{account}/{container}")
public final class ContainerResource extends BlobStoreResource {

    @POST
    public Response postContainer(@NotNull @PathParam("container") String container,
                                  @HeaderParam("X-Auth-Token") String authToken,
                                  @HeaderParam("X-Container-Read") String readACL,
                                  @HeaderParam("X-Container-write") String writeACL,
                                  @HeaderParam("X-Container-Sync-To") String syncTo,
                                  @HeaderParam("X-Container-Sync-Key") String syncKey,
                                  @HeaderParam("X-Versions-Location") String versionsLocation,
                                  @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                                  @HeaderParam("X-Detect-Content-Type") boolean detectContentType,
                                  @HeaderParam(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch) {
        getBlobStore().createContainerInLocation(null, container);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @PUT
    public Response putContainer(@NotNull @PathParam("container") String container,
                                  @HeaderParam("X-Auth-Token") String authToken,
                                  @HeaderParam("X-Container-Read") String readACL,
                                  @HeaderParam("X-Container-write") String writeACL,
                                  @HeaderParam("X-Container-Sync-To") String syncTo,
                                  @HeaderParam("X-Container-Sync-Key") String syncKey,
                                  @HeaderParam("X-Versions-Location") String versionsLocation,
                                  @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                                  @HeaderParam("X-Detect-Content-Type") boolean detectContentType,
                                  @HeaderParam(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch) {
        Response.Status status;
        BlobStore store = getBlobStore();

        if (store.containerExists(container)) {
            status = Response.Status.NO_CONTENT;
        } else {
            store.createContainerInLocation(null, container);
            status = Response.Status.CREATED;
        }

        return Response.status(status).build();
    }

    @DELETE
    public Response deleteContainer(@NotNull @PathParam("container") String container,
                                    @HeaderParam("X-Auth-Token") String authToken) {
        BlobStore store = getBlobStore();
        if (!store.containerExists(container)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (store.deleteContainerIfEmpty(container)) {
            return Response.noContent().build();
        } else {
            return Response.status(Response.Status.CONFLICT)
                    .entity("<html><h1>Conflict</h1><p>There was a conflict when trying to complete your request.</p></html>")
                    .build();
        }
    }

    @HEAD
    public Response headContainer(@NotNull @PathParam("container") String container,
                                  @HeaderParam("X-Auth-Token") String authToken,
                                  @HeaderParam("X-Newest") @DefaultValue("false") boolean newest) {
        BlobStore store = getBlobStore();
        if (!store.containerExists(container)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.status(Response.Status.NO_CONTENT)
                .header("X-Container-Object-Count", 0)
                .header("X-Container-Bytes-Used", 0)
                .header("X-Versions-Location", "")
                .build();
    }
}
