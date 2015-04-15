/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import static java.util.Objects.requireNonNull;

import java.util.Map;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant;
import javax.ws.rs.ext.RuntimeDelegate;

import com.google.common.base.Joiner;

public final class RuntimeDelegateImpl extends RuntimeDelegate {
    private static final MediaTypeProvider MEDIA_TYPE_PROVIDER = new MediaTypeProvider();
    private final RuntimeDelegate delegate;

    RuntimeDelegateImpl(RuntimeDelegate delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public UriBuilder createUriBuilder() {
        return delegate.createUriBuilder();
    }

    @Override
    public Response.ResponseBuilder createResponseBuilder() {
        return delegate.createResponseBuilder();
    }

    @Override
    public Variant.VariantListBuilder createVariantListBuilder() {
        return delegate.createVariantListBuilder();
    }

    @Override
    public <T> T createEndpoint(Application application, Class<T> endpointType) throws IllegalArgumentException, UnsupportedOperationException {
        return delegate.createEndpoint(application, endpointType);
    }

    @Override
    public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
        if (type.equals(MediaType.class)) {
            return (HeaderDelegate<T>) MEDIA_TYPE_PROVIDER;
        } else {
            return delegate.createHeaderDelegate(type);
        }
    }

    @Override
    public Link.Builder createLinkBuilder() {
        return delegate.createLinkBuilder();
    }

    private static final class InvalidMediaType extends MediaType {
        private final String type;

        InvalidMediaType(String type) {
            this.type = requireNonNull(type);
        }

        public String toString() {
            StringBuilder res = new StringBuilder(type);
            Map<String, String> params = getParameters();
            if (params != null && !params.isEmpty()) {
                res.append("; ");
                res.append(Joiner.on("; ").withKeyValueSeparator("=").join(params));
            }
            return res.toString();
        }
    }

    private static final class MediaTypeProvider extends org.glassfish.jersey.message.internal.MediaTypeProvider {
        @Override
        public MediaType fromString(String header) {
            try {
                return super.fromString(header);
            } catch (IllegalArgumentException e) {
                return new InvalidMediaType(header);
            }
        }

        @Override
        public String toString(MediaType type) {
            StringBuilder res = new StringBuilder();
            res.append(type.getType()).append('/').append(type.getSubtype());
            Map<String, String> params = type.getParameters();
            if (params != null && !params.isEmpty()) {
                res.append("; ");
                res.append(Joiner.on("; ").withKeyValueSeparator("=").join(params));
            }
            return res.toString();
        }
    }
}
