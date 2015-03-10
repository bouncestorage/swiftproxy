/*
 * Copyright (c) Bounce Storage, Inc. All rights reserved.
 * For more information, please see COPYRIGHT in the top-level directory.
 */

package com.bouncestorage.swiftproxy;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.inject.Module;
import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.BlobBuilder.PayloadBlobBuilder;
import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.domain.StorageType;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.jclouds.io.ContentMetadata;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Callable;

import static com.google.common.base.Throwables.propagate;

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

    public static Properties propertiesFromFile(File file) throws IOException {
        Properties properties = new Properties();
        try (InputStream is = new FileInputStream(file)) {
            properties.load(is);
        }
        return properties;
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
        private final Callable<Iterator<StorageMetadata>> iteratorCallable;

        CrawlBlobStoreIterable(BlobStore blobStore) {
            iteratorCallable = () -> new CrawlBlobStoreIterator(blobStore);
        }

        CrawlBlobStoreIterable(BlobStore blobStore, String containerName,
                ListContainerOptions options) {
            iteratorCallable = () -> new CrawlBlobStoreIterator(blobStore, containerName, options);
        }

        @Override
        public Iterator<StorageMetadata> iterator() {
            try {
                return iteratorCallable.call();
            } catch (Exception e) {
                throw propagate(e);
            }
        }
    }

    private static class CrawlBlobStoreIterator
            implements Iterator<StorageMetadata> {
        private final ListContainerOptions options;
        private Iterator<? extends StorageMetadata> iterator;
        private String marker;
        private final Callable<PageSet<? extends StorageMetadata>> nextPage;

        CrawlBlobStoreIterator(BlobStore blobStore) {
            options = ListContainerOptions.NONE;
            nextPage = () -> blobStore.list();
            advance();
        }

        CrawlBlobStoreIterator(BlobStore blobStore, String containerName,
                ListContainerOptions options) {
            this.options = Preconditions.checkNotNull(options).recursive();
            nextPage = () -> blobStore.list(containerName, options);
            advance();
        }

        private void advance() {
            if (marker != null) {
                options.afterMarker(marker);
            }
            try {
                PageSet<? extends StorageMetadata> set = nextPage.call();
                marker = set.getNextMarker();
                iterator = set.iterator();
            } catch (Exception e) {
                throw propagate(e);
            }
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext() || marker != null;
        }

        @Override
        public StorageMetadata next() {
            while (true) {
                if (!iterator.hasNext()) {
                    advance();
                }
                StorageMetadata metadata = iterator.next();
                // filter out folders with atmos and filesystem providers
                if (metadata.getType() == StorageType.FOLDER) {
                    continue;
                }
                return metadata;
            }
        }
    }

}
