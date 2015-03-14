/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.glassfish.jersey.spi.ExtendedExceptionMapper;

@Provider
public final class ExceptionLogger implements ExtendedExceptionMapper<Throwable> {
    @Override
    public Response toResponse(Throwable t) {
        return null;
    }

    @Override
    public boolean isMappable(Throwable t) {
        if (!(t instanceof NotFoundException)) {
            t.printStackTrace();
        }
        return false;
    }
}
