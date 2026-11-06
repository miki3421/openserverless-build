# ADV incremental Java 11 runtime qualification

This AMD64-only recipe refreshes the JVM and Ubuntu packages of an existing,
qualified OpenWhisk controller or invoker image. It preserves the application
distribution and launch scripts. It does not rebuild OpenWhisk or change its Java
dependencies. The general multi-architecture build remains unchanged.

The controller input must already include the custom OPTIONS authentication fix.
Do not substitute an older stock controller image just because its tag is similar.

## Pinned inputs

- Controller base manifest: `sha256:17ccb18486e35d4165591b5318e559d2124bedb6c70662a76e48c9e268918463`.
- Invoker base manifest: `sha256:5180166bcd57e299a236d60533be03ef16ff756d9742f26c2ac51d0ede47a706`.
- JVM: [Temurin 11.0.33+10](https://github.com/adoptium/temurin11-binaries/releases/tag/jdk-11.0.33%2B10), Linux x64 HotSpot JDK.
- Asset: `OpenJDK11U-jdk_x64_linux_hotspot_11.0.33_10.tar.gz`.
- Size: 195735669 bytes.
- SHA256: `c4b8eaf56c93b95b689ffc08d155df1bcdbbffb666f8f71d21d97aedaea6f0ec`.

Obtain the official release asset and verify both size and SHA256. In a temporary
build context, place this Dockerfile and the verified archive named `jdk.tar.gz`.
The Dockerfile verifies the digest again and rejects non-AMD64 bases. Select an
already imported, verified base by immutable identity:

```sh
docker build --pull=false --build-arg BASE="$base_identity" -t "$candidate_image" "$build_context"
python3 scripts/advanced-openwhisk-runtime/verify.py --base "$base_image" --candidate "$candidate_image" --component controller --output "$private_report"
```

For the invoker, use its base and `--component invoker`. The caller supplies these
explicit inputs; this recipe does not discover or change a cluster. Distribution
of the local candidate images requires a separate publication/import decision.

`apt-get upgrade --no-remove` uses the configured Ubuntu repositories. Package
availability can change, so a later build is not guaranteed to have the same
digest. Capture the resulting image, installed package set and scan provenance.
Do not claim a later artifact is qualified by these results.

The previous JDK directory becomes a compatibility symlink to the new JDK. The
existing application files, entrypoint and command are retained. No datastore
format or tenant credential change is part of this recipe.
The recipe makes the existing effective root image user explicit; it does not
change the default UID. Kubernetes container security settings are retained.

## Qualification on 2026-11-06

The local test compares all application and launcher file hashes (230 controller
files, 228 invoker files). It runs the previous Java 11.0.17 and new Java 11.0.33
without network, with a read-only root filesystem and UID 1001. It checks source
launch/compilation, version, SHA256, TLS 1.3 provider support, populated default
trust store and timezone lookup. This is not an end-to-end TLS handshake test.

Before using a candidate, also verify the imported manifest/config identities,
catalog and explicit Whisk image settings, rollout readiness, real application
invocations, persistence, authentication, streaming and custom OPTIONS denial.
Retain the prior image identities and configuration for rollback.

On the existing ADV lab, 45 live checks passed: 12 Alpha/Beta workflow,
persistence and authentication checks; 2 streaming checks; 6 custom OPTIONS/GET
authorization checks and fixture cleanup; and 24 real Python 3.12/3.15/sys
invocation, peer-denial and cleanup checks. Both running JVM executables and
container image identities were verified. Controller/invoker had zero restarts;
all active pods were ready. The application files and launchers matched their
base images exactly. These counts cover the selected regression batch, not all
platform functionality.

The tested artifact identities and counts are recorded in `qualification.json`.

Trivy 0.76.2 scanned both bases and both candidates with the same database, updated
2026-11-06T06:50:38Z (SHA256
`905befbd56b0e8c9c9908444a64d7bd6456fbb313dd0208d4ad06449a035a17e`).
No finding exclusions were used. Ubuntu High/Critical matches decreased from
3/0 for the controller and 6/0 for the invoker to 0/0 for each. Both candidates
still have 5 Critical and 74 High Java-library matches, counted as unique
advisory/package/installed-version tuples per image. These are scanner results,
not demonstrated exploitable paths. The dependency updates need a separate
source build and compatibility qualification.

ARM64, a fresh complete installation, production and the full repository suite
are not qualified by this incremental AMD64 lab test.
