#!/bin/bash
# Update bintray file descriptor with the build number as part of the version string.

DESCRIPTOR="bintray-descriptor.json"

mkdir -p target/universal

touch target/universal/foo.zip

sed -i "s/MICRO_VERSION/$TRAVIS_BUILD_NUMBER/" "$DESCRIPTOR"
