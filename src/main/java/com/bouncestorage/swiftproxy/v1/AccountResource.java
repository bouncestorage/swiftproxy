/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy.v1;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import com.bouncestorage.swiftproxy.BlobStoreResource;
import com.bouncestorage.swiftproxy.BounceResourceConfig;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@Singleton
@Path("/v1/{account}")
public final class AccountResource extends BlobStoreResource {

    @GET
    public Response getAccount(@NotNull @PathParam("account") String account,
                               @QueryParam("limit") Optional<Integer> limit,
                               @QueryParam("marker") Optional<String> marker,
                               @QueryParam("end_marker") Optional<String> endMarker,
                               @QueryParam("format") Optional<String> format,
                               @QueryParam("prefix") Optional<String> prefix,
                               @QueryParam("delimiter") Optional<String> delimiter,
                               @HeaderParam("X-Auth-Token") String authToken,
                               @HeaderParam("X-Newest") @DefaultValue("false") boolean newest,
                               @HeaderParam("Accept") Optional<String> accept) {
        delimiter.ifPresent(x -> logger.info("delimiter not supported yet"));

        List<ContainerEntry> entries = getBlobStore(getIdentity(authToken), null, null).list()
                .stream()
                .map(meta -> meta.getName())
                .filter(name -> marker.map(m -> name.compareTo(m) > 0).orElse(true))
                .filter(name -> endMarker.map(m -> name.compareTo(m) < 0).orElse(true))
                .filter(name -> prefix.map(p -> name.startsWith(p)).orElse(true))
                .limit(limit.orElse(Integer.MAX_VALUE))
                .map(name -> new ContainerEntry(name))
                .collect(Collectors.toList());

        MediaType formatType = BounceResourceConfig.getMediaType(format.orElse(accept.orElse(null)));
        if (formatType == null) {
            formatType = MediaType.TEXT_PLAIN_TYPE;
        }

        Account root = new Account();
        root.name = account;
        root.container = entries;
        return output(root, entries, formatType)
                .header("X-Account-Container-Count", entries.size())
                .build();
    }

    @HEAD
    public Response headAccount(@NotNull @PathParam("account") String account,
                                @HeaderParam("X-Auth-Token") String authToken,
                                @HeaderParam("X-Newest") boolean newest) {
        return Response.noContent().build();
    }

    @XmlRootElement(name = "account")
    @XmlType
    static class Account {
        @XmlElement
        List<ContainerEntry> container;
        @XmlAttribute
        private String name;
    }

    @XmlRootElement(name = "container")
    @XmlType
    static class ContainerEntry {
        @XmlElement
        private String name;
        @XmlElement
        private long count;
        @XmlElement
        private long bytes;

        // for jackson XML
        public ContainerEntry() {

        }

        @JsonCreator
        public ContainerEntry(@JsonProperty("name") String name) {
            this.name = requireNonNull(name);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ContainerEntry &&
                    name.equals(((ContainerEntry) other).name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, count, bytes);
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
