/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import com.bouncestorage.swiftproxy.SwiftProxy;
import com.bouncestorage.swiftproxy.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class AccountResourceTest {
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
    public void testEmptyContainers() throws Exception {
        List<AccountResource.ContainerEntry> entries = listContainers();
        assertThat(entries).isEmpty();
    }

    @Test
    public void testOneContainer() throws Exception {
        String authToken = TestUtils.createContainer(target, CONTAINER);

        List<AccountResource.ContainerEntry> entries = listContainers(Optional.of(authToken));
        assertThat(entries).containsOnly(new AccountResource.ContainerEntry(CONTAINER));
    }

    @Test
    public void testHead() throws Exception {
        Response response = target.path(TestUtils.ACCOUNT_PATH).queryParam("format", "json").request().head();
        assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    }

    List<AccountResource.ContainerEntry> listContainers() throws Exception {
        return listContainers(Optional.empty());
    }

    List<AccountResource.ContainerEntry> listContainers(Optional<String> authToken) throws Exception {
        return target.path(TestUtils.ACCOUNT_PATH)
                .queryParam("format", "json")
                .request()
                .header("x-auth-token", authToken.orElseGet(() -> TestUtils.getAuthToken(target)))
                .get(new GenericType<List<AccountResource.ContainerEntry>>() {
                });
    }
}
