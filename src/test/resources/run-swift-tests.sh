#!/bin/bash

set -o xtrace
set -o errexit
set -o nounset

pushd swift-tests
virtualenv --no-site-packages --distribute virtualenv

./virtualenv/bin/pip install -r requirements.txt
./virtualenv/bin/pip install -r test-requirements.txt

mkdir -p ./virtualenv/etc/swift
cat > ./virtualenv/etc/swift/test.conf <<EOF
[func_test]
# sample config for Swift with tempauth
auth_host = 127.0.0.1
auth_port = 8080
auth_ssl = no
auth_prefix = /auth/
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
max_meta_overall_size = 4096
max_header_size = 8192
max_object_name_length = 1024
container_listing_limit = 10000
account_listing_limit = 10000
max_account_name_length = 256
max_container_name_length = 256
strict_cors_mode = true
allow_account_management = false

EOF


cd test/functional
SWIFT_TEST_CONFIG_FILE=../../virtualenv/etc/swift/test.conf ../../virtualenv/bin/nosetests

exit 0
