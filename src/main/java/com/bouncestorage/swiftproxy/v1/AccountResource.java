package com.bouncestorage.swiftproxy.v1;

import com.bouncestorage.swiftproxy.BlobStoreResource;
import com.bouncestorage.swiftproxy.BounceResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
@Path("/v1/{account}")
public class AccountResource extends BlobStoreResource {
    private Logger logger = LoggerFactory.getLogger(getClass());

    @GET
    public Response getAccount(@NotNull @PathParam("account") String account,
                               @QueryParam("limit") Optional<Integer> limit,
                               @QueryParam("marker") Optional<String> marker,
                               @QueryParam("end_marker") Optional<String> endMarker,
                               @QueryParam("format") @DefaultValue("plain") String format,
                               @QueryParam("prefix") Optional<String> prefix,
                               @QueryParam("delimiter") Optional<String> delimiter,
                               @HeaderParam("X-Auth-Token") String authToken,
                               @HeaderParam("X-Newest") @DefaultValue("false") boolean newest,
                               @HeaderParam("Accept") Optional<String> accept) {
        delimiter.ifPresent(x -> logger.info("delimiter not supported yet"));
        accept.ifPresent(x -> logger.info("Accept not supported yet"));

        List<ContainerEntry> entries = getBlobStore().list()
                .stream()
                .map(meta -> meta.getName())
                .filter(name -> marker.map(m -> name.compareTo(m) > 0).orElse(true))
                .filter(name -> endMarker.map(m -> name.compareTo(m) < 0).orElse(true))
                .filter(name -> prefix.map(p -> name.startsWith(p)).orElse(true))
                .limit(limit.orElse(Integer.MAX_VALUE))
                .map(name -> new ContainerEntry(name))
                .collect(Collectors.toList());

        return Response.ok(entries, BounceResourceConfig.getMediaType(format))
                .header("X-Account-Container-Count", entries.size())
                .build();
    }

    static class ContainerEntry {
        public String name;
        public long count;
        public long bytes;

        // dummy constructor for jackson
        public ContainerEntry() {
        }

        public ContainerEntry(String name) {
            this.name = checkNotNull(name);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ContainerEntry &&
                    name.equals(((ContainerEntry) other).name);
        }
    }
}
