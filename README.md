[![](https://jitpack.io/v/spotify/confidence-openfeature-provider-kotlin.svg)](https://jitpack.io/#spotify/confidence-openfeature-provider-kotlin)

# OpenFeature Kotlin Confidence Provider
Kotlin implementation of the Confidence feature provider, to be used in conjunction with the OpenFeature SDK.

## Usage

### Adding the package dependency
Add the following dependency to your gradle file:
```
implementation("com.github.spotify:confidence-openfeature-provider-kotlin:<VERSION>")
```
for the latest version:
```
implementation("com.github.spotify:confidence-openfeature-provider-kotlin")
```
for using any branch and commit:
```
implementation("com.github.spotify:confidence-openfeature-provider-kotlin:[BRANCH]-[SNAPSHOT/Version]")
```
for using specific commit:
```
implementation("com.github.spotify:confidence-openfeature-provider-kotlin:[COMMIT SHA]")
```

The Android project must include `maven("https://jitpack.io")` in `settings.gradle` in the repositories block.

Where `<LATEST>` is the most recent version of this SDK. Released versions can be found under "Releases" within this repository.


### Enabling the provider, setting the evaluation context and resolving flags

```kotlin
runBlocking {
    OpenFeatureAPI.setProvider(
        ConfidenceFeatureProvider.Builder(
            applicationContext,
            "mysecret"
        ).build(),
        MutableContext(targetingKey = "myTargetingKey")
    )
}
val result = client.getBooleanValue("flag.my-boolean", false)
```

Notes:
- If a flag can't be resolved from cache, the provider doesn't automatically resort to calling remote: refreshing the cache from remote only happens when setting a new provider and/or evaluation context in the global OpenFeatureAPI
- It's advised not to perform resolves while `setProvider` and `setEvaluationContext` are running: resolves might return the default value with reason `STALE` during such operations.

## Contributing

### Formatting

This repo uses [ktlint](https://github.com/JLLeitschuh/ktlint-gradle) for formatting.

Please consider adding a pre-commit hook for formatting using

```
./gradlew addKtlintCheckGitPreCommitHook
```
Manual formatting is done by invoking
```
./gradlew ktlintFormat
```
