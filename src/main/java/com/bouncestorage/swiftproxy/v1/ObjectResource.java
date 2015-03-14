/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy.v1;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import com.bouncestorage.swiftproxy.BlobStoreResource;

import org.glassfish.grizzly.http.server.Request;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.BlobBuilder;
import org.jclouds.blobstore.domain.BlobMetadata;

@Path("/v1/{account}/{container}/{object:.*}")
public final class ObjectResource extends BlobStoreResource {
    @GET
    public Response getObject(@NotNull @PathParam("container") String container,
                              @NotNull @PathParam("object") String object,
                              @NotNull @PathParam("account") String account,
                              @HeaderParam("X-Auth-Token") String authToken,
                              @HeaderParam("X-Newest") boolean newest,
                              @QueryParam("signature") String signature,
                              @QueryParam("expires") String expires,
                              @QueryParam("multipart-manifest") String multiPartManifest,
                              @HeaderParam("Range") String range,
                              @HeaderParam("If-Match") String ifMatch,
                              @HeaderParam("If-None-Match") String ifNoneMatch,
                              @HeaderParam("If-Modified-Since") String ifModifiedSince,
                              @HeaderParam("If-Unmodified-Since") String ifUnmodifiedSince) {

        Blob blob = getBlobStore().getBlob(container, object);
        if (blob == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        BlobMetadata meta = blob.getMetadata();
        try (InputStream is = blob.getPayload().openStream()) {
            return addObjectHeaders(meta, Response.ok(is)).build();
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    // TODO: Handle object metadata
    @PUT
    @Consumes(" ")
    public Response putObject(@NotNull @PathParam("container") String container,
                              @NotNull @PathParam("object") String objectName,
                              @NotNull @PathParam("account") String account,
                              @QueryParam("multipart-manifest") boolean multiPartManifest,
                              @QueryParam("signature") String signature,
                              @QueryParam("expires") String expires,
                              @HeaderParam("X-Object-Manifest") String objectManifest,
                              @HeaderParam("X-Auth-Token") String authToken,
                              @HeaderParam(HttpHeaders.CONTENT_LENGTH) int contentLength,
                              @HeaderParam("Transfer-Encoding") String transferEncoding,
                              @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                              @HeaderParam("X-Detect-Content-Type") boolean detectContentType,
                              @HeaderParam("X-Copy-From") String copyFrom,
                              @HeaderParam(HttpHeaders.ETAG) String eTag,
                              @HeaderParam(HttpHeaders.CONTENT_DISPOSITION) String contentDisposition,
                              @HeaderParam(HttpHeaders.CONTENT_ENCODING) String contentEncoding,
                              @HeaderParam("X-Delete-At") long deleteAt,
                              @HeaderParam("X-Delete-After") long deleteAfter,
                              @HeaderParam(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch,
                              @Context Request request) {
        try (InputStream is = request.getInputStream()) {
            BlobBuilder.PayloadBlobBuilder builder = getBlobStore().blobBuilder(objectName)
                    .payload(is)
                    .contentLength(contentLength)
                    .contentType(contentType);
            String remoteETag = getBlobStore().putBlob(container, builder.build());
            return Response.status(Response.Status.CREATED).header(HttpHeaders.ETAG, remoteETag)
                    .header(HttpHeaders.CONTENT_LENGTH, 0)
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.DATE, new Date()).build();
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @HEAD
    public Response headObject(@NotNull @PathParam("container") String container,
                               @NotNull @PathParam("object") String objectName,
                               @NotNull @PathParam("account") String account,
                               @HeaderParam("X-Auth-Token") String authToken) {
        BlobMetadata meta = getBlobStore().blobMetadata(container, objectName);
        if (meta == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // TODO: We should be sending a 204 (No Content), but cannot due to https://java.net/jira/browse/JERSEY-2822
        return addObjectHeaders(meta, Response.ok()).build();
    }

    @DELETE
    public Response deleteObject(@NotNull @PathParam("account") String account,
                                 @NotNull @PathParam("container") String container,
                                 @NotNull @PathParam("object") String objectName,
                                 @QueryParam("multipart-manifest") String multipartManifest,
                                 @HeaderParam("X-Auth-Token") String authToken) {
        BlobStore store = getBlobStore();
        BlobMetadata meta = store.blobMetadata(container, objectName);
        if (meta == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.noContent()
                .type(meta.getContentMetadata().getContentType())
                .build();
    }

    private Response.ResponseBuilder addObjectHeaders(BlobMetadata metaData, Response.ResponseBuilder responseBuilder) {
        return responseBuilder.header(HttpHeaders.CONTENT_LENGTH, metaData.getContentMetadata().getContentLength())
                .header(HttpHeaders.LAST_MODIFIED, metaData.getLastModified())
                .header(HttpHeaders.ETAG, metaData.getETag())
                .header("X-Static-Large-Object", false)
                .header(HttpHeaders.DATE, new Date())
                .header(HttpHeaders.CONTENT_TYPE, metaData.getContentMetadata().getContentType());
    }
}
