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
normalized_urls = True

account = test
username = tester
password = testing

account2 = test2
username2 = tester2
password2 = testing2


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

EOF

export PYTHONUNBUFFERED=1
export NOSE_NOCAPTURE=1
export NOSE_NOLOGCAPTURE=1

if [ $# == 0 ]; then
    SWIFT_TEST_CONFIG_FILE=./virtualenv/etc/swift/test.conf stdbuf -oL -eL ./virtualenv/bin/nosetests -v \
        --logging-level=debug \
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
        test.functional.tests:TestContainer.testFileThenContainerDelete \
        test.functional.tests:TestContainer.testPrefixAndLimit \
        test.functional.tests:TestContainer.testCreate \
        test.functional.tests:TestContainer.testContainerFileListOnContainerThatDoesNotExist \
        test.functional.tests:TestContainer.testCreateOnExisting \
        test.functional.tests:TestContainer.testSlashInName \
        test.functional.tests:TestContainer.testDelete \
        test.functional.tests:TestContainer.testDeleteOnContainerThatDoesNotExist \
        test.functional.tests:TestContainer.testDeleteOnContainerWithFiles \
        test.functional.tests:TestContainer.testFileCreateInContainerThatDoesNotExist \
        test.functional.tests:TestContainer.testLastFileMarker \
        test.functional.tests:TestContainer.testContainerFileList \
        test.functional.tests:TestContainer.testMarkerLimitFileList \
        test.functional.tests:TestContainer.testFileOrder \
        test.functional.tests:TestContainer.testContainerInfoOnContainerThatDoesNotExist \
        test.functional.tests:TestContainer.testContainerFileListWithLimit \
        test.functional.tests:TestContainer.testTooLongName \
        test.functional.tests:TestContainer.testContainerExistenceCachingProblem \
        test.functional.tests:TestContainerPathsEnv \
        test.functional.tests:TestContainerPaths \
        test.functional.tests:TestFile.testCopy \
        test.functional.tests:TestFile.testCopyAccount \
        test.functional.tests:TestFile.testCopy404s \
        test.functional.tests:TestFile.testCopyNoDestinationHeader \
        test.functional.tests:TestFile.testCopyDestinationSlashProblems \
        test.functional.tests:TestFile.testCopyFromHeader \
        test.functional.tests:TestFile.testCopyFromHeader404s \
        test.functional.tests:TestFile.testNameLimit \
        test.functional.tests:TestFile.testQuestionMarkInName \
        test.functional.tests:TestFile.testDeleteThen404s \
        test.functional.tests:TestFile.testBlankMetadataName \
        test.functional.tests:TestFile.testMetadataNumberLimit \
        test.functional.tests:TestFile.testRangedGets \
        test.functional.tests:TestFile.testRangedGetsWithLWSinHeader \
        test.functional.tests:TestFile.testDelete \
        test.functional.tests:TestFile.testMetadataLengthLimits \
        test.functional.tests:TestFile.testEtagWayoff \

else
    SWIFT_TEST_CONFIG_FILE=./virtualenv/etc/swift/test.conf stdbuf -oL -eL ./virtualenv/bin/nosetests -v $@
fi

EXIT_CODE=$?
exit $?
