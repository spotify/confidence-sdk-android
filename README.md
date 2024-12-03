[![](https://jitpack.io/v/spotify/confidence-sdk-android.svg)](https://jitpack.io/#spotify/confidence-sdk-android)
<a href="https://maven-badges.herokuapp.com/maven-central/com.spotify.confidence/confidence-sdk-android">
<img alt="Maven Central" src="https://maven-badges.herokuapp.com/maven-central/com.spotify.confidence/confidence-sdk-android/badge.svg" />
</a>

# Confidence SDK for Android
This is the Android SDK for [Confidence](https://confidence.spotify.com/), a feature flagging and Experimentation system developed by Spotify.

It also contains the Confidence OpenFeature Provider, to be used in conjunction with the [Open Feature SDK](https://openfeature.dev/docs/reference/concepts/provider/).

For documentation related to flag management and event tracking in Confidence, refer to [Confidence decumentation website](https://confidence.spotify.com/docs).

Functionalities:

* Managed integration with the Confidence backend
* Prefetch and cache flag evaluations, for fast value reads even when the application is offline
* Automatic data collection about which flags have been accessed by the application
* Event tracking for instrumenting your application

## Usage

### Adding the package dependency

The latest release of the SDK is available on Maven central.

<!---x-release-please-start-version-->
Add the following dependency to your gradle file to use it:
```
implementation("com.spotify.confidence:confidence-sdk-android:0.3.6")
```

Where `0.3.6` is the most recent version of this SDK. 

Released versions can be found under "Releases" within this repository.
<!---x-release-please-end-->

### Creating the Confidence instance
You can create your `Confidence` instance using the `ConfidenceFactory` class like this:

```kotlin
val confidence = ConfidenceFactory.create(
    context = app.applicationContext,
    clientSecret = "<MY_SECRET>",
    region = ConfidenceRegion.EUROPE,
    loggingLevel = LoggingLevel.VERBOSE
)
```
Where `MY_SECRET` is an API key that can be generated in the [Confidence UI](https://confidence.spotify.com/console).
The `loggingLevel` sets the verbosity level for logging to console. This can be useful while testing your integration with the Confidence SDK.

_Note: the Confidence SDK has been intended to work as a single instance in your Application. Creating multiple instances in the same runtime could lead to unexpected behaviours._

### Initialization strategy
`confidence.fetchAndActivate()` is an async function that fetches the flags from the Confidence backend, stores the result on disk, and make the same data ready for the Application to be consumed.

The alternative option is to call `confidence.activate()`: this loads previously fetched flags data from storage and makes that available for the Application to consume right away. To avoid waiting on backend calls when the Application starts, the suggested approach is to call `confidence.activate()` and then trigger a background refresh via `confidence.asyncFetch()` for future sessions.

### Setting the context
The context is a key-value map that will be used for sampling and for targeting input in assigning feature flag values by the Confidence backend. It is also a crucial way to create dimensions for metrics generated by event data.

The Confidence SDK supports multiple ways to set the Context. Some of them are mutating the current context of the Confidence instance, others are returning a new instance with the context changes applied.

```kotlin
confidence.putContext("key", ConfidenceValue.String("value")) // this will mutate the context of the current Confidence instance

val otherConfidenceInstance = confidence.withContext("key", ConfidenceValue.String("value")) // this will return a new Confidence instance with the context changes applied but the context of the original instance is kept intact
```

### Resolving flags

**Once the flags are fetched and activated**, you can access their value using the `getValue` method or the `getFlag` method.
Both methods uses generics to return a type defined by the default value type.

The method `getFlag` returns an `Evaluation` object that contains the `value` of the flag, the `reason` for the value returned, and the `variant` selected.

In the case of an error, the default value will be returned and the `Evaluation` contains information about the error.

The method `getValue` will simply return the assigned value or the default.

```kotlin
val message: String = confidence.getValue("flag-name.message", "default message")
val messageFlag: Evaluation<String> = confidence.getFlag("flag-name.message", "default message")

val messageValue = messageFlag.value
// message and messageValue are the same
```

### Tracking an event
Events are defined by a `name` and a `message` where the message is a key-value map of type `<String, ConfidenceValue>`. You can track an event using the `track` method.

All `context` data set on the `Confidence` instance will be appended to the event and its message.

```kotlin
confidence.track("button-tapped", mapOf("button_id" to ConfidenceValue.String("purchase_button")))
```

The Confidence SDK has support for `EventProducer`. This is a way to programmatically emit context changes and events into streams 
which can be consumed by the SDK to automatically emit events or to automatically update context data.

The Confidence SDK comes with a pre-defined event producer to emit some application lifecycle events: `AndroidLifecycleEventProducer`. To use it:
```kotlin
import com.spotify.confidence.AndroidLifecycleEventProducer
confidence.track(
    AndroidLifecycleEventProducer(
        application = getApplication(),
        trackActivities = false // or true
    )
)
```

### Logging
By default, the Confidence SDK will log errors and warnings. You can change the preferred log level by passing a `loggingLevel` to the `Confidence.create()` function.

To turn off logging completely, you can pass `LoggingLevel.NONE` to the `Confidence.create()` function.


## OpenFeature Kotlin Confidence Provider
If you want to use OpenFeature, an OpenFeature Provider for the [OpenFeature SDK](https://github.com/open-feature/kotlin-sdk) is also available.

### Adding the package dependency

The latest release of the Provider is available on Maven central.

<!---x-release-please-start-version-->
Add the following dependency to your gradle file:
```
implementation("com.spotify.confidence:openfeature-provider-android:0.3.6")
```

Where `0.3.6` is the most recent version of the Provider. Released versions can be found under "Releases" within this repository.
<!---x-release-please-end-->


