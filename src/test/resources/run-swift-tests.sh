#!/bin/bash

set -o xtrace
set -o errexit
set -o nounset

PROXY_BIN="java -cp target/classes/:./target/swift-proxy-1.0-SNAPSHOT-jar-with-dependencies.jar com.bouncestorage.swiftproxy.Main"
PROXY_PORT="8080"
TEST_CONF="${PWD}/src/main/resources/swiftproxy.conf"

stdbuf -oL -eL $PROXY_BIN --properties $TEST_CONF &
PROXY_PID=$!

trap "kill $PROXY_PID" EXIT

pushd swift-tests

if [ ! -e ./virtualenv/bin/pip ]; then
    virtualenv --no-site-packages --distribute virtualenv
fi

./virtualenv/bin/pip install -r requirements.txt
./virtualenv/bin/pip install -r test-requirements.txt

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


if [ $# == 0 ]; then
    SWIFT_TEST_CONFIG_FILE=./virtualenv/etc/swift/test.conf stdbuf -oL -eL ./virtualenv/bin/nosetests -v \
        test.functional.tests:TestAccountEnv \
        test.functional.tests:TestAccountDev \
        test.functional.tests:TestAccountDevUTF8 \
        test.functional.tests:TestAccountNoContainersEnv \
        test.functional.tests:TestAccountNoContainers \
        test.functional.tests:TestAccountNoContainersUTF8 \
        test.functional.tests:TestContainerEnv \
        test.functional.tests:TestContainerDev \
        test.functional.tests:TestContainerDevUTF8 \
        test.functional.tests:TestContainer.testContainerNameLimit \

else
    SWIFT_TEST_CONFIG_FILE=./virtualenv/etc/swift/test.conf stdbuf -oL -eL ./virtualenv/bin/nosetests -v $@
fi

EXIT_CODE=$?
exit $?
