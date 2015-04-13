/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy.v1;

import static com.google.common.base.Throwables.propagate;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import com.bouncestorage.swiftproxy.BlobStoreResource;
import com.bouncestorage.swiftproxy.COPY;
import com.bouncestorage.swiftproxy.Utils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.utils.Pair;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.ContainerNotFoundException;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.BlobBuilder;
import org.jclouds.blobstore.domain.BlobMetadata;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.options.CopyOptions;
import org.jclouds.blobstore.options.GetOptions;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.jclouds.http.HttpResponseException;
import org.jclouds.io.ContentMetadata;
import org.jclouds.io.MutableContentMetadata;
import org.jclouds.io.payloads.InputStreamPayload;
import org.jclouds.openstack.swift.v1.CopyObjectException;

@Path("/v1/{account}/{container}/{object:.*}")
public final class ObjectResource extends BlobStoreResource {
    private static final String META_HEADER_PREFIX = "x-object-meta-";
    private static final int MAX_OBJECT_NAME_LENGTH = 1024;
    private static final String DYNAMIC_OBJECT_MANIFEST = "x-object-manifest";
    private static final Set<String> RESERVED_METADATA = ImmutableSet.of(
            DYNAMIC_OBJECT_MANIFEST
    );

    private static GetOptions parseRange(GetOptions options, String range) {
        if (range != null) {
            range = range.replaceAll(" ", "").toLowerCase();
            String bytesUnit = "bytes=";
            int idx = range.indexOf(bytesUnit);
            if (idx == 0) {
                String byteRangeSet = range.substring(bytesUnit.length());
                Iterator<Object> iter = Iterators.forEnumeration(new StringTokenizer(byteRangeSet, ","));
                StreamSupport.stream(Spliterators.spliteratorUnknownSize(iter, Spliterator.ORDERED), false)
                        .map(rangeSpec -> (String) rangeSpec)
                        .map(rangeSpec -> {
                            int dash = rangeSpec.indexOf("-");
                            if (dash == -1) {
                                throw new BadRequestException("Range");
                            }
                            String firstBytePos = rangeSpec.substring(0, dash);
                            String lastBytePos = rangeSpec.substring(dash + 1);
                            Long firstByte = firstBytePos.isEmpty() ? null : Long.parseLong(firstBytePos);
                            Long lastByte = lastBytePos.isEmpty() ? null : Long.parseLong(lastBytePos);
                            return new Pair<>(firstByte, lastByte);
                        })
                        .forEach(rangeSpec -> {
                            if (rangeSpec.getFirst() == null) {
                                if (rangeSpec.getSecond() == 0) {
                                    throw new ClientErrorException(Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE);
                                }
                                options.tail(rangeSpec.getSecond());
                            } else if (rangeSpec.getSecond() == null) {
                                options.startAt(rangeSpec.getFirst());
                            } else {
                                options.range(rangeSpec.getFirst(), rangeSpec.getSecond());
                            }
                        });
            }
        }
        return options;
    }

    @GET
    public Response getObject(@NotNull @PathParam("container") String container,
                              @NotNull @Encoded @PathParam("object") String object,
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
        GetOptions options = parseRange(new GetOptions(), range);
        BlobStore blobStore = getBlobStore();

        Blob blob = blobStore.getBlob(container, object, options);
        if (blob == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        BlobMetadata meta = blob.getMetadata();
        if (multiPartManifest == null && meta.getUserMetadata().containsKey(DYNAMIC_OBJECT_MANIFEST)) {
            return getDloObject(blobStore, meta);
        } else {
            try (InputStream is = blob.getPayload().openStream()) {
                return addObjectHeaders(meta, Response.ok(is)).build();
            } catch (IOException e) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }
        }
    }

