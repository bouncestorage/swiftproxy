/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import static org.assertj.core.api.Assertions.assertThat;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

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

        String container = "container";
        String path = Joiner.on("/").join(TestUtils.ACCOUNT_PATH, container);
        target.path(path).request().header("X-auth-token", "foo" + BlobStoreLocator.TOKEN_SEPARATOR + "token")
                .post(null);
        target.path(path).request().header("X-auth-token", "bar" + BlobStoreLocator.TOKEN_SEPARATOR + "token")
                .post(null);
        assertThat(foo.containerExists(container)).isTrue();
        assertThat(bar.containerExists(container)).isTrue();
    }
}
