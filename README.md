[![](https://jitpack.io/v/spotify/confidence-sdk-android.svg)](https://jitpack.io/#spotify/confidence-sdk-android)
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
implementation("com.spotify.confidence:openfeature-provider-android:0.2.0")
```

Where `0.2.0` is the most recent version of this SDK. Released versions can be found under "Releases" within this repository.
<!---x-release-please-end-->

### Enabling the provider, setting the evaluation context and resolving flags

Please refer to the OpenFeature Kotlin SDK [documentation](https://github.com/open-feature/kotlin-sdk) for more information on OpenFeature specific APIs mentioned throughout this README (e.g. `setProvider`, `setProviderAndWait`, OpenFeature Events).

Basic usage:
```kotlin
coroutineScope.launch {
    OpenFeatureAPI.setProviderAndWait(
        ConfidenceFeatureProvider.create(
            applicationContext,
            "<MY_SECRET>",
            initialisationStrategy = InitialisationStrategy.ActivateAndFetchAsync
        ),
        dispatcher = Dispatchers.IO,
        initialContext = ImmutableContext(targetingKey = "myTargetingKey")
    )
    val result = client.getBooleanValue("flag.my-boolean", false)
}
```

Where:
- `MY_SECRET` is an API key that can be generated in the [Confidence UI](https://confidence.spotify.com/console).

- `initializationStrategy` is set to one of these two options:
  - **FetchAndActivate** (_default_): when calling `setProvider`/`setProviderAndWait`, the Provider attemtps to refresh the cache from remote and emits the OpenFeature Event `ProviderReady` after such an attempt (regardless of its success)
  - **ActivateAndFetchAsync**: when calling `setProvider`/`setProviderAndWait`, the Provider emits the OpenFeature Event `ProviderReady` immediately, utilizing whatever cache was saved on disk from the previous session. After that, the Provider will try to refresh the cache on disk for the future session, without impacting the current session.

### Changing context after the provider initialization 
The evaluation context can be changed during the app session using `setEvaluationContext(...)`.
After calling this method the new context is set for the provider, the flags will be fetched again and the cache and storage will be updated accordingly. The event `ProviderReady` will be emitted once the new flags are ready to be consumed by the application (note that the selected initialization strategy property doesn't play a role in this case).

Notes:
- If a flag can't be resolved from cache, the provider does NOT automatically resort to calling remote: refreshing the cache from remote only happens when setting a new provider and/or evaluation context in the global OpenFeatureAPI
- It's advised not to perform resolves while `setProvider` and `setEvaluationContext` are running: resolves might return the default value with reason `STALE` during such operations. The event `ProviderReady` can be used to guarantee correctness.

## Apply events
This Provider automatically emits `apply` events to the Confidence backend once a flag's property is read by the application. This allows Confidence to track who was exposed to what variant and when.

_Note: the `apply` event is only generated for flags that are successfully evaluated (i.e. default values returned due to errors don't generate `apply` events)._
_Note: the `apply` event reports which flag and variant was read by the application, but not which property the application has read from such variant's value._

To avoid generating redundant data, as long as the flags' data returned from the backend for a user remains unchanged, only the first time a flag's property is read will generate an `apply` event. This is true also across restarts of the application.

The Provider stores `apply` events on disk until they are emitted correctly, thus ensuring the apply data reaches the backend even if generated when there is no network available (assuming the device will ever re-connect to the network before the application is deleted by the user).

<!-- Add link to the more detailed documentation on apply events in the Confidence portal once it's ready -->
