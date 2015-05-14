SwiftProxy ![logo](src/main/resources/chameleon.png)
=======
SwiftProxy allows applications using the
[Swift API](https://wiki.openstack.org/wiki/Swift)
to access other object stores,
e.g., Amazon S3, EMC Atmos, Google Cloud Storage, Microsoft Azure.
It also allows local testing of Swift without the complication of actually setting up Swift.
Finally users can extend SwiftProxy with custom middlewares, e.g., caching,
encryption, tiering.

Features
--------
* create, remove, and list containers
* put, get, delete, and list objects
* large objects (static and dynamic)
* copy objects
* store and retrieve object metadata, including user metadata
* authorization via V1 Auth

Supported object stores:

* atmos
* aws-s3
* azureblob
* filesystem (on-disk storage)
* google-cloud-storage
* hpcloud-objectstorage
* openstack-swift
* rackspace-cloudfiles-uk and rackspace-cloudfiles-us
* s3
* swift and swift-keystone (legacy)
* transient (in-memory storage)

Installation
------------

Users can
[download releases](https://github.com/bouncestorage/swiftproxy/releases)
from GitHub.  One can also build the project by running `mvn package`
which produces a binary at
`target/swift-proxy-1.0-SNAPSHOT-jar-with-dependencies.jar`.
SwiftProxy requires Java 8 to run.

Examples
--------

```
java -jar ./swift-proxy-1.0-SNAPSHOT-jar-with-dependencies.jar --properties swiftproxy.conf
```

Users can configure SwiftProxy via a properties file.  An example
using Amazon S3 as the backing store:

```
swiftproxy.endpoint=http://0.0.0.0:8080

jclouds.provider=aws-s3
jclouds.identity=AWS_ACCESSKEY
jclouds.credential=AWS_CREDENTIAL
jclouds.region=us-west-2
```

SwiftProxy forwards authentication to the underlying object store,
with the above configuration you can access Amazon S3 with:

```
$ swift -A http://127.0.0.1:8080/auth/v1.0 -U AWS_ACCESSKEY -K AWS_CREDENTIAL list
```

Another example using the local file system as the backing store:

```
swiftproxy.endpoint=http://127.0.0.1:8080
jclouds.provider=filesystem
jclouds.identity=test:tester
jclouds.credential=testing
jclouds.filesystem.basedir=/tmp/swiftproxy
```

Users can also set other Java and
[jclouds](https://github.com/jclouds/jclouds/blob/master/core/src/main/java/org/jclouds/Constants.java)
properties.

Limitations
-----------

SwiftProxy does not support:

* object metadata with filesystem provider on Mac OS X
  ([OpenJDK issue](https://bugs.openjdk.java.net/browse/JDK-8030048))
* object versioning
* ACLs, container metadata and container syncing
* object auto delete (`X-Delete-At` or `X-Delete-After`)
* range get for large objects
* delete multiple objects
* HTTPS frontend (Connecting to HTTPS object store is supported)

Testing
-------

SwiftProxy itself has limited tests and those can be run via `mvn
test`. We use Swift's functional tests to catch incompatibilities with
the Swift API. SwiftProxy passes a large subset of Swift tests, and
there's a helper script to run them against SwiftProxy
(`src/test/resources/run-swift-tests.sh`).

References
----------

* Apache [jclouds](http://jclouds.apache.org/) provides object store
support for SwiftProxy
* [OpenStack Swift tests](https://github.com/openstack/swift/tree/master/test/functional)
used to maintain and improve compatibility with the Swift API
* [Docker Swift](https://github.com/ualbertalib/docker-swift) provides
functionality similar to SwiftProxy when using the filesystem
provider
* [S3Proxy](https://github.com/andrewgaul/s3proxy) provided inspiration
for this project


License
-------
Copyright (C) 2015 Bounce Storage

Licensed under the Apache License, Version 2.0
