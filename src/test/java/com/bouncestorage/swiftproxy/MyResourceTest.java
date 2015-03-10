package com.bouncestorage.swiftproxy;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import com.google.common.io.Resources;
import org.glassfish.grizzly.http.server.HttpServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class MyResourceTest {

    private SwiftProxy proxy;
    private WebTarget target;

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
    }

    /**
     * Test to see that the message "Got it!" is sent in the response.
     */
    @Test
    public void testGetIt() {
        String responseMsg = target.path("myresource").request().get(String.class);
        assertEquals("Got it!", responseMsg);
    }

    @Test
    public void testGetIt2() {
        String responseMsg = target.path("myresource/2/3").request().get(String.class);
        assertEquals("Got it!", responseMsg);
    }
}
