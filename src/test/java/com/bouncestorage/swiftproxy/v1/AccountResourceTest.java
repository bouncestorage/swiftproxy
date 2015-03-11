/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
    private static final String ACCOUNT_PATH = "/v1/testAccount";
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
        createContainer(CONTAINER);

        List<AccountResource.ContainerEntry> entries = listContainers();
        assertThat(entries).containsOnly(new AccountResource.ContainerEntry(CONTAINER));
    }

    @Test
    public void testDeleteContainer() throws Exception {
        Response resp = deleteContainer(CONTAINER);
        assertThat(resp.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

        createContainer(CONTAINER);

        resp = deleteContainer(CONTAINER);
        assertThat(resp.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    }

    Response deleteContainer(String container) {
        return target.path(ACCOUNT_PATH + "/" + container)
                .request().delete();
    }

    void createContainer(String container) {
        Response resp = target.path(ACCOUNT_PATH + "/" + container)
                .request().post(null);
        assertThat(resp.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    }

    List<AccountResource.ContainerEntry> listContainers() {
        return target.path(ACCOUNT_PATH)
                .queryParam("format", "json")
                .request().get(new GenericType<List<AccountResource.ContainerEntry>>() {
                });
    }
}
