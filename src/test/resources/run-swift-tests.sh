#!/bin/bash

set -o xtrace
set -o errexit
set -o nounset

source $(dirname $0)/run-swiftproxy.sh

SWIFT_DOCKER_TESTS=$(echo \
        test.functional.tests:TestContainerPaths \
        test.functional.tests:TestFile.testCopyAccount \
        test.functional.tests:TestFile.testMetadataNumberLimit \
        test.functional.tests:TestFile.testMetadataLengthLimits \
        test.functional.tests:TestFileComparison \
        test.functional.tests:TestSlo.test_slo_copy_account \
        test.functional.tests:TestSlo.test_slo_copy_the_manifest_account \
)


SWIFT_TESTS=$(echo \
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
        test.functional.tests:TestFile.testCopy \
        test.functional.tests:TestFile.testCopy404s \
        test.functional.tests:TestFile.testCopyNoDestinationHeader \
        test.functional.tests:TestFile.testCopyDestinationSlashProblems \
        test.functional.tests:TestFile.testCopyFromHeader \
        test.functional.tests:TestFile.testCopyFromHeader404s \
        test.functional.tests:TestFile.testNameLimit \
        test.functional.tests:TestFile.testQuestionMarkInName \
        test.functional.tests:TestFile.testDeleteThen404s \
        test.functional.tests:TestFile.testBlankMetadataName \
        test.functional.tests:TestFile.testRangedGets \
        test.functional.tests:TestFile.testRangedGetsWithLWSinHeader \
        test.functional.tests:TestFile.testNoContentLengthForPut \
        test.functional.tests:TestFile.testDelete \
        test.functional.tests:TestFile.testBadHeaders \
        test.functional.tests:TestFile.testEtagWayoff \
        test.functional.tests:TestFile.testFileCreate \
        test.functional.tests:TestFile.testDeleteOfFileThatDoesNotExist \
        test.functional.tests:TestFile.testHeadOnFileThatDoesNotExist \
        test.functional.tests:TestFile.testMetadataOnPost \
        test.functional.tests:TestFile.testGetOnFileThatDoesNotExist \
        test.functional.tests:TestFile.testPostOnFileThatDoesNotExist \
        test.functional.tests:TestFile.testMetadataOnPut \
        test.functional.tests:TestFile.testStackedOverwrite \
        test.functional.tests:TestFile.testTooLongName \
        test.functional.tests:TestFile.testZeroByteFile \
        test.functional.tests:TestFile.testEtagResponse \
        test.functional.tests:TestFile.testChunkedPut \
        test.functional.tests:TestDlo.test_get_manifest \
        test.functional.tests:TestDlo.test_get_manifest_document_itself \
        test.functional.tests:TestDlo.test_get_range \
        test.functional.tests:TestDlo.test_get_range_out_of_range \
        test.functional.tests:TestDlo.test_copy \
        test.functional.tests:TestDlo.test_copy_account \
        test.functional.tests:TestDlo.test_copy_manifest \
        test.functional.tests:TestDlo.test_dlo_if_match_get \
        test.functional.tests:TestDlo.test_dlo_if_none_match_get \
        test.functional.tests:TestSlo.test_slo_get_simple_manifest \
        test.functional.tests:TestSlo.test_slo_get_nested_manifest \
        test.functional.tests:TestSlo.test_slo_ranged_get \
        test.functional.tests:TestSlo.test_slo_ranged_submanifest \
        test.functional.tests:TestSlo.test_slo_etag_is_hash_of_etags \
        test.functional.tests:TestSlo.test_slo_etag_is_hash_of_etags_submanifests \
        test.functional.tests:TestSlo.test_slo_etag_mismatch \
        test.functional.tests:TestSlo.test_slo_size_mismatch \
        test.functional.tests:TestSlo.test_slo_copy \
        test.functional.tests:TestSlo.test_slo_copy_the_manifest \
        test.functional.tests:TestSlo.test_slo_get_the_manifest \
        test.functional.tests:TestSlo.test_slo_head_the_manifest \
)

if [ "$TRAVIS" != "true" ]; then
    export NOSE_NOCAPTURE=1
    export NOSE_NOLOGCAPTURE=1
    SWIFT_TESTS="$SWIFT_TESTS $SWIFT_DOCKER_TESTS"
fi

pushd swift-tests

mkdir -p virtualenv
if [ ! -e ./virtualenv/bin/pip ]; then
    virtualenv --no-site-packages --distribute virtualenv
fi

./virtualenv/bin/pip install -r requirements.txt
./virtualenv/bin/pip install -r test-requirements.txt

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

[slo]
max_manifest_segments = 1000

EOF

export PYTHONUNBUFFERED=1

if [ $# == 0 ]; then
    SWIFT_TESTS=$(echo $SWIFT_TESTS | tr ' ' '\n' | sort)
    SWIFT_TEST_CONFIG_FILE=./virtualenv/etc/swift/test.conf stdbuf -oL -eL ./virtualenv/bin/nosetests -v \
        $SWIFT_TESTS

else
    SWIFT_TEST_CONFIG_FILE=./virtualenv/etc/swift/test.conf stdbuf -oL -eL ./virtualenv/bin/nosetests -v $@
fi

EXIT_CODE=$?
exit $?
