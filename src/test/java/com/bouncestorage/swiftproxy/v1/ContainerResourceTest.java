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

import java.util.Optional;
import java.util.Random;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.bouncestorage.swiftproxy.SwiftProxy;
import com.bouncestorage.swiftproxy.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class ContainerResourceTest {
    private static final String CONTAINER = "swiftproxy-test-container-" + new Random().nextInt(Integer.MAX_VALUE);
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

    @Test
    public void testGetContainer() throws Exception {
        Response resp = getContainer(CONTAINER, Optional.empty());
        assertThat(resp.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());

        String authToken = TestUtils.createContainer(target, CONTAINER);

        String data = "foo";
        resp = target.path(TestUtils.ACCOUNT_PATH + "/" + CONTAINER + "/blob").request()
                .header("x-auth-token", authToken)
                .put(Entity.entity(data.getBytes(), MediaType.APPLICATION_OCTET_STREAM));
        assertThat(resp.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());

        resp = getContainer(CONTAINER, Optional.of(authToken));
        assertThat(resp.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(resp.readEntity(String.class)).isEqualTo("blob\n");
    }

    Response headContainer(String container, Optional<String> authToken) {
        return target.path(TestUtils.ACCOUNT_PATH + "/" + container).request()
                .header("x-auth-token", authToken.orElseGet(() -> TestUtils.getAuthToken(target)))
                .head();
    }

    Response getContainer(String container, Optional<String> authToken) {
        return target.path(TestUtils.ACCOUNT_PATH + "/" + container).request()
                .header("x-auth-token", authToken.orElseGet(() -> TestUtils.getAuthToken(target)))
                .get();
    }
}
