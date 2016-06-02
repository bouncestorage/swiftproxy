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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.bouncestorage.swiftproxy.SwiftProxy;
import com.bouncestorage.swiftproxy.TestUtils;
import com.google.common.base.Joiner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class ObjectResourceTest {
    private static final String CONTAINER = "swiftproxy-test-container-" + new Random().nextInt(Integer.MAX_VALUE);
    private static final String BLOB_NAME = "blob";
    private final String path;

    private SwiftProxy proxy;
    private WebTarget target;
    private String authToken;

    public ObjectResourceTest() {
        String[] parts = {TestUtils.ACCOUNT_PATH, CONTAINER, BLOB_NAME};
        path = Joiner.on("/").join(parts);
    }

    @Before
    public void setup() throws Exception {
        proxy = TestUtils.setupAndStartProxy();
        Client c = ClientBuilder.newClient();
        target = c.target(proxy.getEndpoint());

        authToken = TestUtils.createContainer(target, CONTAINER);
    }

    @After
    public void tearDown() throws Exception {
        if (proxy != null) {
            proxy.stop();
        }
    }

    @Test
    public void testPut() throws Exception {
        String data = "foo";
        putObject(target.path(path), data.getBytes());

        Response resp = target.path(path).request().header("x-auth-token", authToken).get();
        assertThat(resp.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(resp.readEntity(String.class)).isEqualTo(data);
        assertThat(resp.getLength()).isEqualTo(data.length());
        assertThat(resp.getMediaType().toString()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    public void testMissingObject() throws Exception {
        Response resp = target.path(path).request().header("x-auth-token", authToken).get();
        assertThat(resp.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testHead() throws Exception {
        String data = "foo";
        putObject(target.path(path), data.getBytes());

        Response resp = target.path(path).request().header("x-auth-token", authToken).head();
        // TODO: this should be fixed once the Jersey issue is resolved and we return NO_CONTENT
        assertThat(resp.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(resp.getLength()).isEqualTo(data.length());
        assertThat(resp.getMediaType().toString()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    Response putObject(WebTarget putTarget, byte[] data) throws Exception {
        Response resp = target.path(path).request()
                .header("x-auth-token", authToken)
                .put(Entity.entity(data, MediaType.APPLICATION_OCTET_STREAM));
        assertThat(resp.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
        assertThat(resp.getMediaType().toString()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        return resp;
    }
}
