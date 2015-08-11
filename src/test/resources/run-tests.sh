#!/bin/bash

set -o xtrace
set -o errexit
set -o nounset


source $(dirname $0)/run-swiftproxy.sh

wait_for_swiftproxy

export SKIP_PROXY=1

src/test/resources/run-swift-tests.sh
src/test/resources/run-swiftclient-python-tests.sh
src/test/resources/run-swiftclient-tests.sh
exit $?
