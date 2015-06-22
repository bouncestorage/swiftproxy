#!/bin/bash

set -o xtrace
set -o errexit
set -o nounset

source $(dirname $0)/run-swiftproxy.sh


pushd swiftclient-tests

mkdir -p virtualenv
if [ ! -e ./virtualenv/bin/pip ]; then
    virtualenv --no-site-packages --distribute virtualenv
fi

./virtualenv/bin/pip install -r requirements.txt
./virtualenv/bin/pip install -r test-requirements.txt
./virtualenv/bin/pip install nose

wait_for_swiftproxy

mkdir -p ./virtualenv/etc/swift
cat > ./virtualenv/etc/swift/test.conf <<EOF
[func_test]
# sample config for Swift with tempauth
auth_host = 127.0.0.1
auth_port = 8080
auth_ssl = no
auth_prefix = /auth/
normalized_urls = True

account = test
username = tester
password = testing

[unit_test]
fake_syslog = False

[swift-constraints]
max_file_size = 5368709122
max_meta_name_length = 128
max_meta_value_length = 256
max_meta_count = 90
max_meta_overall_size = 2048
max_header_size = 8192
max_object_name_length = 1024
container_listing_limit = 10000
account_listing_limit = 10000
max_account_name_length = 256
max_container_name_length = 256
strict_cors_mode = true
allow_account_management = false

[slo]
max_manifest_segments = 1000

EOF

export PYTHONUNBUFFERED=1
export NOSE_NOCAPTURE=1
export NOSE_NOLOGCAPTURE=1
export PYTHONPATH=.

NOSECMD="stdbuf -oL -eL ./virtualenv/bin/nosetests"
export SWIFT_TEST_CONFIG_FILE=./virtualenv/etc/swift/test.conf

TESTS=$($NOSECMD -v --collect-only |& grep ^tests. | sed -e 's/\.Test/:Test/' | sed -e 's/ \.\.\. ok//' \
    | grep -v ^tests.functional.test_swiftclient:TestFunctional.test_post_account$ \
    | grep -v ^tests.functional.test_swiftclient:TestFunctional.test_post_container$ \
)


$NOSECMD -v --logging-level=debug $TESTS

EXIT_CODE=$?
exit $?

