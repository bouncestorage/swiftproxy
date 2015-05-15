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

package com.bouncestorage.swiftproxy.v2;

import static java.util.Objects.requireNonNull;

import static com.google.common.base.Throwables.propagate;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.bouncestorage.swiftproxy.BlobStoreResource;
import com.bouncestorage.swiftproxy.BounceResourceConfig;
import com.bouncestorage.swiftproxy.v1.InfoResource;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import org.glassfish.grizzly.http.server.Request;

@Path("/v2.0")
public final class Identity extends BlobStoreResource {
    @Path("tokens")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response auth(AuthV2Request payload,
                         @HeaderParam("Host") Optional<String> host,
                         @Context Request request) {
        String tenant = payload.auth.tenantName;
        String identity = payload.auth.passwordCredentials.username;
        if (Strings.isNullOrEmpty(tenant)) {
            tenant = identity;
        }
        String credential = payload.auth.passwordCredentials.password;
        String authToken = null;
        try {
            authToken = ((BounceResourceConfig) application).authenticate(tenant + ":" + identity, credential);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (authToken == null) {
            return notAuthorized();
        }

        String storageURL = host.orElseGet(() -> request.getLocalAddr() + ":" + request.getLocalPort());
        String scheme = request.getScheme();
        tenant = "AUTH_" + tenant;
        storageURL = scheme + "://" + storageURL + "/v1/" + tenant;

        AuthV2Response resp = new AuthV2Response();
        resp.access.token.id = authToken;
        resp.access.token.expires = Instant.now().plusSeconds(InfoResource.CONFIG.tempauth.token_life);
        resp.access.token.tenant = new IDAndName(tenant, tenant);
        resp.access.user = new AuthV2Response.Access.User(identity, identity);
        resp.access.user.roles.add(new IDAndName(identity, identity));
        if (!identity.equals(tenant)) {
            resp.access.user.roles.add(new IDAndName(tenant, tenant));
        }

        AuthV2Response.Access.ServiceCatalog.Endpoint endpoint = new AuthV2Response.Access.ServiceCatalog.Endpoint();
        try {
            endpoint.publicURL = new URL(storageURL);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw propagate(e);
        }
        endpoint.tenantId = tenant;
        resp.access.serviceCatalog[0].endpoints.add(endpoint);
        org.jclouds.Context c = getBlobStore(authToken).get().getContext().unwrap();
        resp.access.serviceCatalog[0].name += String.format(" (%s %s)",
                c.getId(), c.getProviderMetadata().getEndpoint());
        return Response.ok(resp).build();
    }

    static class AuthV2Request {
        @JsonProperty
        Auth auth = new Auth();
        static class Auth {
            @JsonProperty
            PasswordCredentials passwordCredentials = new PasswordCredentials();
            @JsonProperty
            String tenantName;
            static class PasswordCredentials {
                @JsonProperty
                String username;
                @JsonProperty
                String password;
            }
        }
    }

    static class IDAndName {
        @JsonProperty
        String id;
        @JsonProperty
        String name;

        IDAndName(String id, String name) {
            this.id = requireNonNull(id);
            this.name = requireNonNull(name);
        }
    }

    static class AuthV2Response {
        @JsonProperty
        Access access = new Access();
        static class Access {
            @JsonProperty
            Token token = new Token();
            @JsonProperty
            User user;
            @JsonProperty
            ServiceCatalog[] serviceCatalog = {new ServiceCatalog()};

            static class Token {
                @JsonProperty
                String id;
                @JsonSerialize(using = ToStringSerializer.class)
                Instant expires;
                @JsonProperty
                IDAndName tenant;
            }

            static class User extends IDAndName {
                @JsonProperty
                List<IDAndName> roles = Lists.newArrayList();

                User(String id, String name) {
                    super(id, name);
                }
            }

            static class ServiceCatalog {
                @JsonProperty
                List<Endpoint> endpoints = Lists.newArrayList();
                @JsonProperty
                String name = "Swift Object Storage";
                @JsonProperty
                String type = "object-store";

                static class Endpoint {
                    @JsonProperty
                    String region = "default";
                    @JsonProperty
                    URL publicURL;
                    @JsonProperty
                    String versionId = "1";
                    @JsonProperty
                    String tenantId;
                }
            }
        }
    }

}
