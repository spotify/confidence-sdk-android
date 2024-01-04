[![](https://jitpack.io/v/spotify/confidence-openfeature-provider-kotlin.svg)](https://jitpack.io/#spotify/confidence-openfeature-provider-kotlin)
<a href="https://maven-badges.herokuapp.com/maven-central/com.spotify.confidence/openfeature-provider-android">
<img alt="Maven Central" src="https://maven-badges.herokuapp.com/maven-central/com.spotify.confidence/openfeature-provider-android/badge.svg" />
</a>
# OpenFeature Kotlin Confidence Provider
Kotlin implementation of the [Confidence](https://confidence.spotify.com/) feature provider, to be used in conjunction with the [OpenFeature SDK](https://github.com/open-feature/kotlin-sdk).

## Usage

### Adding the package dependency

The latest release of the Provider is available on Maven central.

<!---x-release-please-start-version-->
Add the following dependency to your gradle file:
```
implementation("com.spotify.confidence:openfeature-provider-android:0.1.6")
```
It can also be consumed from jitpack for using any branch or build:
```
implementation("com.github.spotify:confidence-openfeature-provider-kotlin:[BRANCH]-[SNAPSHOT/Version]")
```
for using specific commit:
```
implementation("com.github.spotify:confidence-openfeature-provider-kotlin:[COMMIT SHA]")
```

Where `0.1.6` is the most recent version of this SDK. Released versions can be found under "Releases" within this repository.
<!---x-release-please-end-->

### Enabling the provider, setting the evaluation context and resolving flags
```kotlin
coroutineScope.launch {
    OpenFeatureAPI.setProviderAndWait(
        ConfidenceFeatureProvider.create(
            applicationContext,
            "<MY_SECRET>"
        ),
        dispatcher = Dispatchers.IO,
        initialContext = ImmutableContext(targetingKey = "myTargetingKey")
    )
    val result = client.getBooleanValue("flag.my-boolean", false)
}
```

`MY_SECRET` is an API key that can be generated in the [Confidence UI](https://confidence.spotify.com/console).

The OpenFeature Event `ProviderReady` is also emitted when the provider is done initializing the cache. For more information on the OpenFeature APIs, follow the related [README](https://github.com/open-feature/kotlin-sdk?tab=readme-ov-file#usage).

### Changing context after the provider initialization 
The evaluation context can be changed during the app session using `setEvaluationContext(...)`.
After calling this method the new context is set for the provider and the flags will be fetched again and the cache and storage will be updated accordingly.
The OpenFeature Events `ProviderStale` and `ProviderReady` events will be emitted accordingly.

Notes:
- If a flag can't be resolved from cache, the provider does NOT automatically resort to calling remote: refreshing the cache from remote only happens when setting a new provider and/or evaluation context in the global OpenFeatureAPI
- It's advised not to perform resolves while `setProvider` and `setEvaluationContext` are running: resolves might return the default value with reason `STALE` during such operations.