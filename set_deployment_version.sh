#!/bin/bash
# Update bintray file descriptor with the build number as part of the version string.

DESCRIPTOR="bintray-descriptor.json"

sed -i "s/MICRO_VERSION/$TRAVIS_BUILD_NUMBER/" "$DESCRIPTOR"
