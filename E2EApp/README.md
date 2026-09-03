# Confidence E2E app

This headless Android app verifies the public consumer APIs of the Confidence SDK and the OpenFeature SDK with `ConfidenceFeatureProvider`. Its instrumentation tests use a local HTTP server, so pull requests do not depend on a client secret or mutable remote flag configuration.

The suite covers:

- Confidence initialization, resolve and apply requests, caching, typed flag evaluation, context mutation, child contexts, event tracking, flushing, and shutdown.
- OpenFeature provider initialization and events, global context updates, boolean, string, integer, double, and object evaluation, evaluation details, tracking, and shutdown.

Run it on a connected emulator or device:

```shell
./gradlew :E2EApp:connectedDebugAndroidTest
```
