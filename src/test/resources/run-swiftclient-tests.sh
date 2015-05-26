#!/bin/bash

set -o xtrace
set -o errexit
set -o nounset

PROXY_BIN="java -cp target/classes/:./target/swift-proxy-1.1.0-SNAPSHOT-jar-with-dependencies.jar com.bouncestorage.swiftproxy.Main"
PROXY_PORT="8080"
TEST_CONF="${PWD}/target/swiftproxy-saio.conf"
PROXY_PID=

function cleanup {
    if [ "$PROXY_PID" != "" ]; then
        kill $PROXY_PID
    fi
}

trap cleanup EXIT

cat > target/swiftproxy-saio.conf <<EOF
swiftproxy.endpoint=http://127.0.0.1:8080

jclouds.provider=transient
jclouds.identity=test:tester
jclouds.credential=testing
EOF

stdbuf -oL -eL $PROXY_BIN --properties $TEST_CONF &
PROXY_PID=$!

# wait for SwiftProxy to start
for i in $(seq 30);
do
    if exec 3<>"/dev/tcp/localhost/8080";
    then
        exec 3<&-  # Close for read
        exec 3>&-  # Close for write
        break
    fi
    sleep 1
done

export PYTHONUNBUFFERED=1
export NOSE_NOCAPTURE=1
export NOSE_NOLOGCAPTURE=1

AUTH=$(stdbuf -oL -eL curl -i http://127.0.0.1:8080/auth/v1.0 -X GET \
    -H 'X-Auth-User: test:tester' -H 'X-Auth-Key: testing' \
    | grep -i x-storage-token | sed -e 's/.*: //')

SWIFT="swift --debug -v -v --os-auth-token $AUTH --os-storage-url http://127.0.0.1:8080/v1/AUTH_test:tester"

$SWIFT list
$SWIFT post test_container
$SWIFT stat test_container
$SWIFT list
$SWIFT upload test_container README.md
$SWIFT stat test_container README.md
$SWIFT list test_container
$SWIFT list test_container --lh

EXIT_CODE=$?
exit $?
