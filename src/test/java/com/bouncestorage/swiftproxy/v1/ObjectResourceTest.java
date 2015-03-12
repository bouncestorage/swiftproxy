/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy.v1;

import static org.assertj.core.api.Assertions.assertThat;

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
    private static final String CONTAINER = "test-container";
    private static final String BLOB_NAME = "blob";
    private final String path;

    private SwiftProxy proxy;
    private WebTarget target;

    public ObjectResourceTest() {
        String[] parts = {TestUtils.ACCOUNT_PATH, CONTAINER, BLOB_NAME};
        path = Joiner.on("/").join(parts);
    }

    @Before
    public void setup() throws Exception {
        proxy = TestUtils.setupAndStartProxy();
        Client c = ClientBuilder.newClient();
        target = c.target(proxy.getEndpoint());

        TestUtils.createContainer(target, CONTAINER);
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

        Response resp = target.path(path).request().get();
        assertThat(resp.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(resp.readEntity(String.class)).isEqualTo(data);
        assertThat(resp.getLength()).isEqualTo(data.length());
        assertThat(resp.getMediaType().toString()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    public void testMissingObject() throws Exception {
        Response resp = target.path(path).request().get();
        assertThat(resp.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testHead() throws Exception {
        String data = "foo";
        putObject(target.path(path), data.getBytes());

        Response resp = target.path(path).request().head();
        // TODO: this should be fixed once the Jersey issue is resolved and we return NO_CONTENT
        assertThat(resp.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(resp.getLength()).isEqualTo(data.length());
        assertThat(resp.getMediaType().toString()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    Response putObject(WebTarget putTarget, byte[] data) throws Exception {
        Response resp = target.path(path).request().put(Entity.entity(data, MediaType.APPLICATION_OCTET_STREAM));
        assertThat(resp.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
        assertThat(resp.getMediaType().toString()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        return resp;
    }
}
