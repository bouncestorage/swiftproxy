/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.glassfish.jersey.spi.ExtendedExceptionMapper;
import org.jclouds.http.HttpResponseException;

@Provider
public final class HttpResponseExceptionMapper implements ExtendedExceptionMapper<HttpResponseException> {
    @Override
    public Response toResponse(HttpResponseException exception) {
        return Response.status(exception.getResponse().getStatusCode())
                .build();
    }

    @Override
    public boolean isMappable(HttpResponseException exception) {
        return exception.getResponse() != null;
    }
}
