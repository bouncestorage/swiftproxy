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

import java.util.Iterator;
import java.util.Objects;
import java.util.Properties;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.inject.Module;

import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.domain.StorageType;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.jclouds.javax.annotation.Nullable;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;

public final class Utils {
    private Utils() {
        throw new AssertionError("intentionally unimplemented");
    }

    public static Iterable<StorageMetadata> crawlBlobStore(
            BlobStore blobStore, String containerName) {
        return crawlBlobStore(blobStore, containerName,
                new ListContainerOptions());
    }

    public static Iterable<StorageMetadata> crawlBlobStore(
            BlobStore blobStore, String containerName,
            ListContainerOptions options) {
        return new CrawlBlobStoreIterable(blobStore, containerName, options);
    }

    public static BlobStore storeFromProperties(Properties properties) {
        String provider = properties.getProperty(Constants.PROPERTY_PROVIDER);
        ContextBuilder builder = ContextBuilder
                .newBuilder(provider)
                .modules(ImmutableList.<Module>of(new SLF4JLoggingModule()))
                .overrides(properties);
        BlobStoreContext context = builder.build(BlobStoreContext.class);
        return context.getBlobStore();
    }

    private static class CrawlBlobStoreIterable
            implements Iterable<StorageMetadata> {
        private final BlobStore blobStore;
        private final String containerName;
        private final ListContainerOptions options;

        CrawlBlobStoreIterable(BlobStore blobStore, String containerName,
                               ListContainerOptions options) {
            this.blobStore = Objects.requireNonNull(blobStore);
            this.containerName = Objects.requireNonNull(containerName);
            this.options = Objects.requireNonNull(options).clone();
        }

        @Override
        public Iterator<StorageMetadata> iterator() {
            return new CrawlBlobStoreIterator(blobStore, containerName,
                    options);
        }
    }

    private static class CrawlBlobStoreIterator
            extends AbstractIterator<StorageMetadata> {
        private final BlobStore blobStore;
        private final String containerName;
        private final ListContainerOptions options;
        private Iterator<? extends StorageMetadata> iterator;
        private String marker;

        CrawlBlobStoreIterator(BlobStore blobStore, String containerName,
                               ListContainerOptions options) {
            this.blobStore = Objects.requireNonNull(blobStore);
            this.containerName = Objects.requireNonNull(containerName);
            this.options = Objects.requireNonNull(options);
            if (options.getDelimiter() == null && options.getDir() == null) {
                this.options.recursive();
            }
            advance();
        }

        private void advance() {
            if (marker != null) {
                options.afterMarker(marker);
            }
            PageSet<? extends StorageMetadata> set = blobStore.list(
                    containerName, options);
            marker = set.getNextMarker();
            iterator = set.iterator();
        }

        @Override
        protected StorageMetadata computeNext() {
            while (true) {
                if (!iterator.hasNext()) {
                    if (marker == null) {
                        return endOfData();
                    }
                    advance();
                    continue;
                }

                StorageMetadata metadata = iterator.next();
                // filter out folders with atmos and filesystem providers
                // accept metadata == null for Google Cloud Storage folders
                if (metadata == null || metadata.getType() == StorageType.RELATIVE_PATH) {
                    continue;
                }
                return metadata;
            }
        }
    }

    public static String trimETag(@Nullable String eTag) {
        if (eTag == null) {
            return null;
        }
        int begin = 0;
        int end = eTag.length();
        if (eTag.startsWith("\"")) {
            begin = 1;
        }
        if (eTag.endsWith("\"")) {
            end = eTag.length() - 1;
        }
        return eTag.substring(begin, end);
    }

    public static boolean eTagsEqual(@Nullable String eTag1, @Nullable String eTag2) {
        return Objects.equals(trimETag(eTag1), trimETag(eTag2));
    }
}
