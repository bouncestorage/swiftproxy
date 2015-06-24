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

package com.bouncestorage.swiftproxy;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.NetworkListener;

public final class ContentLengthAddOn implements AddOn {
    @Override
    public void setup(NetworkListener networkListener, FilterChainBuilder builder) {

        // Get the index of HttpCodecFilter in the HttpServer filter chain
        final int httpCodecFilterIdx = builder.indexOfType(HttpServerFilter.class);

        if (httpCodecFilterIdx >= 0) {
            // Insert the WebSocketFilter right after HttpCodecFilter
            HttpServerFilter originalFilter = (HttpServerFilter) builder.get(httpCodecFilterIdx);
            builder.set(httpCodecFilterIdx, new ContentLengthFilter(originalFilter));
        }
    }

    private static class ContentLengthFilter extends HttpServerFilter {
        ContentLengthFilter(HttpServerFilter original) {
            setAllowPayloadForUndefinedHttpMethods(original.isAllowPayloadForUndefinedHttpMethods());
        }

        @Override
        protected void onInitialLineEncoded(HttpHeader header, FilterChainContext ctx) {
            super.onInitialLineEncoded(header, ctx);

            if (!header.isCommitted()) {
                final HttpResponsePacket response = (HttpResponsePacket) header;
                if (response.getStatus() == Response.Status.NO_CONTENT.getStatusCode()) {
                    response.getHeaders().setValue("Content-Length").setString("0");
                    response.getHeaders().setValue("Content-Type").setString(MediaType.TEXT_PLAIN);
                }
            }
        }
    }
}
