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

import static org.assertj.core.api.Assertions.assertThat;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class BlobStoreLocatorTest {
    private SwiftProxy proxy;
    private WebTarget target;
    private ImmutableMap<String, BlobStore> blobStoreMap;

    @Before
    public void setUp() throws Exception {
        proxy = TestUtils.setupAndStartProxy();
        // create the client
        Client c = ClientBuilder.newClient();

        // uncomment the following line if you want to enable
        // support for JSON in the client (you also have to uncomment
        // dependency on jersey-media-json module in pom.xml and Main.startServer())
        // --
        // c.configuration().enable(new org.glassfish.jersey.media.json.JsonJaxbFeature());

        target = c.target(proxy.getEndpoint());
    }

    @After
    public void tearDown() throws Exception {
        proxy.stop();
        if (blobStoreMap != null) {
            blobStoreMap.forEach((key, value) -> {
                if (value != null) {
                    value.getContext().close();
                }
            });
        }
    }

    @Test
    public void testBlobStoreLocator() throws Exception {
        ContextBuilder context = ContextBuilder.newBuilder("transient");
        BlobStore foo = context.build(BlobStoreContext.class).getBlobStore();
        ContextBuilder otherContext = ContextBuilder.newBuilder("transient");
        BlobStore bar = otherContext.build(BlobStoreContext.class).getBlobStore();
        blobStoreMap = ImmutableMap.of("foo", foo, "bar", bar);
        proxy.setBlobStoreLocator((identity, container, blob) -> {
            if (blobStoreMap.containsKey(identity)) {
                return Maps.immutableEntry(identity, blobStoreMap.get(identity));
            }
            return null;
        });

        String path = "/auth/v1.0";
        Response resp = target.path(path).request().header("X-auth-user", "foo").header("X-auth-key", "foo")
                .get();
        String storageURL = resp.getHeaderString("x-storage-url");
        String authToken = resp.getHeaderString("x-auth-token");
        assertThat(storageURL).isNotNull();
        assertThat(authToken).isNotNull();
        String container = "container";
        path = Joiner.on("/").join(TestUtils.ACCOUNT_PATH, container);
        target.path(path).request().header("X-auth-token", authToken)
                .post(null);
        assertThat(foo.containerExists(container)).isTrue();
        assertThat(bar.containerExists(container)).isFalse();
    }
}
