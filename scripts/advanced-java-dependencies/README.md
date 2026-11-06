# ADV controller/invoker Java libraries

This AMD64 laboratory batch rebuilds the complete OpenWhisk application
distributions with updated dependencies. It uses the already qualified Java
11.0.33 images from `../advanced-openwhisk-runtime` as bases. The Dockerfile
replaces each application directory with its complete Gradle distribution;
it does not delete individual vulnerable JARs or change runtime security settings.

`qualification.json` records exact source, base/image identities, distribution
hashes, database hashes and test counts. `distribution-manifest.json` records
every packaged application file. `source-overlay-manifest.json` binds the files
in `openwhisk-patch` to the independently committed source branch. The normal
OpenServerless overlay layout and pinned OpenWhisk revision are preserved.

## Build and local checks

Use a clean checkout of the source commit in `qualification.json`, or the exact
pinned OpenWhisk base plus this repository's overlay. Use Java 11, the pinned
Gradle wrapper and a bounded writable Gradle cache. The following commands take
explicit local paths; none discovers, deploys or changes a cluster.

```sh
./gradlew --no-daemon --max-workers=4 \
  -I "$build_repo/scripts/advanced-java-dependencies/focused-tests.gradle" \
  :core:controller:distTar :core:invoker:distTar \
  :tests:testUnit -x :tests:buildArtifacts
```

The test init script selects six existing suites, provides their fixture
configuration and gives test remoting ephemeral ports. `buildArtifacts` creates
unrelated language action fixtures; those are not needed by these six suites.
The timeout regression retains its original oracle. No production networking,
authentication, namespace, admission or platform guards are disabled.

Copy the controller/invoker distribution TARs to a bounded image context named
`controller.tar` and `invoker.tar`, with this Dockerfile. Resolve each base to
the verified image identity recorded in the qualification before building:

```sh
docker build --pull=false --build-arg BASE="$verified_base" \
  --build-arg COMPONENT="$component" -t "$candidate_image" "$image_context"
docker run --rm --network=none --read-only --user=1001:1001 \
  --tmpfs=/tmp:rw,exec,nosuid,nodev,size=128m --entrypoint java \
  -v "$build_repo/scripts/advanced-java-dependencies:/checks:ro" \
  "$candidate_image" -cp "/$component/lib/*" /checks/DependencyChecks.java
```

The private executable tmpfs allows the native Snappy/LZ4 libraries to load in
the isolated test container. It does not alter cluster security settings. The
checks contact no service endpoint. The optional final `legacy` argument checks
the old image's behavior; its existing CBOR linkage failure intentionally returns
exit 2 and is not converted into PASS. Do not run Gradle containers concurrently
against the same cache; their lock communication crosses PID namespaces.

Full distribution bytes can vary with rebuild metadata and dependency repository
availability. The recorded identities qualify only the measured artifacts.

## Evidence and acceptance

- Six existing suites: 142 PASS, zero failures/skips on both the original source
  overlay and this candidate; all test sources compiled.
- Offline candidate checks: 18 PASS per image, covering JSON/YAML/CBOR, protobuf,
  Snappy/LZ4, Kubernetes parsing, AWS dependency binding, logging, Curator, gRPC
  and TLS provider construction. This is not an end-to-end TLS handshake test.
- Synthetic library witnesses: Commons Text script interpolation defaults and
  explicit YAML Java-type construction differ between old and new libraries.
  Inputs are arithmetic and URL construction without I/O. No reachable platform
  exploit is claimed from these library-level observations.
- ADV: 12 Alpha/Beta workflow/persistence/authentication checks, 2 streaming
  checks, 7 custom OPTIONS/GET authorization and cleanup checks, and 24 real
  Python invocation/peer-denial/cleanup checks: 45 PASS in total.
- Catalog, Whisk overrides, running image identities, unchanged container
  settings and Java executable verified; controller/invoker zero restarts.

Wait for rollout AND `/invokers/ready` on the controller's internal port 8080
before acceptance. Kubernetes Ready alone preceded invoker registration here:
the first synthetic GET/OPTIONS owner calls returned 503. Logs show subsequent
Healthy registration, then all acceptance checks passed. Initial failures and
private logs are preserved outside Git. The existing operator only redeploys
these workloads for selected configuration changes, so this lab rollout updated
both explicit Whisk image overrides and matching StatefulSet images, retaining
previous images and private configuration for rollback.

Trivy 0.76.2, with identical databases and no finding exclusions, reports per
image 0 Critical / 0 High, versus 5 Critical / 74 High previously. Remaining
matches: 6 Java Medium, 30 Ubuntu Medium and 42 Ubuntu Low. See the qualification
record for exact advisory/package tuples. Counts are inventory findings, not
proof of exploitation or whole-platform security certification.

The remaining Java findings involve Azure Blob, Akka Management/HTTP and Kafka;
they need their own compatibility batch. ARM64, optional external cloud stores,
fresh installation, production and the full repository suite remain unqualified.
The earlier CBOR error and intermediate build/fixture failures remain separate
evidence. Baseline VM and repositories outside the local forks are unchanged.
