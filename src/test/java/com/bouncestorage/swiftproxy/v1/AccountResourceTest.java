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

import java.util.List;
import java.util.Optional;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.bouncestorage.swiftproxy.SwiftProxy;
import com.bouncestorage.swiftproxy.TestUtils;
import com.google.common.base.Joiner;

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

    @Test
    public void testBulkDelete() throws Exception {
        String authToken = TestUtils.createContainer(target, CONTAINER);

        String[] removeObjects = {"/test/bar", "/test"};
        Response response = target.path(TestUtils.ACCOUNT_PATH)
                // swift actually sends ?bulk-delete and this sends ?bulk-delete=, but
                // that's the closest we can get
                .queryParam("bulk-delete", "")
                .request()
                .header("X-Auth-Token", authToken)
                .post(Entity.entity(Joiner.on("\n").join(removeObjects), MediaType.TEXT_PLAIN));
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        AccountResource.BulkDeleteResult result = response.readEntity(AccountResource.BulkDeleteResult.class);
        assertThat(result.numberDeleted).isEqualTo(1);
        assertThat(result.numberNotFound).isEqualTo(1);
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
