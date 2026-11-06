# ADV Kafka and Azure client compatibility batch

This batch updates the OpenWhisk client libraries in controller and invoker.
The Kafka broker, Java 11 runtime, acknowledgements, manual consumer offsets,
authentication, logging and tenant controls remain unchanged. It is qualified
for the existing AMD64 ADV laboratory; it is not a fresh-install or ARM64 release.

The source changes are mirrored at their original paths under `openwhisk-patch`.
`source-overlay-manifest.json` verifies every overlay file against the source
commit in `qualification.json`. The normal pinned OpenWhisk overlay mechanism
is preserved. The task fork selects these locally imported images only on its
separate ADV branch; the baseline catalog is not changed.

## Minimum compatible dependency set

- Kafka client 2.4.0 → 3.9.2. Replace the removed `Total` metric with
  `CumulativeSum`, preserving counter classification and gauge controls.
- Azure Blob 12.7.0 → 12.19.0. Replace deprecated `download()` with the equivalent
  `downloadStream()` in the attachment store, preserving reactive streaming.
- Reactor BOM 2024.0.8 aligns Reactor Netty to 1.2.8 and Reactor Core to 3.7.8.
  Azure 12.18.0 initially brought vulnerable Reactor Netty 1.0.20; simply taking
  the first fixed Blob version was insufficient. The old aggregate Netty JAR
  also conflicted with the split modules in the test classpath. The BOM aligns
  both layouts. Azure 12.18.0 still calls removed `Mono.subscriberContext` from
  synchronous upload; that failure was reproduced. 12.19.0 is the first stable
  subsequent Blob release without that call; there is no 12.18 patch release.
- Retain LZ4 1.11.4 with an explicit constraint on `at.yawk.lz4`. Kafka now
  directly requests that coordinate, bypassing the earlier `org.lz4` substitution.

The Kafka [advisories](https://kafka.apache.org/community/cve-list/) and Reactor
[redirect advisory](https://spring.io/security/cve-2025-22227/) explain the fixed
version floors. Inventory matches do not establish a reachable OPS exploit.
OPS uses ordinary Azure shared-key Blob clients, not client-side encryption;
the Azure change is dependency hygiene. No untrusted action-to-Kafka-client
configuration path was established; process configuration stays trusted.

## Reproduce local qualification

Use the exact source commit, Java 11 and a bounded Gradle cache. The init script
selects seven suites, assigns ephemeral test remoting ports and supplies only
test configuration. It neither changes production configuration nor hides skips.

```sh
./gradlew --no-daemon --max-workers=4 \
  -I "$build_repo/scripts/advanced-kafka-azure/focused-tests.gradle" \
  :core:controller:distTar :core:invoker:distTar \
  :tests:testUnit -x :tests:buildArtifacts \
  :common:scala:checkScalafmt :tests:checkScalafmt
```

The Dockerfile replaces the complete application directory with the freshly
built distribution, using the exact previous image identities recorded in the
qualification. Copy the distributions as `controller.tar` and `invoker.tar` into
a bounded build context. Resolve and verify the base identity before building:

```sh
docker build --pull=false --build-arg BASE="$verified_base" \
  --build-arg COMPONENT="$component" -t "$candidate" "$context"
```

Run `KafkaAzureChecks.java` with `java -cp "/$component/lib/*"` inside the
candidate image. Its explicit modes are `redaction`, `kafka` and `azure`.
`redaction` needs no network. The other modes address only the fixed synthetic
fixture aliases `broker:9092` and `azurite:10000`; the key in the fixture source
is synthetic and grants no cloud access. Use an owned internal Docker network,
no published ports or Docker socket, ephemeral storage and resource limits.

The Kafka fixture uses the exact existing broker image in `qualification.json`,
with a ZooKeeper process from that same image. Configure advertised listener
`PLAINTEXT://broker:9092`, ZooKeeper `zk:2181`, one broker and offset replication
factor 1. The checker creates UUID-named topics, verifies all five compression
modes, exact ordered payloads, absence of implicit commits and correct resume
after explicit commit, and deletes its topics in `finally`.

Azurite uses the pinned image in the qualification, alias `azurite`,
`azurite-blob --blobHost 0.0.0.0 --location /data --disableProductStyleUrl`, and
`AZURITE_ACCOUNTS` with the `ACCOUNT`/`KEY` constants from the checker. This
preserves authentication and service-version validation. The checker verifies
shared-key upload/read/delete and rejects anonymous/wrong-key reads. It creates
`test-ow-travis` for the existing OpenWhisk attachment suite; configure:

```sh
export AZ_ENDPOINT=http://azurite:10000/syntheticaccount
export AZ_ACCOUNT_NAME=syntheticaccount
export AZ_CONTAINER_NAME=test-ow-travis
# AZ_ACCOUNT_KEY must equal the local fixture KEY constant.
./gradlew --offline --no-daemon --max-workers=4 \
  -I "$build_repo/scripts/advanced-kafka-azure/azure-tests.gradle" \
  :tests:testUnit -x :tests:buildArtifacts
```

Run the previous `../advanced-java-dependencies/DependencyChecks.java` on both
new images as a regression gate. Never share a writable Gradle cache between
concurrent containers. Remove only owned synthetic fixture containers/network
after retaining results. Preserve the previous images for rollback.

## Evidence and limits

Exact counts, source/distribution/image identities and identical scan database
hashes are in `qualification.json`; all packaged files are bound by
`distribution-manifest.json`. Private logs and intermediate failures are retained
outside Git. The original Kafka library fails the same authentication-byte
redaction witness that passes on both new images, while wire bytes are preserved.
This is a library-level witness, not an end-to-end SASL attack demonstration.

ADV acceptance waits for controller `/invokers/ready` to report `1/1`, then checks
Alpha/Beta persistence and authorization, OPTIONS, streaming and real Python
actions. Whisk overrides, task catalog and running image identities must agree;
container settings must remain unchanged and controller/invoker restarts zero.

The four Kafka/Azure inventory findings are removed without new High/Critical
matches. Two Java Medium findings remain in Akka Management and Akka HTTP.
Ubuntu findings are separate and unchanged. Real Azure cloud/CDN, other optional
cloud stores, SASL deployment, ARM64, production and the full repository test
suite are not qualified by this batch. No finding exclusions were applied.
