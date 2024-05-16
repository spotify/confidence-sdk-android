package com.example.confidencedemoapp

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.confidencedemoapp.ui.theme.MyApplicationTheme
import com.spotify.confidence.ConfidenceFactory
import com.spotify.confidence.ConfidenceRegion
import com.spotify.confidence.ConfidenceValue
import kotlinx.coroutines.flow.flow

class MainActivity : ComponentActivity() {
    private lateinit var vm: MainVm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val confidence = ConfidenceFactory.create(
                applicationContext,
                "clientSecret",
                initialContext = mapOf("targeting_key" to ConfidenceValue.String("a98a4291-53b0-49d9-bae8-73d3f5da2070")),
                ConfidenceRegion.EUROPE
            )
            val state = flow {
                confidence.fetchAndActivate()
                emit("READY")
            }.collectAsState(initial ="LOADING")
            // These are observable states where changed will "update the view"
            val msgState = vm.message.observeAsState()
            val colorState = vm.color.observeAsState()

            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Column {
                        Greeting(msgState.value.toString())
                        Button(onClick = {
                            vm.refreshUi()

                        }) {
                            Text("Refresh UI")
                        }
                        Button(onClick = {
                            vm.updateContext()
                        }) {
                            Text("Re-apply evaluationContext")
                        }

                        Surface(
                            modifier = Modifier
                                .width(100.dp)
                                .height(100.dp),
                            color = colorState.value ?: Color.Gray
                        ) {
                            Text("This is just a Surface")
                        }
                    }
                }
            }
        }
    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        vm = ViewModelProvider(this).get(MainVm::class.java)
        return super.onCreateView(name, context, attrs)
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}