package com.example.confidencedemoapp

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.confidencedemoapp.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private lateinit var vm: MainVm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // These are observable states where changed will "update the view"
            val msgState = vm.message.observeAsState()
            val colorState = vm.color.observeAsState()
            val surfaceText = vm.surfaceText.observeAsState()

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

                        Button(onClick = { vm.multiput() }) {
                            Text("MultiPut")
                        }
                        Surface(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxWidth()
                                .height(500.dp),
                            color = colorState.value ?: Color.Gray
                        ) {
                            Text(text = surfaceText.value ?: "N/A")
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