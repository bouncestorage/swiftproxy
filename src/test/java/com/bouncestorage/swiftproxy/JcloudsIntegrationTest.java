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

package com.bouncestorage.swiftproxy;

import static com.google.common.base.Throwables.propagate;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.Uninterruptibles;

import org.jclouds.Constants;
import org.jclouds.openstack.keystone.v2_0.config.KeystoneProperties;
import org.jclouds.openstack.swift.v1.blobstore.integration.SwiftBlobIntegrationLiveTest;
import org.junit.AfterClass;
import org.testng.SkipException;

public final class JcloudsIntegrationTest extends SwiftBlobIntegrationLiveTest {
    protected static final int AWAIT_CONSISTENCY_TIMEOUT_SECONDS = Integer.parseInt(System.getProperty(
            "test.blobstore.await-consistency-timeout-seconds", "0"));
    private SwiftProxy proxy;

    public JcloudsIntegrationTest() throws Exception {
    }

    @AfterClass
    public void tearDown() {
        proxy.stop();
    }

    @Override
    protected void awaitConsistency() {
        Uninterruptibles.sleepUninterruptibly(AWAIT_CONSISTENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    protected Properties setupProperties() {
        try {
            proxy = TestUtils.setupAndStartProxy();
        } catch (Exception e) {
            throw propagate(e);
        }
        Properties props = super.setupProperties();
        identity = "test:tester";
        credential = "testing";
        endpoint = proxy.getEndpoint().toString() + "/auth/v1.0";
        props.setProperty(KeystoneProperties.CREDENTIAL_TYPE, "tempAuthCredentials");
        props.setProperty(Constants.PROPERTY_IDENTITY, identity);
        props.setProperty(Constants.PROPERTY_CREDENTIAL, credential);
        props.setProperty(Constants.PROPERTY_ENDPOINT, endpoint);
        return props;
    }

    @Override
    public void testPutBlobAccess() throws Exception {
        throw new SkipException("unsupported in swift");
    }

    @Override
    public void testPutBlobAccessMultipart() throws Exception {
        throw new SkipException("unsupported in swift");
    }
}
