#!/bin/bash

set -o xtrace
set -o errexit
set -o nounset

source $(dirname $0)/run-swiftproxy.sh

wait_for_swiftproxy

export PYTHONUNBUFFERED=1
export NOSE_NOCAPTURE=1
export NOSE_NOLOGCAPTURE=1
CURL="stdbuf -oL -eL curl"

function login {
    AUTH=$($CURL -i http://127.0.0.1:8080/auth/v1.0 -X GET \
        -H 'X-Auth-User: test:tester' -H 'X-Auth-Key: testing' \
        | grep -i x-storage-token | sed -e 's/.*: //')
    echo $AUTH
}

AUTH=$(login)
if [ "$AUTH" = "" ]; then
    sleep 10
    AUTH=$(login)
fi
AUTH=$(login)
if [ "$AUTH" = "" ]; then
    echo "Failed to authenticate"
    exit 1
fi


SWIFT="stdbuf -oL -eL swift --debug -v -v --os-auth-token $AUTH --os-storage-url http://127.0.0.1:8080/v1/AUTH_test:tester"

$SWIFT capabilities
$SWIFT list
$SWIFT post test_container
$SWIFT stat test_container
$SWIFT list
$SWIFT stat test_container README.md
$SWIFT upload test_container README.md
$SWIFT stat test_container README.md
$SWIFT list test_container
$SWIFT list test_container --lh
$SWIFT delete test_container README.md
$SWIFT list test_container --lh

dd if=/dev/zero of=BIG bs=1M count=$((32 * 2 + 1))
$SWIFT upload --segment-container test_container --segment-size $((32 * 1024 * 1024)) test_container BIG
$SWIFT stat test_container BIG
$SWIFT download -o BIG-2 test_container BIG
diff --speed-large-files -q BIG BIG-2
$SWIFT delete test_container BIG

$SWIFT delete test_container
$SWIFT list
