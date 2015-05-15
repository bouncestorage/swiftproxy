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

import java.io.InputStream;
import java.net.URI;
import java.util.Properties;

import com.bouncestorage.swiftproxy.SwiftProxy;
import com.bouncestorage.swiftproxy.TestUtils;
import com.google.common.io.Resources;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class AuthResourceTest {
    private SwiftProxy proxy;
    private URI endpoint;
    private Properties properties;

    @Before
    public void setup() throws Exception {
        properties = new Properties();
        try (InputStream is = Resources.asByteSource(Resources.getResource(
                "swiftproxy.conf")).openStream()) {
            properties.load(is);
        }
        proxy = SwiftProxy.Builder.builder()
                .overrides(properties)
                .build();
        proxy.start();
        proxy = TestUtils.setupAndStartProxy();
        endpoint = proxy.getEndpoint();
    }

    @After
    public void tearDown() throws Exception {
        if (proxy != null) {
            proxy.stop();
        }
    }

    @Test
    public void testV1() throws Exception {
        properties.setProperty("jclouds.keystone.credential-type", "tempAuthCredentials");
        BlobStoreContext context = ContextBuilder.newBuilder("openstack-swift")
                .endpoint(endpoint.toString() + "/auth/v1.0")
                .overrides(properties)
                .build(BlobStoreContext.class);
        context.getBlobStore().list();
    }

    @Test
    public void testV2() throws Exception {
        BlobStoreContext context = ContextBuilder.newBuilder("openstack-swift")
                .endpoint(endpoint.toString() + "/v2.0")
                .overrides(properties)
                .build(BlobStoreContext.class);
        context.getBlobStore().list();
    }
}
