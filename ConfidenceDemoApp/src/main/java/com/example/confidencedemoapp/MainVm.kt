package com.example.confidencedemoapp

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.spotify.confidence.ConfidenceFeatureProvider
import com.spotify.confidence.InitialisationStrategy
import dev.openfeature.sdk.Client
import dev.openfeature.sdk.EvaluationContext
import dev.openfeature.sdk.FlagEvaluationDetails
import dev.openfeature.sdk.ImmutableContext
import dev.openfeature.sdk.OpenFeatureAPI
import dev.openfeature.sdk.Value
import dev.openfeature.sdk.async.setProviderAndWait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class MainVm(app: Application) : AndroidViewModel(app) {

    companion object {
        private val TAG = MainVm::class.java.simpleName
    }

    private var client: Client
    private var ctx: EvaluationContext = ImmutableContext(targetingKey = UUID.randomUUID().toString())
    private val _message: MutableLiveData<String> = MutableLiveData("initial")
    private val _color: MutableLiveData<Color> = MutableLiveData(Color.Gray)
    val message: LiveData<String> = _message
    val color: LiveData<Color> = _color

    init {
        val start = System.currentTimeMillis()
        val clientSecret = ClientSecretProvider.clientSecret()

        val strategy = if (ConfidenceFeatureProvider.isStorageEmpty(app.applicationContext)) {
            InitialisationStrategy.FetchAndActivate
        } else {
            InitialisationStrategy.ActivateAndFetchAsync
        }

        client = OpenFeatureAPI.getClient()
        viewModelScope.launch {
            OpenFeatureAPI.setEvaluationContext(ctx)
            OpenFeatureAPI.setProviderAndWait(
                ConfidenceFeatureProvider.create(
                    app.applicationContext,
                    clientSecret,
                    initialisationStrategy = strategy
                ),
                dispatcher = Dispatchers.IO
            )
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
