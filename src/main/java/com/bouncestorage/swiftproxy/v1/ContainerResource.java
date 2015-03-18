/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy.v1;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import com.bouncestorage.swiftproxy.BlobStoreResource;
import com.bouncestorage.swiftproxy.BounceResourceConfig;
import com.bouncestorage.swiftproxy.Utils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.domain.StorageType;
import org.jclouds.blobstore.options.ListContainerOptions;

@Path("/v1/{account}/{container}")
public final class ContainerResource extends BlobStoreResource {

    private void createContainer(String container) {
        if (container.length() > 256) {
            throw new BadRequestException("container name too long");
        }

        getBlobStore().createContainerInLocation(null, container);
    }

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
        createContainer(container);
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
            status = Response.Status.ACCEPTED;
        } else {
            createContainer(container);
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

    private String contentType(StorageMetadata meta) {
        return metaGetName(meta).endsWith("/") ? "application/directory" : MediaType.APPLICATION_OCTET_STREAM;
        //return "application/directory";
    }

    private boolean delimFilter(String key, String delimiter) {
        if (delimiter == null) {
            return true;
        }
        key = key.substring(delimiter.length());
        int idx = key.indexOf(delimiter);
        return idx == -1 || idx == key.length() - delimiter.length();
    }

    private boolean logFilter(String tag, Object obj) {
        logger.info("{}: {}", tag, obj);
        return true;
    }

    private static String metaGetName(StorageMetadata meta) {
        return meta.getType() == StorageType.RELATIVE_PATH ?
                (meta.getName().endsWith("/") ? meta.getName() : meta.getName() + "/") :
                meta.getName();
    }

    @GET
    public Response listContainer(@NotNull @PathParam("container") String container,
                                  @HeaderParam("X-Auth-Token") String authToken,
                                  @QueryParam("limit") Integer limit,
                                  @QueryParam("marker") String marker,
                                  @QueryParam("end_marker") String endMarker,
                                  @QueryParam("format") @DefaultValue("plain") String format,
                                  @QueryParam("prefix") String prefix,
                                  @QueryParam("delimiter") String delimiter,
                                  @QueryParam("path") String path,
                                  @HeaderParam("X-Newest") @DefaultValue("false") boolean newest,
                                  @HeaderParam("Accept") String accept) {
        BlobStore store = getBlobStore();
        if (!store.containerExists(container)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        ListContainerOptions options = new ListContainerOptions();
        if (marker != null) {
            options.afterMarker(marker);
        }

        if (path != null) {
            delimiter = "/";
            prefix = path.endsWith("/") ? path : path + "/";
        }
        if (prefix != null && ("/".equals(delimiter) || prefix.endsWith("/"))) {
            if (!"/".equals(prefix)) {
                options.inDirectory(prefix);
                prefix = null;
            } else {
                options.inDirectory("");
            }
        }

        if (options.getDir() == null) {
            options.recursive();
        }
/*
        final String fake_prefix;
        if (prefix != null) {
            fake_prefix = prefix;
        } else {
            fake_prefix = null;
        }
        final String delim_filter = delimiter;
        logger.info("list: {} marker={} fake_prefix={}", options, options.getMarker(), fake_prefix);
*/
        List<ObjectEntry> entries = StreamSupport.stream(
                Utils.crawlBlobStore(store, container, options).spliterator(), false)
                .filter(meta -> logFilter("meta", meta))
                //.filter(meta -> (fake_prefix == null || meta.getName().startsWith(fake_prefix)))
                //.filter(meta -> delimFilter(meta.getName(), delim_filter))
                .filter(meta -> endMarker == null || meta.getName().compareTo(endMarker) < 0)
                .limit(limit == null ? Integer.MAX_VALUE : limit)
                .map(meta -> new ObjectEntry(metaGetName(meta), meta.getETag(),
                        meta.getSize() == null ? 0 : meta.getSize(),
                        contentType(meta), meta.getLastModified()))
                .collect(Collectors.toList());

        MediaType formatType = BounceResourceConfig.getMediaType(format);

        ContainerRoot root = new ContainerRoot();
        root.name = container;
        root.object = entries;
        return output(root, entries, formatType)
                .header("X-Container-Object-Count", entries.size())
                .build();

    }

    @XmlRootElement(name = "container")
    @XmlType
    static class ContainerRoot {
        @XmlElement
        List<ObjectEntry> object;
        @XmlAttribute
        private String name;
    }

    @XmlRootElement(name = "object")
    @XmlType
    static class ObjectEntry {
        @XmlElement
        String name;
        @XmlElement
        String hash;
        @XmlElement
        long bytes;
        @XmlElement
        String content_type;
        @XmlElement
        Date last_modified;

        // dummy
        public ObjectEntry() {
        }

        @JsonCreator
        public ObjectEntry(@JsonProperty("name") String name,
                           @JsonProperty("hash") String hash,
                           @JsonProperty("bytes") long bytes,
                           @JsonProperty("content_type") String content_type,
                           @JsonProperty("last_modified") Date last_modified) {
            this.name = requireNonNull(name);
            this.hash = hash == null ? "" : hash;
            this.bytes = bytes;
            this.content_type = requireNonNull(content_type);
            this.last_modified = last_modified == null ? Date.from(Instant.EPOCH) : last_modified;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
