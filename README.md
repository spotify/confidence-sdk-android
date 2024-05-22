[![](https://jitpack.io/v/spotify/confidence-sdk-android.svg)](https://jitpack.io/#spotify/confidence-sdk-android)
<a href="https://maven-badges.herokuapp.com/maven-central/com.spotify.confidence/confidence-sdk-android">
<img alt="Maven Central" src="https://maven-badges.herokuapp.com/maven-central/com.spotify.confidence/confidence-sdk-android/badge.svg" />
</a>

# Confidence SDK for Android
This is the Android SDK for Confidence, a feature flagging and Experimentation system developed by Spotify. The SDK allows you to consume feature flags and track events from your application.

## Usage

### Adding the package dependency

The latest release of the SDK is available on Maven central.

<!---x-release-please-start-version-->
Add the following dependency to your gradle file:
```
implementation("com.spotify.confidence:confidence-sdk-android:0.2.1")
```

Where `0.2.1` is the most recent version of this SDK. Released versions can be found under "Releases" within this repository.
<!---x-release-please-end-->

### Creating the Confidence instance
You can create your `Confidence` instance using the `ConfidenceFactory` class like this:

```kotlin
val confidence = ConfidenceFactory.create(
    context = app.applicationContext,
    clientSecret = "<MY_SECRET>",
    region = ConfidenceRegion.EUROPE
)
```
Where `MY_SECRET` is an API key that can be generated in the [Confidence UI](https://confidence.spotify.com/console).

And make the initial fetching of flags using the `activateAndFetch` method. This is a suspending function that will fetch the flags from the server and activate them.
It needs to be run in a coroutine scope.

```kotlin
viewModelScope.launch {
    confidence.fetchAndActivate()
}
```

### Setting the context
The context is a key-value map that will be used for sampling and targeting input in feature flag resolves. It can be used to target specific users or groups of users.

The Confidence SDK supports multiple ways to set the Context. Some of them are mutating the current context of the Confidence instance, others are returning a new instance with the context changes applied.

```kotlin
confidence.putContext("key", ConfidenceValue.String("value")) // this will mutate the context of the current Confidence instance

val otherConfidenceInstance = confidence.withContext("key", ConfidenceValue.String("value")) // this will return a new Confidence instance with the context changes applied
```

### Resolving feature flags
**Once the flags are fetched and activated**, you can access their value using the `getValue` method or the `confidence.getFlag` method.

The method `getValue` uses generics to return a type defined by the default value type.

The method `getFlag` returns an `Evaluation` object that contains the value of the flag, the reason for the value returned, and the variant selected.
In the case of an error, the default value will be returned and the Evaluation type has information about the error.

```kotlin

val message: String = confidence.getValue("flag-name.message", "default message")
val messageFlag: Evaluation<String> = confidence.getFlag("flag-name.message", "default message")

val messageValue = messageFlag.value
// message and messageValue are the same
```

### Tracking an event
Events are defined by a `name` and a `message` where the message is a key-value map of type `<String, ConfidenceValue>`. You can track an event using the `track` method.

```kotlin
confidence.track("button-tapped", mapOf("button_id" to ConfidenceValue.String("purchase_button")))
```


## Apply events
This SDK automatically emits `apply` events to the Confidence backend once a flag is accessed. This allows Confidence to track who was exposed to what variant and when.

_Note: the `apply` event is only generated for flags that are successfully evaluated (i.e. default values returned due to errors don't generate `apply` events)._
_Note: the `apply` event reports which flag and variant was read by the application, but not which property the application has read from such variant's value._

To avoid generating redundant data, as long as the flags' data returned from the backend for a user remains unchanged, only the first time a flag's property is read will generate an `apply` event. This is true also across restarts of the application.

The SDK stores `apply` events on disk until they are emitted correctly, thus ensuring the apply data reaches the backend even if generated when there is no network available (assuming the device will ever re-connect to the network before the application is deleted by the user).

<!-- Add link to the more detailed documentation on apply events in the Confidence portal once it's ready -->


## OpenFeature Kotlin Confidence Provider
A [Confidence](https://confidence.spotify.com/) Provider for the [OpenFeature SDK](https://github.com/open-feature/kotlin-sdk).

### Adding the package dependency

The latest release of the Provider is available on Maven central.

<!---x-release-please-start-version-->
Add the following dependency to your gradle file:
```
implementation("com.spotify.confidence:openfeature-provider-android:0.2.1")
```

Where `0.2.1` is the most recent version of the Provider. Released versions can be found under "Releases" within this repository.
<!---x-release-please-end-->


### Enabling the provider, setting the evaluation context and resolving flags

The Provider is created using a confidence instance. The Provider is then set in the OpenFeature API using the `setProvider` or `setProviderAndWait` methods.
Please refer to the OpenFeature Kotlin SDK [documentation](https://github.com/open-feature/kotlin-sdk) for more information on OpenFeature specific APIs mentioned throughout this README (e.g. `setProvider`, `setProviderAndWait`, OpenFeature Events).

Basic usage:
```kotlin
val confidence = ConfidenceFactory.create(
    context = app.applicationContext,
    clientSecret = "<MY_SECRET>"
)
coroutineScope.launch {
    OpenFeatureAPI.setProviderAndWait(
        ConfidenceFeatureProvider.create(
            confidence = confidence
        )
    )
    val result = client.getBooleanValue("flag.my-boolean", false)
}
```

#### Changing context after the provider initialization 
The evaluation context can be changed during the app session using `OpenFeatureApi.setEvaluationContext(...)`.
After calling this method the new context is set for the provider, the flags will be fetched again and the cache and storage will be updated accordingly. 
The event `ProviderReady` will be emitted once the new flags are ready to be consumed by the application (note that the selected initialization strategy property doesn't play a role in this case).

Notes:
- If a flag can't be resolved from cache, the provider does NOT automatically resort to calling remote: refreshing the cache from remote only happens when setting a new provider and/or evaluation context in the global OpenFeatureAPI
- It's advised not to access flags while `setProvider` and `setEvaluationContext` are running: flag access might return the default value with reason `STALE` during such operations. The event `ProviderReady` can be used to guarantee correctness.
