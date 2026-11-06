# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.

# Incremental ADV qualification: preserve the application distribution exactly.
# BASE must identify the already qualified controller/invoker artifact.
ARG BASE
FROM ${BASE}
USER root
COPY jdk.tar.gz /tmp/adv-jdk.tar.gz
RUN test "$(dpkg --print-architecture)" = amd64 \
 && echo 'c4b8eaf56c93b95b689ffc08d155df1bcdbbffb666f8f71d21d97aedaea6f0ec  /tmp/adv-jdk.tar.gz' | sha256sum -c - \
 && mkdir /usr/local/temurin-11.0.33 \
 && tar -xzf /tmp/adv-jdk.tar.gz -C /usr/local/temurin-11.0.33 --strip-components=1 \
 && test -d '/usr/local/jdk-11.0.17+8' \
 && rm -rf '/usr/local/jdk-11.0.17+8' \
 && ln -s /usr/local/temurin-11.0.33 '/usr/local/jdk-11.0.17+8' \
 && ln -sf /usr/local/temurin-11.0.33/bin/java /usr/bin/java \
 && rm /tmp/adv-jdk.tar.gz
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get -y --no-remove upgrade \
 && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/local/temurin-11.0.33
ENV PATH=/usr/local/temurin-11.0.33/bin:$PATH