    private Response getDloObject(BlobStore blobStore, BlobMetadata meta) {
        Hasher hash = Hashing.md5().newHasher();
        String manifest = meta.getUserMetadata().get(DYNAMIC_OBJECT_MANIFEST);
        Pair<String, String> param = validateCopyParam(manifest);
        String dloContainer = param.getFirst();
        String objectsPrefix = param.getSecond();
        ListContainerOptions listOptions = new ListContainerOptions()
                .recursive()
                .afterMarker(objectsPrefix)
                .withDetails();
        Iterable<StorageMetadata> res = Utils.crawlBlobStore(blobStore, dloContainer, listOptions);
        List<InputStream> segmentStreams = new ArrayList<>();
        long segmentsTotalLength = 0;
        for (StorageMetadata sm : res) {
            if (sm.getName().startsWith(objectsPrefix)) {
                Blob segment = blobStore.getBlob(dloContainer, sm.getName());
                if (segment != null) {
                    try {
                        segmentStreams.add(segment.getPayload().openStream());
                        segmentsTotalLength += segment.getMetadata().getSize();
                        hash.putString(segment.getMetadata().getETag(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw propagate(e);
                    }
                }
            } else {
                break;
            }
        }

        SequenceInputStream combined =
                new SequenceInputStream(Iterators.asEnumeration(segmentStreams.iterator()));
        return addObjectHeaders(meta, segmentsTotalLength,
                '"' + hash.hash().toString() + '"', Response.ok(combined)).build();
    }

    private static String normalizePath(String pathName) {
        String objectName = Joiner.on("/").join(Iterators.forEnumeration(new StringTokenizer(pathName, "/")));
        if (pathName.endsWith("/")) {
            objectName += "/";
        }
        return objectName;
    }

    private static String contentType(String contentType) {
        // workaround the stupidity in jclouds where it always strip trailing / from
        // blob name listings. this allows us to detect that it's happened
        if ("application/directory".equals(contentType)) {
            return "application/x-directory";
        }
        return contentType;
    }

    private Map<String, String> getUserMetadata(Request request) {
        return StreamSupport.stream(request.getHeaderNames().spliterator(), false)
                .peek(name -> logger.info("header: {}", name))
                .filter(name -> name.toLowerCase().startsWith(META_HEADER_PREFIX))
                .filter(name -> {
                    if (name.equals(META_HEADER_PREFIX) || RESERVED_METADATA.contains(name)) {
                        throw new BadRequestException();
                    }
                    return true;
                })
                .collect(Collectors.toMap(
                        name -> name.substring(META_HEADER_PREFIX.length()),
                        name -> request.getHeader(name)));
    }

    private static Pair<String, String> validateCopyParam(String destination) {
        if (destination == null) {
            return null;
        }
        Pair<String, String> res;

        if (destination.charAt(0) == '/') {
            String[] tokens = destination.split("/", 3);
            if (tokens.length != 3) {
                return null;
            }
            res = new Pair<>(tokens[1], tokens[2]);
        } else {
            String[] tokens = destination.split("/", 2);
            if (tokens.length != 2) {
                return null;
            }
            res = new Pair<>(tokens[0], tokens[1]);
        }

        return res;
    }

    private String emulatedCopyBlob(BlobMetadata meta, String destContainer, String destObject,
                                    CopyOptions options) {
        BlobStore blobStore = getBlobStore();
        Response resp = getDloObject(blobStore, meta);
        Response.StatusType statusInfo = resp.getStatusInfo();
        if (statusInfo.equals(Response.Status.OK)) {
            ContentMetadata contentMetadata = meta.getContentMetadata();
            Map newMetadata = new HashMap<>();
            newMetadata.putAll(meta.getUserMetadata());
            newMetadata.putAll(options.getUserMetadata().or(ImmutableMap.of()));
            newMetadata.remove(DYNAMIC_OBJECT_MANIFEST);
            Blob blob = blobStore.blobBuilder(destObject)
                    .userMetadata(newMetadata)
                    .payload(new InputStreamPayload((InputStream) resp.getEntity()))
                    .contentLength(resp.getLength())
                    .contentDisposition(contentMetadata.getContentDisposition())
                    .contentEncoding(contentMetadata.getContentEncoding())
                    .contentType(contentMetadata.getContentType())
                    .contentLanguage(contentMetadata.getContentLanguage())
                    .build();
            return blobStore.putBlob(destContainer, blob);
        } else {
            throw new ClientErrorException(statusInfo.getReasonPhrase(), statusInfo.getStatusCode());
        }
    }

    private String serverCopyBlob(String container, String objectName, String destContainer,
                                  String destObject, CopyOptions options) {
        try {
            return getBlobStore().copyBlob(container, objectName, destContainer, destObject, options);
        } catch (CopyObjectException e) {
            if (e.getCause() instanceof HttpResponseException) {
                throw (HttpResponseException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    @COPY
    @Consumes(" ")
    public Response copyObject(@NotNull @PathParam("container") String container,
                               @NotNull @Encoded @PathParam("object") String objectName,
                               @NotNull @PathParam("account") String account,
                               @HeaderParam("X-Auth-Token") String authToken,
                               @NotNull @HeaderParam("Destination") String destination,
                               @NotNull @HeaderParam("Destination-Account") String destAccount,
                               @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                               @HeaderParam(HttpHeaders.CONTENT_ENCODING) String contentEncoding,
                               @HeaderParam(HttpHeaders.CONTENT_DISPOSITION) String contentDisposition,
                               @Context Request request) {
        if (objectName.length() > MAX_OBJECT_NAME_LENGTH) {
            return badRequest();
        }

        Pair<String, String> dest = validateCopyParam(destination);
        if (dest == null) {
            return Response.status(Response.Status.PRECONDITION_FAILED).build();
        }

        String destContainer = dest.getFirst();
        String destObject = dest.getSecond();
        // TODO: unused
        if (destAccount == null) {
            destAccount = account;
        }
        if (destObject.length() > MAX_OBJECT_NAME_LENGTH) {
            return badRequest();
        }

        logger.info("copy {}/{} to {}/{}", container, objectName, destContainer, destObject);

        Map<String, String> additionalUserMeta = getUserMetadata(request);

        BlobStore blobStore = getBlobStore();
        if (!blobStore.containerExists(container) || !blobStore.containerExists(destContainer)) {
            return notFound();
        }
        String copiedFrom;
        try {
            copiedFrom = container + "/" + URLDecoder.decode(objectName, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw propagate(e);
        }

        BlobMetadata meta = blobStore.blobMetadata(container, objectName);
        if (meta == null) {
            return notFound();
        }

        CopyOptions options;
        if (additionalUserMeta.isEmpty()) {
            options = CopyOptions.NONE;
        } else {
            Map newMetadata = new HashMap<>();
            newMetadata.putAll(meta.getUserMetadata());
            newMetadata.putAll(additionalUserMeta);

            options = CopyOptions.builder()
                    .userMetadata(newMetadata)
                    .build();
        }

        Map<String, String> userMetadata = meta.getUserMetadata();
        String etag;
        if (userMetadata.containsKey(DYNAMIC_OBJECT_MANIFEST)) {
            // copy is supposed to flatten the large object, which we have to emulate
            etag = emulatedCopyBlob(meta, destContainer, destObject, options);
        } else {
            etag = serverCopyBlob(container, objectName, destContainer, destObject, options);
        }
        return Response.status(Response.Status.CREATED)
                .header(HttpHeaders.ETAG, etag)
                .header(HttpHeaders.CONTENT_LENGTH, 0)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.DATE, new Date())
                .header("X-Copied-From", copiedFrom)
                .build();
    }

    // TODO: actually handle this, jclouds doesn't support metadata update yet
    @POST
    @Consumes(" ")
    public Response postObject(@NotNull @PathParam("container") String container,
                               @NotNull @Encoded @PathParam("object") String objectName,
                               @NotNull @PathParam("account") String account,
                               @HeaderParam("X-Auth-Token") String authToken,
                               @HeaderParam("X-Delete-At") long deleteAt,
                               @HeaderParam(HttpHeaders.CONTENT_DISPOSITION) String contentDisposition,
                               @HeaderParam(HttpHeaders.CONTENT_ENCODING) String contentEncoding,
                               @HeaderParam("X-Delete-After") long deleteAfter,
                               @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                               @HeaderParam("X-Detect-Content-Type") boolean detectContentType,
                               @Context Request request) {
        if (objectName.length() > MAX_OBJECT_NAME_LENGTH) {
            return badRequest();
        }

        if (!getBlobStore().containerExists(container)) {
            return notFound();
        }
        Blob blob = getBlobStore().getBlob(container, objectName);
        if (blob == null) {
            return notFound();
        }
        Map<String, String> newMetadata = getUserMetadata(request);
        Map<String, String> originalMetadata = blob.getMetadata().getUserMetadata();
        RESERVED_METADATA.stream()
                .filter(k -> originalMetadata.containsKey(k))
                .forEach(k -> newMetadata.put(k, originalMetadata.get(k)));
        blob.getMetadata().setUserMetadata(newMetadata);
        copyContentHeaders(blob, contentDisposition, contentEncoding, contentType);

        getBlobStore().putBlob(container, blob);
        return Response.accepted()
                .header(HttpHeaders.CONTENT_LENGTH, 0)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.DATE, new Date())
                .build();
    }

    private static void copyContentHeaders(Blob blob, String contentDisposition, String contentEncoding, String contentType) {
        MutableContentMetadata contentMetadata = blob.getMetadata().getContentMetadata();
        if (contentDisposition != null) {
            contentMetadata.setContentDisposition(contentDisposition);
        }
        if (contentType != null) {
            contentMetadata.setContentType(contentType);
        }
        if (contentEncoding != null) {
            contentMetadata.setContentEncoding(contentEncoding);
        }
    }

    @PUT
    public Response putObject(@NotNull @PathParam("container") String container,
                              @NotNull @Encoded @PathParam("object") String objectName,
                              @NotNull @PathParam("account") String account,
                              @QueryParam("multipart-manifest") boolean multiPartManifest,
                              @QueryParam("signature") String signature,
                              @QueryParam("expires") String expires,
                              @HeaderParam(DYNAMIC_OBJECT_MANIFEST) String objectManifest,
                              @HeaderParam("X-Auth-Token") String authToken,
                              @HeaderParam(HttpHeaders.CONTENT_LENGTH) String contentLengthParam,
                              @HeaderParam("Transfer-Encoding") String transferEncoding,
                              @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                              @HeaderParam("X-Detect-Content-Type") boolean detectContentType,
                              @HeaderParam("X-Copy-From") String copyFrom,
                              @HeaderParam("X-Copy-From-Account") String copyFromAccount,
                              @HeaderParam(HttpHeaders.ETAG) String eTag,
                              @HeaderParam(HttpHeaders.CONTENT_DISPOSITION) String contentDisposition,
                              @HeaderParam(HttpHeaders.CONTENT_ENCODING) String contentEncoding,
                              @HeaderParam("X-Delete-At") long deleteAt,
                              @HeaderParam("X-Delete-After") long deleteAfter,
                              @HeaderParam(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch,
                              @Context Request request) {
        //objectName = normalizePath(objectName);
        if (objectName.length() > MAX_OBJECT_NAME_LENGTH) {
            return badRequest();
        }
        if (transferEncoding != null && !"chunked".equals(transferEncoding)) {
            return Response.status(Response.Status.NOT_IMPLEMENTED).build();
        }

        if (contentLengthParam == null && !"chunked".equals(transferEncoding)) {
            return Response.status(Response.Status.LENGTH_REQUIRED).build();
        }
        long contentLength = contentLengthParam == null ? 0 : Long.parseLong(contentLengthParam);

        logger.info("PUT {}", objectName);

        if (copyFromAccount == null) {
            copyFromAccount = account;
        }

        if (copyFrom != null) {
            Pair<String, String> copy = validateCopyParam(copyFrom);
            return copyObject(copy.getFirst(), copy.getSecond(), copyFromAccount, authToken,
                    container + "/" + objectName, account, contentType, contentEncoding, contentDisposition,
                    request);
        }

        Map<String, String> metadata = getUserMetadata(request);
        if (objectManifest != null) {
            metadata.put(DYNAMIC_OBJECT_MANIFEST, objectManifest);
        }

        if (!getBlobStore().containerExists(container)) {
            return notFound();
        }

        HashCode contentMD5 = null;
        if (eTag != null) {
            try {
                contentMD5 = HashCode.fromBytes(Base64.getDecoder().decode(eTag));
            } catch (IllegalArgumentException iae) {
                throw new ClientErrorException(422); // Unprocessable Entity
            }
            if (contentMD5.bits() != Hashing.md5().bits()) {
                throw new ClientErrorException(422); // Unprocessable Entity
            }
        }

        try (InputStream is = request.getInputStream()) {
            BlobBuilder.PayloadBlobBuilder builder = getBlobStore().blobBuilder(objectName)
                    .userMetadata(metadata)
                    .payload(is)
                    .contentType(contentType(contentType));
            if (contentLengthParam != null) {
                builder.contentLength(contentLength);
            }
            if (contentMD5 != null) {
                builder.contentMD5(contentMD5);
            }
            try {
                String remoteETag = getBlobStore().putBlob(container, builder.build());
                return Response.status(Response.Status.CREATED).header(HttpHeaders.ETAG, remoteETag)
                        .header(HttpHeaders.CONTENT_LENGTH, 0)
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .header(HttpHeaders.DATE, new Date()).build();
            } catch (ContainerNotFoundException e) {
                return notFound();
            }
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @HEAD
    public Response headObject(@NotNull @PathParam("container") String container,
                               @NotNull @Encoded @PathParam("object") String objectName,
                               @NotNull @PathParam("account") String account,
                               @HeaderParam("X-Auth-Token") String authToken) {
        if (objectName.length() > MAX_OBJECT_NAME_LENGTH) {
            return badRequest();
        }

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
                                 @NotNull @Encoded @PathParam("object") String objectName,
                                 @QueryParam("multipart-manifest") String multipartManifest,
                                 @HeaderParam("X-Auth-Token") String authToken) {
        if (objectName.length() > MAX_OBJECT_NAME_LENGTH) {
            return badRequest();
        }

        BlobStore store = getBlobStore();
        if (!store.containerExists(container)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        BlobMetadata meta = store.blobMetadata(container, objectName);
        if (meta == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        store.removeBlob(container, objectName);

        return Response.noContent()
                .type(meta.getContentMetadata().getContentType())
                .build();
    }

    private Response.ResponseBuilder addObjectHeaders(BlobMetadata metaData, long contentLength,
                                                      String etag, Response.ResponseBuilder responseBuilder) {
        Map<String, String> userMetadata = metaData.getUserMetadata();
        userMetadata.entrySet().stream()
                .filter(entry -> !RESERVED_METADATA.contains(entry.getKey()))
                .forEach(entry -> responseBuilder.header(META_HEADER_PREFIX + entry.getKey(), entry.getValue()));
        if (userMetadata.containsKey(DYNAMIC_OBJECT_MANIFEST)) {
            responseBuilder.header(DYNAMIC_OBJECT_MANIFEST, userMetadata.get(DYNAMIC_OBJECT_MANIFEST));
        }
        return responseBuilder.header(HttpHeaders.CONTENT_LENGTH, contentLength)
                .header(HttpHeaders.LAST_MODIFIED, metaData.getLastModified())
                .header(HttpHeaders.ETAG, etag)
                .header("X-Static-Large-Object", false)
                .header(HttpHeaders.DATE, new Date())
                .header(HttpHeaders.CONTENT_TYPE, metaData.getContentMetadata().getContentType());
    }
    private Response.ResponseBuilder addObjectHeaders(BlobMetadata metaData, Response.ResponseBuilder responseBuilder) {
        return addObjectHeaders(metaData, metaData.getSize(), metaData.getETag(), responseBuilder);
    }
}
