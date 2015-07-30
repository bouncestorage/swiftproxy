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

import static java.util.Objects.requireNonNull;

import static com.google.common.base.Throwables.propagate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.StringTokenizer;
import java.util.function.Supplier;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.bouncestorage.swiftproxy.BlobStoreResource;
import com.bouncestorage.swiftproxy.COPY;
import com.bouncestorage.swiftproxy.Utils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multimap;
import com.google.common.collect.PeekingIterator;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;

import org.apache.commons.io.input.TeeInputStream;
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
import org.jclouds.http.HttpResponse;
import org.jclouds.http.HttpResponseException;
import org.jclouds.io.ContentMetadata;
import org.jclouds.io.MutableContentMetadata;
import org.jclouds.io.payloads.InputStreamPayload;

@Path("/v1/{account}/{container}/{object:.*}")
public final class ObjectResource extends BlobStoreResource {
    private static final String META_HEADER_PREFIX = "X-Object-Meta-";
    private static final String DYNAMIC_OBJECT_MANIFEST = "x-object-manifest";
    private static final String STATIC_OBJECT_MANIFEST = "x-static-large-object";
    private static final Set<String> RESERVED_METADATA = ImmutableSet.of(
            DYNAMIC_OBJECT_MANIFEST,
            STATIC_OBJECT_MANIFEST
    );
    private static final MediaType MANIFEST_CONTENT_TYPE = MediaType.APPLICATION_JSON_TYPE.withCharset("utf-8");
    private static final Set<String> STD_BLOB_HEADERS = ImmutableSet.of(
            "Content-Range"
    );

