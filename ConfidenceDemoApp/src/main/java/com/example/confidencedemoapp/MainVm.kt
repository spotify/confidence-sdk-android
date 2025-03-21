package com.example.confidencedemoapp

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.spotify.confidence.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
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
    private var eventSender: EventSender
    private var confidence: Confidence

    init {
        val start = System.currentTimeMillis()
        val clientSecret = ClientSecretProvider.clientSecret()

        val mutableMap = mutableMapOf<String, ConfidenceValue>()
        mutableMap["screen"] = ConfidenceValue.String("value")
        mutableMap["hello"] = ConfidenceValue.Boolean(false)
        mutableMap["NN"] = ConfidenceValue.Double(20.0)
        mutableMap["list"] = ConfidenceValue.stringList(listOf(""))
        mutableMap["my_struct"] = ConfidenceValue.Struct(mapOf("x" to ConfidenceValue.Double(2.0)))

        confidence = ConfidenceFactory.create(
            app.applicationContext,
            clientSecret,
            initialContext = mapOf("targeting_key" to ConfidenceValue.String("a98a4291-53b0-49d9-bae8-73d3f5da2070")),
            ConfidenceRegion.EUROPE,
            loggingLevel = LoggingLevel.VERBOSE
        )
        confidence.track(AndroidLifecycleEventProducer(getApplication(), false))
        confidence.track(
            ConfidenceDeviceInfoContextProducer(
                applicationContext = getApplication(),
                withAppInfo = true,
                withOsInfo = true,
                withDeviceInfo = true,
                withLocale = true
            )
        )

        eventSender = confidence.withContext(mutableMap)

        viewModelScope.launch {
            if (confidence.isStorageEmpty()) {
                confidence.fetchAndActivate()
            } else {
                confidence.activate()
                confidence.asyncFetch()
            }

            Log.d(TAG, "client secret is $clientSecret")
            Log.d(TAG, "init took ${System.currentTimeMillis() - start} ms")
            refreshUi()
        }
    }

    fun refreshUi() {
        Log.d(TAG, "refreshing UI")
        val flagMessageKey = "hawkflag.message"
        val flagMessageDefault = "default"
        val messageValue = confidence.getValue(flagMessageKey, flagMessageDefault)
        val flagColorKey = "hawkflag.color"
        val flagColorDefault = "Gray"
        val colorFlag = confidence.getFlag(flagColorKey, flagColorDefault).apply {
            Log.d(TAG, "reason=$reason")
            Log.d(TAG, "variant=$variant")
        }.toComposeColor()
        _message.postValue(messageValue)
        _color.postValue(colorFlag)
        _surfaceText.postValue(confidence.getContext().entries.map { "${it.key}=${it.value}" }.joinToString { it })
        eventSender.track(
            "navigate",
            mapOf("my_date" to ConfidenceValue.Date(Date()), "my_time" to ConfidenceValue.Timestamp(Date()))
        )
    }

    fun updateContext() {
        val start = System.currentTimeMillis()
        val ctx = mapOf(
            "user_id" to ConfidenceValue.String(UUID.randomUUID().toString()),
            "picture" to ConfidenceValue.String("hej"),
            "region" to ConfidenceValue.String("eu")
        )
        viewModelScope.launch {
            Log.d(TAG, "set new EvaluationContext")
            // or confidence.awaitPutContext(ctx)
            confidence.putContext(ctx)
            confidence.awaitReconciliation()
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

    fun multiput() {
        Log.d(TAG, "putContext1")
        confidence.putContext("user_id", ConfidenceValue.String("first"))
        Log.d(TAG, "putContext2")
        confidence.putContext("user_id", ConfidenceValue.String("second"))
        viewModelScope.launch {
            delay(5)
            Log.d(TAG, "putContext3")
            confidence.putContext("user_id", ConfidenceValue.String("third"))

            Log.d(TAG, "reconcile")
            val time = measureTimeMillis {
                confidence.awaitReconciliation()
            }
            Log.d(TAG, "awaitReconciliation() done in $time ms")
            refreshUi()
        }
    }

    fun clear() {
        Log.d(TAG, "clearing confidence")
        for (key in confidence.getContext()) {
            confidence.removeContext(key.key)
        }
        Log.d(TAG, "confidence context: ${confidence.getContext()}")
    }
}

private fun <T> Evaluation<T>.toComposeColor(): Color {
    if (errorCode != null) return Color.Red
    return when (value) {
        "green" -> Color.Green
        "blue" -> Color.Blue
        "black" -> Color.Black
        "magenta" -> Color.Magenta
        else -> Color.Yellow
    }
}
