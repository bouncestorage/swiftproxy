#!/bin/bash

set -o xtrace
set -o errexit
set -o nounset

source $(dirname $0)/run-swiftproxy.sh

wait_for_swiftproxy

export PYTHONUNBUFFERED=1
export NOSE_NOCAPTURE=1
export NOSE_NOLOGCAPTURE=1

AUTH=$(stdbuf -oL -eL curl -i http://127.0.0.1:8080/auth/v1.0 -X GET \
    -H 'X-Auth-User: test:tester' -H 'X-Auth-Key: testing' \
    | grep -i x-storage-token | sed -e 's/.*: //')
if [ "$AUTH" = "" ]; then
    echo "Failed to authenticate"
    exit 1
fi

SWIFT="swift --debug -v -v --os-auth-token $AUTH --os-storage-url http://127.0.0.1:8080/v1/AUTH_test:tester"

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
$SWIFT delete test_container
$SWIFT list
