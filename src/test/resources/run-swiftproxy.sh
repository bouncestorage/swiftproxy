#!/bin/bash

set -o xtrace
set -o errexit
set -o nounset

function cleanup {
    if [ "$DOCKER" != "" ]; then
        sudo docker stop $DOCKER
        sudo docker rm $DOCKER
    fi
    if [ "$PROXY_PID" != "" ]; then
        kill $PROXY_PID
    fi
}

trap cleanup EXIT

: ${TRAVIS:="false"}
if [ "$TRAVIS" != "true" ]; then
    DOCKER=$(sudo docker run -d kahing/docker-swift)
    DOCKER_IP=$(sudo docker inspect  -f '{{ .NetworkSettings.IPAddress }}' $DOCKER)
    export NOSE_NOCAPTURE=1
    export NOSE_NOLOGCAPTURE=1
    export MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"

    cat > target/swiftproxy-saio.conf <<EOF
swiftproxy.endpoint=http://127.0.0.1:8080

jclouds.provider=openstack-swift
jclouds.endpoint=http://${DOCKER_IP}:8080/auth/v1.0
jclouds.keystone.credential-type=tempAuthCredentials
jclouds.identity=test:tester
jclouds.credential=testing
EOF
else
    cat > target/swiftproxy-saio.conf <<EOF
swiftproxy.endpoint=http://127.0.0.1:8080

jclouds.provider=transient
jclouds.identity=test:tester
jclouds.credential=testing
EOF
fi

PROXY_BIN="mvn exec:java -Dswiftproxy.conf=target/swiftproxy-saio.conf"
PROXY_PORT="8080"
DOCKER=
PROXY_PID=

stdbuf -oL -eL $PROXY_BIN &
PROXY_PID=$!

if [ $(basename $0) = "run-swiftproxy.sh" ]; then
    wait $PROXY_PID
    PROXY_PID=
fi

function wait_for_swiftproxy
{

    for i in $(seq 30);
    do
        if exec 3<>"/dev/tcp/localhost/8080";
        then
            exec 3<&-  # Close for read
            exec 3>&-  # Close for write
            return
        fi
        sleep 1
    done

    # we didn't start correctly
    exit
}
