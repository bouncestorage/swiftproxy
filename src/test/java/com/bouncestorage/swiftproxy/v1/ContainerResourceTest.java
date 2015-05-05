/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import com.bouncestorage.swiftproxy.SwiftProxy;
import com.bouncestorage.swiftproxy.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class ContainerResourceTest {
    private static final String CONTAINER = "testContainer";
    private SwiftProxy proxy;
    private WebTarget target;

    @Before
    public void setup() throws Exception {
        proxy = TestUtils.setupAndStartProxy();
        Client c = ClientBuilder.newClient();
        target = c.target(proxy.getEndpoint());
    }

    @After
    public void tearDown() throws Exception {
        if (proxy != null) {
            proxy.stop();
        }
    }

    @Test
    public void testDeleteContainer() throws Exception {
        Response resp = TestUtils.deleteContainer(target, CONTAINER);
        assertThat(resp.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

        String authToken = TestUtils.createContainer(target, CONTAINER);

        resp = TestUtils.deleteContainer(target, CONTAINER, Optional.of(authToken));
        assertThat(resp.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    }

    @Test
    public void testHeadContainer() throws Exception {
        Response resp = headContainer(CONTAINER, Optional.empty());
        assertThat(resp.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

        String authToken = TestUtils.createContainer(target, CONTAINER);

        resp = headContainer(CONTAINER, Optional.of(authToken));
        assertThat(resp.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    }

    Response headContainer(String container, Optional<String> authToken) {
        return target.path(TestUtils.ACCOUNT_PATH + "/" + container).request()
                .header("x-auth-token", authToken.orElseGet(() -> TestUtils.getAuthToken(target)))
                .head();
    }
}
