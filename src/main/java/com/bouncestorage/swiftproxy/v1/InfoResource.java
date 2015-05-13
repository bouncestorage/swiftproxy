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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.annotation.JsonProperty;

@Path("/info")
public final class InfoResource {
    public static final ServerConfiguration CONFIG = new ServerConfiguration();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ServerConfiguration getInfo() {
        return CONFIG;
    }

    //CHECKSTYLE:OFF
    public static final class ServerConfiguration {
        @JsonProperty public final SwiftConfiguration swift = new SwiftConfiguration();
        @JsonProperty public final SLOConfiguration slo = new SLOConfiguration();
        @JsonProperty public final TempAuthConfiguration tempauth = new TempAuthConfiguration();

        static final class SwiftConfiguration {
            @JsonProperty final int account_listing_limit = 10000;
            @JsonProperty final boolean allow_account_management = false;
            @JsonProperty final int container_listing_limit = 10000;
            @JsonProperty final int max_account_name_length = 256;
            @JsonProperty final int max_container_name_length = 256;
            @JsonProperty final long max_file_size = 5368709122L;
            @JsonProperty final int max_header_size = 8192;
            @JsonProperty final int max_meta_name_length = 128;
            @JsonProperty final int max_meta_value_length = 256;
            @JsonProperty final int max_meta_count = 90;
            @JsonProperty final int max_meta_overall_size = 2048;
            @JsonProperty final int max_object_name_length = 1024;
            @JsonProperty final boolean strict_cors_mode = true;
        }

        static final class SLOConfiguration {
            @JsonProperty final int max_manifest_segments = 1000;
        }

        public static final class TempAuthConfiguration {
            @JsonProperty public final int token_life = 85400;
        }
    }
    //CHECKSTYLE:ON
}