    private List<Pair<Long, Long>> parseRange(String range) {
        range = range.replaceAll(" ", "").toLowerCase();
        String bytesUnit = "bytes=";
        int idx = range.indexOf(bytesUnit);
        if (idx == 0) {
            String byteRangeSet = range.substring(bytesUnit.length());
            Iterator<Object> iter = Iterators.forEnumeration(new StringTokenizer(byteRangeSet, ","));
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iter, Spliterator.ORDERED), false)
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
                    .peek(r -> logger.debug("parsed range {} {}", r.getFirst(), r.getSecond()))
                    .collect(Collectors.toList());
        } else {
            return null;
        }
    }

    private static GetOptions addRanges(GetOptions options, List<Pair<Long, Long>> ranges) {
        ranges.forEach(rangeSpec -> {
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
        return options;
    }

    private static String maybeUnquote(String quoted) {
        if (quoted.startsWith("\"") && quoted.endsWith("\"")) {
            return quoted.substring(1, quoted.length() - 1);
        }

        return quoted;
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
                              @HeaderParam("If-Modified-Since") Date ifModifiedSince,
                              @HeaderParam("If-Unmodified-Since") Date ifUnmodifiedSince) {
        logger.debug("GET account={} container={} object={}", account, container, object);
        BlobStore blobStore = getBlobStore(authToken).get(container, object);

        if (!blobStore.containerExists(container)) {
            return notFound();
        }

        GetOptions options = new GetOptions();
        List<Pair<Long, Long>> ranges = null;
        if (range != null) {
            ranges = parseRange(range);
            if (ranges != null) {
                options = addRanges(options, ranges);
            }
        }

        if (ifMatch != null) {
            options.ifETagMatches(maybeUnquote(ifMatch));
        }
        if (ifNoneMatch != null) {
            options.ifETagDoesntMatch(maybeUnquote(ifNoneMatch));
        }
        if (ifModifiedSince != null) {
            options.ifModifiedSince(ifModifiedSince);
        }
        if (ifUnmodifiedSince != null) {
            options.ifUnmodifiedSince(ifUnmodifiedSince);
        }

        return getObject(blobStore, container, object, options, ranges, "get".equals(multiPartManifest));
    }

    private Map<String, Object> blobGetStandardHeaders(Blob blob) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        Multimap<String, String> headers = blob.getAllHeaders();
        for (String h : STD_BLOB_HEADERS) {
            if (headers.containsKey(h)) {
                builder.put(h, headers.get(h).iterator().next());
            }
        }

        return builder.build();
    }

    private Response getObject(BlobStore blobStore, String container, String object, GetOptions options,
                               List<Pair<Long, Long>> ranges, boolean multiPartManifest) {
        Blob blob = null;
        BlobMetadata meta;
        if (ranges == null || multiPartManifest) {
            blob = blobStore.getBlob(container, object, options);
            if (blob == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            meta = blob.getMetadata();
        } else {
            logger.debug("range get, check to see if object is a large object");
            meta = blobStore.blobMetadata(container, object);
            if (meta == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        }

        boolean isMultiPartManifest = false;
        if (meta.getUserMetadata().containsKey(DYNAMIC_OBJECT_MANIFEST)) {
            isMultiPartManifest = true;
            if (!multiPartManifest) {
                return getDloObject(blobStore, meta, ranges);
            }
        } else if (meta.getUserMetadata().containsKey(STATIC_OBJECT_MANIFEST)) {
            isMultiPartManifest = true;
            if (!multiPartManifest) {
                if (blob == null) {
                    blob = blobStore.getBlob(container, object);
                }
                return getSloObject(blobStore, blob, ranges);
            }
        } else if (blob == null) {
            // this is just a normal blob
            try {
                blob = blobStore.getBlob(container, object, options);
            } catch (IllegalArgumentException e) {
                if (ranges != null) {
                    throw requestRangeNotSatisfiable();
                } else {
                    throw e;
                }
            }
            meta = blob.getMetadata();
        }

        try {
            return addObjectHeaders(Response.ok(blob.getPayload().openStream()), meta,
                    isMultiPartManifest ?
                            Optional.of(ImmutableMap.of(HttpHeaders.CONTENT_TYPE, MANIFEST_CONTENT_TYPE)) :
                            Optional.of(blobGetStandardHeaders(blob)))
                    .build();
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    private ClientErrorException requestRangeNotSatisfiable() {
        throw new ClientErrorException(Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE);
    }

    private long getTotalRangesLength(List<Pair<Long, Long>> ranges, long totalSize) {
        return ranges.stream().mapToLong(r -> {
            if (r.getFirst() == null) {
                if (r.getSecond() > totalSize) {
                    throw requestRangeNotSatisfiable();
                }
                return r.getSecond();
            } else {
                if (r.getFirst() >= totalSize) {
                    throw requestRangeNotSatisfiable();
                }
                if (r.getSecond() == null) {
                    return totalSize - r.getFirst();
                } else {
                    return r.getSecond() - r.getFirst() + 1;
                }
            }
        }).sum();
    }

    private Response getSloObject(BlobStore blobStore, Blob blob, List<Pair<Long, Long>> ranges) {
        try {
            Iterable<ManifestEntry> entries = Arrays.asList(readSLOManifest(blob.getPayload().openStream()));
            Pair<Long, String> sizeAndEtag = getManifestTotalSizeAndETag(entries);
            logger.debug("getting SLO object: {}", sizeAndEtag);
            entries.forEach(e -> logger.debug("sub-object: {}", e));

            InputStream combined = new ManifestObjectInputStream(blobStore, entries);
            long size = sizeAndEtag.getFirst();
            if (ranges != null) {
                combined = new HttpRangeInputStream(combined, sizeAndEtag.getFirst(), ranges);
                size = getTotalRangesLength(ranges, size);
                logger.debug("range request for {} bytes", size);
            }
            return addObjectHeaders(Response.ok(combined), blob.getMetadata(),
                    Optional.of(overwriteSizeAndETag(size, sizeAndEtag.getSecond())))
                    .build();
        } catch (IOException e) {
            throw propagate(e);
        }
    }

    private Response getDloObject(BlobStore blobStore, BlobMetadata meta, List<Pair<Long, Long>> ranges) {
        String manifest = meta.getUserMetadata().get(DYNAMIC_OBJECT_MANIFEST);
        Pair<String, String> param = validateCopyParam(manifest);
        String dloContainer = param.getFirst();
        String objectsPrefix = param.getSecond();
        /* jclouds doesn't really support prefixed listing, so faking it with marker */
        ListContainerOptions listOptions = new ListContainerOptions()
                .recursive()
                .afterMarker(objectsPrefix)
                .withDetails();
        Iterable<StorageMetadata> res = Utils.crawlBlobStore(blobStore, dloContainer, listOptions);

        List<ManifestEntry> segments = new ArrayList<>();
        for (StorageMetadata sm : res) {
            if (sm.getName().startsWith(objectsPrefix)) {
                ManifestEntry entry = new ManifestEntry();
                entry.container = dloContainer;
                entry.object = sm.getName();
                entry.size_bytes = sm.getSize();
                entry.etag = sm.getETag();
                segments.add(entry);
            } else {
                break;
            }
        }
        Pair<Long, String> sizeAndEtag = getManifestTotalSizeAndETag(segments);
        logger.debug("getting SLO object: {}", sizeAndEtag);
        segments.forEach(e -> logger.debug("sub-object: {}", e));

        InputStream combined = new ManifestObjectInputStream(blobStore, segments);
        long size = sizeAndEtag.getFirst();
        if (ranges != null) {
            combined = new HttpRangeInputStream(combined, sizeAndEtag.getFirst(), ranges);
            size = getTotalRangesLength(ranges, size);
            logger.debug("range request for {} bytes", size);
        }
        return addObjectHeaders(Response.ok(combined), meta,
                Optional.of(overwriteSizeAndETag(size, sizeAndEtag.getSecond())))
                .build();
    }

    private static String normalizePath(String pathName) {
        String objectName = Joiner.on("/").join(Iterators.forEnumeration(new StringTokenizer(pathName, "/")));
        if (pathName.endsWith("/")) {
            objectName += "/";
        }
        return objectName;
    }

    private Map<String, String> getUserMetadata(Request request) {
        return StreamSupport.stream(request.getHeaderNames().spliterator(), false)
                .filter(name -> name.toLowerCase().startsWith(META_HEADER_PREFIX.toLowerCase()))
                .filter(name -> {
                    if (name.equalsIgnoreCase(META_HEADER_PREFIX) || RESERVED_METADATA.contains(name)) {
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

    private String emulateCopyBlob(BlobStore blobStore, Response resp, BlobMetadata meta,
                                   String destContainer, String destObject, CopyOptions options) {
        Response.StatusType statusInfo = resp.getStatusInfo();
        if (statusInfo.equals(Response.Status.OK)) {
            ContentMetadata contentMetadata = meta.getContentMetadata();
            Map newMetadata = new HashMap<>();
            newMetadata.putAll(meta.getUserMetadata());
            newMetadata.putAll(options.getUserMetadata().or(ImmutableMap.of()));
            RESERVED_METADATA.forEach(s -> newMetadata.remove(s));
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

    private String serverCopyBlob(BlobStore blobStore, String container, String objectName, String destContainer,
                                  String destObject, CopyOptions options) {
        try {
            return blobStore.copyBlob(container, objectName, destContainer, destObject, options);
        } catch (RuntimeException e) {
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
                               @QueryParam("multipart-manifest") String multiPartManifest,
                               @HeaderParam(HttpHeaders.CONTENT_TYPE) String contentType,
                               @HeaderParam(HttpHeaders.CONTENT_ENCODING) String contentEncoding,
                               @HeaderParam(HttpHeaders.CONTENT_DISPOSITION) String contentDisposition,
                               @Context Request request) {
        if (objectName.length() > InfoResource.CONFIG.swift.max_object_name_length) {
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
        if (destObject.length() > InfoResource.CONFIG.swift.max_object_name_length) {
            return badRequest();
        }

        logger.info("copy {}/{} to {}/{}", container, objectName, destContainer, destObject);

        Map<String, String> additionalUserMeta = getUserMetadata(request);

        BlobStore blobStore = getBlobStore(authToken).get(container, objectName);
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
        String etag = null;
        if (!"get".equals(multiPartManifest)) {
            // copy is supposed to flatten the large object, which we have to emulate
            Response resp = null;
            if (userMetadata.containsKey(DYNAMIC_OBJECT_MANIFEST)) {
                resp = getDloObject(blobStore, meta, null);
            } else if (userMetadata.containsKey(STATIC_OBJECT_MANIFEST)) {
                resp = getSloObject(blobStore, blobStore.getBlob(container, objectName), null);
            }

            if (resp != null) {
                etag = emulateCopyBlob(blobStore, resp, meta, destContainer, destObject, options);
            }
        }

        if (etag == null) {
            etag = serverCopyBlob(blobStore, container, objectName, destContainer, destObject, options);
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
        if (objectName.length() > InfoResource.CONFIG.swift.max_object_name_length) {
            return badRequest();
        }

        BlobStore blobStore = getBlobStore(authToken).get(container, objectName);
        if (!blobStore.containerExists(container)) {
            return notFound();
        }
        Blob blob = blobStore.getBlob(container, objectName);
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

        blobStore.putBlob(container, blob);
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

    private ManifestEntry[] readSLOManifest(InputStream in) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ManifestEntry[] res = mapper.readValue(in, ManifestEntry[].class);
        if (res.length > 1000) {
            throw new ClientErrorException(Response.Status.BAD_REQUEST);
        }

        return res;
    }

    private Pair<Long, String> getManifestTotalSizeAndETag(Iterable<ManifestEntry> entries) {
        Hasher hash = Hashing.md5().newHasher();
        long segmentsTotalLength = 0;
        for (ManifestEntry entry : entries) {
            hash.putString(entry.etag, StandardCharsets.UTF_8);
            segmentsTotalLength += entry.size_bytes;
        }

        return new Pair<>(segmentsTotalLength, '"' + hash.hash().toString() + '"');
    }

    private void validateManifest(ManifestEntry[] res, BlobStore blobStore) {
        Arrays.stream(res).parallel()
                .forEach(s -> {
                    Response r = getObject(blobStore, s.container, s.object, GetOptions.NONE, null, false);
                    if (!r.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
                        throw new ClientErrorException(Response.Status.CONFLICT);
                    }
                    long size = Long.parseLong(r.getHeaderString(HttpHeaders.CONTENT_LENGTH));
                    String etag = r.getHeaderString(HttpHeaders.ETAG);
                    if (etag.startsWith("\"") && etag.endsWith("\"") && etag.length() > 2) {
                        etag = etag.substring(1, etag.length() - 1);
                    }
                    if (s.size_bytes != size || !s.etag.equals(etag)) {
                        logger.error("400 bad request: {}/{} {} {} != {} {}",
                                s.container, s.object, s.etag, s.size_bytes, etag, size);

                        throw new ClientErrorException(Response.Status.BAD_REQUEST);
                    }
                });
    }

    @PUT
    public Response putObject(@NotNull @PathParam("container") String container,
                              @NotNull @Encoded @PathParam("object") String objectName,
                              @NotNull @PathParam("account") String account,
                              @QueryParam("multipart-manifest") String multiPartManifest,
                              @QueryParam("signature") String signature,
                              @QueryParam("expires") String expires,
                              @HeaderParam(DYNAMIC_OBJECT_MANIFEST) String objectManifest,
                              @HeaderParam("X-Auth-Token") String authToken,
                              @HeaderParam(HttpHeaders.CONTENT_LENGTH) String contentLengthParam,
                              @HeaderParam("Transfer-Encoding") String transferEncoding,
                              @HeaderParam(HttpHeaders.CONTENT_TYPE) MediaType contentType,
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
        if (objectName.length() > InfoResource.CONFIG.swift.max_object_name_length) {
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
                    container + "/" + objectName, account, null, contentType.toString(), contentEncoding, contentDisposition,
                    request);
        }

        Map<String, String> metadata = getUserMetadata(request);
        InputStream copiedStream = null;

        BlobStore blobStore = getBlobStore(authToken).get(container, objectName);
        if ("put".equals(multiPartManifest)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (TeeInputStream tee = new TeeInputStream(request.getInputStream(), buffer, true)) {
                ManifestEntry[] manifest = readSLOManifest(tee);
                validateManifest(manifest, blobStore);
                Pair<Long, String> sizeAndEtag = getManifestTotalSizeAndETag(Arrays.asList(manifest));
                metadata.put(STATIC_OBJECT_MANIFEST, sizeAndEtag.getFirst() + " " + sizeAndEtag.getSecond());
                copiedStream = new ByteArrayInputStream(buffer.toByteArray());
            } catch (IOException e) {
                throw propagate(e);
            }
        } else if (objectManifest != null) {
            metadata.put(DYNAMIC_OBJECT_MANIFEST, objectManifest);
        }

        if (!blobStore.containerExists(container)) {
            return notFound();
        }

        HashCode contentMD5 = null;
        if (eTag != null) {
            try {
                contentMD5 = HashCode.fromBytes(BaseEncoding.base16().lowerCase().decode(eTag));
            } catch (IllegalArgumentException iae) {
                throw new ClientErrorException(422, iae); // Unprocessable Entity
            }
            if (contentMD5.bits() != Hashing.md5().bits()) {
                // Unprocessable Entity
                throw new ClientErrorException(contentMD5.bits() + " != " + Hashing.md5().bits(), 422);
            }
        }

        try (InputStream is = copiedStream != null ? copiedStream : request.getInputStream()) {
            BlobBuilder.PayloadBlobBuilder builder = blobStore.blobBuilder(objectName)
                    .userMetadata(metadata)
                    .payload(is);
            if (contentDisposition != null) {
                builder.contentDisposition(contentDisposition);
            }
            if (contentEncoding != null) {
                builder.contentEncoding(contentEncoding);
            }
            if (contentType != null) {
                builder.contentType(contentType.toString());
            }
            if (contentLengthParam != null) {
                builder.contentLength(contentLength);
            }
            if (contentMD5 != null) {
                builder.contentMD5(contentMD5);
            }
            try {
                String remoteETag;
                try {
                    remoteETag = blobStore.putBlob(container, builder.build());
                } catch (HttpResponseException e) {
                    HttpResponse response = e.getResponse();
                    int code = response.getStatusCode();

                    if (code == 400 && !"openstack-swift".equals(blobStore.getContext().unwrap().getId())) {
                        // swift expects 422 for md5 mismatch
                        throw new ClientErrorException(response.getStatusLine(), 422, e.getCause());
                    } else {
                        throw new ClientErrorException(response.getStatusLine(), code, e.getCause());
                    }
                }
                BlobMetadata meta = blobStore.blobMetadata(container, objectName);
                return Response.status(Response.Status.CREATED).header(HttpHeaders.ETAG, remoteETag)
                        .header(HttpHeaders.LAST_MODIFIED, meta.getLastModified())
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
                               @HeaderParam("X-Auth-Token") String authToken,
                               @QueryParam("multipart-manifest") String multiPartManifest) {
        if (objectName.length() > InfoResource.CONFIG.swift.max_object_name_length) {
            return badRequest();
        }

        BlobStore blobStore = getBlobStore(authToken).get(container, objectName);
        BlobMetadata meta = blobStore.blobMetadata(container, objectName);
        if (meta == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        boolean isMultiPartManifest = false;
        if (meta.getUserMetadata().containsKey(STATIC_OBJECT_MANIFEST)) {
            isMultiPartManifest = true;
            if (!"get".equals(multiPartManifest)) {
                String sloData = meta.getUserMetadata().get(STATIC_OBJECT_MANIFEST);
                String[] data = sloData.split(" ", 2);
                return addObjectHeaders(Response.ok(), meta,
                        Optional.of(overwriteSizeAndETag(Long.parseLong(data[0]), data[1])))
                        .build();
            }
        }

        // TODO: We should be sending a 204 (No Content), but cannot due to https://java.net/jira/browse/JERSEY-2822
        return addObjectHeaders(Response.ok(), meta,
                isMultiPartManifest ?
                        Optional.of(ImmutableMap.of(HttpHeaders.CONTENT_TYPE, MANIFEST_CONTENT_TYPE)) :
                        Optional.empty())
                .build();
    }

    @DELETE
    public Response deleteObject(@NotNull @PathParam("account") String account,
                                 @NotNull @PathParam("container") String container,
                                 @NotNull @Encoded @PathParam("object") String objectName,
                                 @QueryParam("multipart-manifest") String multipartManifest,
                                 @HeaderParam("X-Auth-Token") String authToken) throws IOException {
        if (objectName.length() > InfoResource.CONFIG.swift.max_object_name_length) {
            return badRequest();
        }

        BlobStore store = getBlobStore(authToken).get(container, objectName);
        if (!store.containerExists(container)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        BlobMetadata meta = store.blobMetadata(container, objectName);
        if (meta == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if ("delete".equals(multipartManifest) && meta.getUserMetadata().containsKey(STATIC_OBJECT_MANIFEST)) {
            Blob blob = store.getBlob(container, objectName);
            if (blob == null) {
                return notFound();
            }
            ManifestEntry[] entries = readSLOManifest(blob.getPayload().openStream());
            Arrays.stream(entries).parallel().forEach(e -> store.removeBlob(e.container, e.object));
        }
        store.removeBlob(container, objectName);

        return Response.noContent()
                .type(meta.getContentMetadata().getContentType())
                .build();
    }

    private Map<String, Object> overwriteSizeAndETag(long size, String etag) {
        return ImmutableMap.of(HttpHeaders.CONTENT_LENGTH, size,
                HttpHeaders.ETAG, etag);
    }

    private Response.ResponseBuilder addObjectHeaders(Response.ResponseBuilder responseBuilder, BlobMetadata metaData,
                                                      Optional<Map<String, Object>> overwrites) {
        Map<String, String> userMetadata = metaData.getUserMetadata();
        userMetadata.entrySet().stream()
                .filter(entry -> !RESERVED_METADATA.contains(entry.getKey()))
                .forEach(entry -> responseBuilder.header(META_HEADER_PREFIX + entry.getKey(), entry.getValue()));
        if (userMetadata.containsKey(DYNAMIC_OBJECT_MANIFEST)) {
            responseBuilder.header(DYNAMIC_OBJECT_MANIFEST, userMetadata.get(DYNAMIC_OBJECT_MANIFEST));
        }

        String contentType = Strings.isNullOrEmpty(metaData.getContentMetadata().getContentType()) ?
                MediaType.APPLICATION_OCTET_STREAM : metaData.getContentMetadata().getContentType();

        Map<String, Supplier<Object>> defaultHeaders = ImmutableMap.<String, Supplier<Object>>builder()
                .put(HttpHeaders.CONTENT_DISPOSITION, () -> metaData.getContentMetadata().getContentDisposition())
                .put(HttpHeaders.CONTENT_ENCODING, () -> metaData.getContentMetadata().getContentEncoding())
                .put(HttpHeaders.CONTENT_LENGTH, metaData::getSize)
                .put(HttpHeaders.LAST_MODIFIED, metaData::getLastModified)
                .put(HttpHeaders.ETAG, metaData::getETag)
                .put(STATIC_OBJECT_MANIFEST, () -> userMetadata.containsKey(STATIC_OBJECT_MANIFEST))
                .put(HttpHeaders.DATE, Date::new)
                .put(HttpHeaders.CONTENT_TYPE, () -> contentType)
                .build();

        overwrites.ifPresent(headers ->
                headers.forEach((k, v) -> responseBuilder.header(k, v)));
        defaultHeaders.forEach((k, v) -> {
            if (!overwrites.isPresent() || !overwrites.get().containsKey(k)) {
                responseBuilder.header(k, v.get());
            }
        });

        return responseBuilder;
    }

    private class HttpRangeInputStream extends InputStream {
        private final InputStream in;
        private final Iterator<Range> ranges;
        private final long size;
        private Range currentRange;
        private long offset = -1;

        HttpRangeInputStream(InputStream in, long size, List<Pair<Long, Long>> ranges) {
            this.in = requireNonNull(in);
            this.size = size;
            if (ranges != null) {
                this.ranges = ranges.stream().map(r -> new Range(r)).collect(Collectors.toList()).iterator();
                currentRange = this.ranges.next();
            } else {
                this.ranges = Iterators.emptyIterator();
                currentRange = new Range(new Pair(0, Long.MAX_VALUE));
            }
        }

        private void seek() throws IOException {
            long skipped = 0;
            logger.debug("seeking to {} from {}", currentRange, offset);
            if (currentRange.start > 0) {
                skipped = in.skip(currentRange.start - offset);
            } else if (currentRange.start == -1) {
                // suffix range
                skipped = in.skip(size - currentRange.available - offset);
            }
            offset += skipped;
        }

        @Override
        public int read() throws IOException {
            if (currentRange.available == 0) {
                if (ranges.hasNext()) {
                    currentRange = ranges.next();
                    seek();
                } else {
                    return -1;
                }
            }
            if (offset == -1) {
                offset = 0;
                seek();
            }

            int val = in.read();
            if (val != -1) {
                currentRange.available--;
                offset++;
            }
            return val;
        }

        private class Range {
            long start;
            long available;
            Range(Pair<Long, Long> rangeSpec) {
                if (rangeSpec.getFirst() == null) {
                    start = -1;
                    available = rangeSpec.getSecond();
                } else {
                    start = rangeSpec.getFirst();
                    if (rangeSpec.getSecond() == null) {
                        available = Long.MAX_VALUE;
                    } else {
                        available = rangeSpec.getSecond() - start + 1;
                    }
                }
            }

            @Override
            public String toString() {
                return Objects.toStringHelper(this)
                        .add("start", start)
                        .add("available", available)
                        .toString();
            }
        }
    }

    private class ManifestObjectInputStream extends InputStream {
        private final PeekingIterator<ManifestEntry> entries;
        private final BlobStore blobStore;
        private InputStream currentStream;
        private long availableBytes;

        ManifestObjectInputStream(BlobStore blobStore, Iterable<ManifestEntry> entries) {
            this.blobStore = requireNonNull(blobStore);
            this.entries = Iterators.peekingIterator(requireNonNull(entries).iterator());
        }

        @Override
        public long skip(long requestSkip) throws IOException {
            long remainingSkip = requestSkip;
            if (remainingSkip <= 0) {
                return 0;
            }

            if (availableBytes >= remainingSkip) {
                long skipped = currentStream.skip(remainingSkip);
                availableBytes -= skipped;
                remainingSkip -= skipped;
            } else {
                remainingSkip -= availableBytes;
                while (entries.hasNext()) {
                    ManifestEntry e = entries.peek();
                    if (remainingSkip > e.size_bytes) {
                        entries.next();
                        remainingSkip -= e.size_bytes;
                    } else {
                        break;
                    }
                }

                if (remainingSkip > 0) {
                    openNextStream();

                    if (currentStream != null) {
                        long skipped = currentStream.skip(remainingSkip);
                        availableBytes -= skipped;
                        remainingSkip -= skipped;
                    }
                }
            }

            return requestSkip - remainingSkip;
        }

        void openNextStream() throws IOException {
            if (currentStream != null) {
                currentStream.close();
                currentStream = null;
                availableBytes = 0;
            }
            if (!entries.hasNext()) {
                return;
            }

            ManifestEntry entry = entries.next();
            logger.info("opening {}/{}", entry.container, entry.object);
            Response resp = getObject(blobStore, entry.container, entry.object, GetOptions.NONE, null, false);
            if (!resp.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
                throw new ClientErrorException(Response.Status.CONFLICT);
            }
            availableBytes = Long.parseLong(resp.getHeaderString(HttpHeaders.CONTENT_LENGTH));
            currentStream = (InputStream) resp.getEntity();
            String etag = resp.getHeaderString(HttpHeaders.ETAG);
            if (etag.startsWith("\"") && etag.endsWith("\"") && etag.length() > 2) {
                etag = etag.substring(1, etag.length() - 1);
            }

            if (entry.size_bytes != availableBytes || !entry.etag.equals(etag)) {
                logger.error("409 conflict: {}/{} {} {} != {} {}",
                        entry.container, entry.object, entry.etag, entry.size_bytes,
                        etag, availableBytes);
                throw new ClientErrorException(Response.Status.CONFLICT);
            }
        }

        @Override
        public int read() throws IOException {
            if (currentStream == null || availableBytes == 0) {
                openNextStream();
            }

            do {
                if (currentStream == null || availableBytes == 0) {
                    return -1;
                }
                try {
                    int res = currentStream.read();
                    if (res == -1) {
                        openNextStream();
                    } else {
                        availableBytes--;
                        return res;
                    }
                } catch (EOFException e) {
                    openNextStream();
                } catch (IOException e) {
                    logger.error("error with {} bytes left", availableBytes);
                    throw e;
                }
            } while (true);
        }
    }

    private static class ManifestEntry {
        @JsonProperty String etag;
        @JsonProperty long size_bytes;
        String container;
        String object;

        @JsonProperty void setPath(String path) {
            Pair<String, String> param = validateCopyParam(path);
            container = param.getFirst();
            object = param.getSecond();
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("object", container + "/" + object)
                    .add("size", size_bytes)
                    .toString();
        }
    }
}
