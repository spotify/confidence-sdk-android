package com.example.confidencedemoapp

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dev.openfeature.contrib.providers.ConfidenceFeatureProvider
import dev.openfeature.sdk.*
import dev.openfeature.sdk.async.awaitProviderReady
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

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
        val applicationContext = app.applicationContext
        val metadataBundle = applicationContext.packageManager.getApplicationInfo(
            applicationContext.packageName,
            PackageManager.GET_META_DATA
        ).metaData
        val clientSecret = metadataBundle.getString("com.example.confidencedemoapp.clientSecret")!!
        OpenFeatureAPI.setProvider(
            ConfidenceFeatureProvider.create(
                app.applicationContext,
                clientSecret
            ),
            initialContext = ctx
        )
        client = OpenFeatureAPI.getClient()

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                awaitProviderReady()
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
