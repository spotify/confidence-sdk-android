package com.example.confidencedemoapp

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.spotify.confidence.ConfidenceFactory
import com.spotify.confidence.ConfidenceFeatureProvider
import com.spotify.confidence.ConfidenceValue
import com.spotify.confidence.EventSender
import com.spotify.confidence.InitialisationStrategy
import com.spotify.confidence.client.ConfidenceRegion
import dev.openfeature.sdk.Client
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.FlagEvaluationDetails
import dev.openfeature.sdk.ImmutableContext
import dev.openfeature.sdk.OpenFeatureAPI
import dev.openfeature.sdk.Value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class MainVm(app: Application) : AndroidViewModel(app) {

    companion object {
        private val TAG = MainVm::class.java.simpleName
    }

    private var client: Client
    private var ctx: EvaluationContext = ImmutableContext(targetingKey = "a98a4291-53b0-49d9-bae8-73d3f5da2070")
    private val _message: MutableLiveData<String> = MutableLiveData("initial")
    private val _color: MutableLiveData<Color> = MutableLiveData(Color.Gray)
    val message: LiveData<String> = _message
    val color: LiveData<Color> = _color
    private var eventSender: EventSender

    init {
        val start = System.currentTimeMillis()
        val clientSecret = ClientSecretProvider.clientSecret()

        val strategy = if (ConfidenceFeatureProvider.isStorageEmpty(app.applicationContext)) {
            InitialisationStrategy.FetchAndActivate
        } else {
            InitialisationStrategy.ActivateAndFetchAsync
        }

        client = OpenFeatureAPI.getClient()

        val mutableMap = mutableMapOf<String, ConfidenceValue>()
        mutableMap["screen"] = ConfidenceValue.String("value")
        mutableMap["hello"] = ConfidenceValue.Boolean(false)
        mutableMap["NN"] = ConfidenceValue.Double(20.0)
        mutableMap["my_struct"] = ConfidenceValue.Struct(mapOf("x" to ConfidenceValue.Double(2.0)))

        val confidence = ConfidenceFactory.create(
            app.applicationContext,
            clientSecret,
            ConfidenceRegion.EUROPE
        )
        eventSender = confidence.withContext(mutableMap)

        viewModelScope.launch {
            OpenFeatureAPI.setEvaluationContext(ctx)
            val provider = ConfidenceFeatureProvider.create(
                confidence,
                context = app.applicationContext,
                initialisationStrategy = strategy
            )
            OpenFeatureAPI.setProviderAndWait(provider, Dispatchers.IO)

            eventSender.send("navigate")

            Log.d(TAG, "client secret is $clientSecret")
            Log.d(TAG, "init took ${System.currentTimeMillis() - start} ms")
            refreshUi()
        }
    }

    fun refreshUi() {
        Log.d(TAG, "refreshing UI")
        val flagMessageKey = "hawkflag.message"
        val flagMessageDefault = "default"
        val messageValue = client.getStringValue(flagMessageKey, flagMessageDefault)
        val flagColorKey = "hawkflag.color"
        val flagColorDefault = "Gray"
        val colorFlag = client.getStringDetails(flagColorKey, flagColorDefault).apply {
            Log.d(TAG, "reason=$reason")
            Log.d(TAG, "variant=$variant")
        }.toComposeColor()
        _message.postValue(messageValue)
        _color.postValue(colorFlag)

        eventSender.send("navigate")
    }

    fun updateContext() {
        val start = System.currentTimeMillis()
        ctx = ImmutableContext(
            attributes = mutableMapOf(
                "user_id" to Value.String(UUID.randomUUID().toString()),
                "picture" to Value.String("hej"),
                "region" to Value.String("eu")
            )
        )
        viewModelScope.launch {
            Log.d(TAG, "set new EvaluationContext")
            OpenFeatureAPI.setEvaluationContext(ctx)
        }.runCatching {
            invokeOnCompletion {
                Log.d(
                    TAG,
                    "set new EvaluationContext took ${System.currentTimeMillis() - start} ms"
                )
                refreshUi()
            }
        }
    }
}

private fun <T> FlagEvaluationDetails<T>.toComposeColor(): Color {
    if (errorCode != null) return Color.Red
    return when (value) {
        "green" -> Color.Green
        "blue" -> Color.Blue
        "black" -> Color.Black
        "magenta" -> Color.Magenta
        else -> Color.Yellow
    }
}
