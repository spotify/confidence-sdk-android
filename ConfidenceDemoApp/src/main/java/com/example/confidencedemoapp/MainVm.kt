package com.example.confidencedemoapp

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.spotify.confidence.*
import com.spotify.confidence.openfeature.ConfidenceFeatureProvider
import com.spotify.confidence.openfeature.InitialisationStrategy
import dev.openfeature.sdk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.system.measureTimeMillis

class MainVm(app: Application) : AndroidViewModel(app) {

    companion object {
        private val TAG = MainVm::class.java.simpleName
    }

    private val _message: MutableLiveData<String> = MutableLiveData("initial")
    private val _color: MutableLiveData<Color> = MutableLiveData(Color.Gray)
    private val _surfaceText: MutableLiveData<String> = MutableLiveData("This is a surface text")
    val message: LiveData<String> = _message
    val color: LiveData<Color> = _color
    val surfaceText: LiveData<String> = _surfaceText

    init {
        val start = System.currentTimeMillis()
        val clientSecret = ClientSecretProvider.clientSecret(app.applicationContext)


        val mutableMap = mutableMapOf<String, ConfidenceValue>()
        mutableMap["screen"] = ConfidenceValue.String("value")
        mutableMap["hello"] = ConfidenceValue.Boolean(false)
        mutableMap["NN"] = ConfidenceValue.Double(20.0)
        mutableMap["list"] = ConfidenceValue.stringList(listOf(""))
        mutableMap["my_struct"] = ConfidenceValue.Struct(mapOf("x" to ConfidenceValue.Double(2.0)))

        val confidence = ConfidenceFactory.create(
            app.applicationContext,
            clientSecret,
            initialContext = mapOf("targeting_key" to ConfidenceValue.String("a98a4291-53b0-49d9-bae8-73d3f5da2070")),
            ConfidenceRegion.EUROPE,
            loggingLevel = LoggingLevel.VERBOSE
        )
        val provider = ConfidenceFeatureProvider.create(
            confidence,
            initialisationStrategy = InitialisationStrategy.FetchAndActivate
        )

        viewModelScope.launch {
            OpenFeatureAPI.setProviderAndWait(provider)

            Log.d(TAG, "client secret is $clientSecret")
            Log.d(TAG, "init took ${System.currentTimeMillis() - start} ms")
            refreshUi()
        }
    }

    fun refreshUi() {
        val client = OpenFeatureAPI.getClient()
        Log.d(TAG, "refreshing UI")
        val flagMessageKey = "hawkflag.message"
        val flagMessageDefault = "default"

        val messageValue = client.getStringValue(flagMessageKey, flagMessageDefault)
        val flagColorKey = "hawkflag.color"
        val flagColorDefault = "Gray"
        val colorFlag = client.getStringDetails(flagColorKey, flagColorDefault).apply {
            Log.d(TAG, "reason=$reason")
            Log.d(TAG, "variant=$variant")
            Log.d(TAG, "value=$value")
            Log.d(TAG, "errorCode=$errorCode")
            errorMessage
        }.toComposeColor()
        _message.postValue(messageValue)
        _color.postValue(colorFlag)
        _surfaceText.postValue(
            OpenFeatureAPI.getEvaluationContext()?.asMap()?.entries?.map { "${it.key}=${it.value.friendlyString()}" }
                ?.joinToString { it })
    }

    fun updateContext() {
        val start = System.currentTimeMillis()
        val visitorId = UUID.randomUUID().toString()
        val ctx = mapOf(
            "visitor_id" to Value.String(visitorId),
            "picture" to Value.String("hej"),
            "region" to Value.String("eu")
        )
        viewModelScope.launch {
            Log.d(TAG, "set new EvaluationContext")
            OpenFeatureAPI.setEvaluationContextAndWait(
                ImmutableContext(
                    targetingKey = visitorId,
                    attributes = ctx
                )
            )
            Log.d(
                TAG,
                "set new EvaluationContext took ${System.currentTimeMillis() - start} ms"
            )
            refreshUi()
        }
    }

    fun clear() {
        Log.d(TAG, "clearing context")
        OpenFeatureAPI.setEvaluationContext(ImmutableContext())
    }
}

private fun <String> FlagEvaluationDetails<String>.toComposeColor(): Color {
    if (errorCode != null) return Color.Red
    return when (value.toString().lowercase()) {
        "green" -> Color.Green
        "blue" -> Color.Blue
        "black" -> Color.Black
        "magenta" -> Color.Magenta
        else -> Color.Yellow
    }
}

private fun Value.friendlyString(): String {
    return when (this) {
        is Value.String -> this.string
        is Value.Integer -> this.integer.toString()
        is Value.Double -> this.double.toString()
        is Value.Boolean -> this.boolean.toString()
        is Value.Date -> this.date.toString()
        is Value.Structure -> this.structure.entries.joinToString(
            prefix = "{",
            postfix = "}"
        ) { "${it.key}=${it.value.friendlyString()}" }

        is Value.List -> this.list.joinToString(prefix = "[", postfix = "]") { it.friendlyString() }
        Value.Null -> "<NULL>"
    }
}