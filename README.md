# ABC Tunes and Note Sequence Matching
[![Build Status](https://travis-ci.org/danwatford/abc-site-play-framework.svg?branch=master)](https://travis-ci.org/danwatford/abc-site-play-framework)

Play Framework site for the uploading, parsing and processing of ABC notation files.

Files can be processed and parsed to check they are ABC files, they will then be broken into into
separate ABC Tunes and processed to extract the note sub-sequences within each tune. Sub-sequences
across tunes are matched and reported.

# Building
Build this site with sbt using
sbt docker:publishLocal

This will build the site as a Docker container and publish to your local docker repository.

Travis Ci will build any changes to the master branch and publish the resulting Docker container to Docker hub
as danwatford/abc-site-play.

# Running
This Play site is packaged as a Docker container with a dependency on a few configuration files which will need to be
made available to the container.

It is recommended that configuration files are placed in /opt/docker-abc/abc-site on the docker host.

## production.conf
This site depends on a production.conf file to specify a secret value needed by the play framework. Create the
production.conf file and populate with something similar to:

> include "application"
>
> play.crypto.secret="secret_value"

## MongoDB
To run the site you will need a Mongo database running somewhere. If no db.conf file is provided the site
will try to connect to a Mongo DB running on the localhost (docker host), port 27017.

If MongoDB is installed elsewhere a db.conf file is needed with the following settings:
> hostname=138.68.131.102

> port=27017
>
> database=stringstore
>
> collection.strings=strings
>
> collection.requests=requests

This file should be placed alongside your production.conf file.

## Running the docker container
On your docker host run the following:

docker run -d -v  /opt/docker-abc/abc-site:/opt/abc-site-play-docker -p 80:9000 --name=playabc danwatford/abc-site-play

This command will retrieve the latest version of danwatford/abc-site-play, name it playabc, expose the containe's port
9000 at port 80 on the docker host, and bind mount the directory /opt/docker-abc/abc-site on the docker host to
directory /opt/abc-site-play-docker in the container.
