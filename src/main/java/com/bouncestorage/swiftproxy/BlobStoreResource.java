/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import static com.google.common.base.Throwables.propagate;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.util.Collection;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyWriter;

import org.glassfish.jersey.message.MessageBodyWorkers;
import org.jclouds.blobstore.BlobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BlobStoreResource {
    protected Logger logger = LoggerFactory.getLogger(getClass());
    @Context
    protected Application application;
    @Context
    private MessageBodyWorkers workers;

    protected final BlobStore getBlobStore() {
        return ((BounceResourceConfig) application).getBlobStore();
    }

    protected static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity("<html><h1>Not Found</h1><p>The resource could not be found.</p></html>")
                .build();
    }

    protected static Response badRequest() {
        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    private void debugWrite(Object root, MediaType format) {

        MessageBodyWriter messageBodyWriter =
                workers.getMessageBodyWriter(root.getClass(), root.getClass(),
                        new Annotation[]{}, format);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            // use the MBW to serialize myBean into baos
            messageBodyWriter.writeTo(root,
                    root.getClass(), root.getClass(), new Annotation[]{},
                    format, new MultivaluedHashMap<String, Object>(),
                    baos);
        } catch (Throwable e) {
            logger.error(String.format("could not serialize %s to format %s", root, format), e);
            throw propagate(e);
        }

        logger.info("{}", baos);
    }

    protected final Response.ResponseBuilder output(Object root, Object value, MediaType
            format) {
        if (value instanceof  Collection) {
            Collection entries = (Collection) value;
            if (format == MediaType.TEXT_PLAIN_TYPE && entries.isEmpty()) {
                return Response.noContent();
            }
            if (format == MediaType.APPLICATION_XML_TYPE) {
                debugWrite(root, format);
                return Response.ok(root, format);
            }
            if (format == MediaType.APPLICATION_JSON_TYPE) {
                return Response.ok(entries, format);
            }
        }

        debugWrite(value, format);
        return Response.ok(value, format);
    }
}
