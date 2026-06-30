package com.danchi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.danchi.app.ui.DanChiApp
import com.danchi.app.ui.DanChiViewModel
import com.danchi.app.ui.DanChiViewModelFactory
import com.danchi.app.ui.theme.DanChiTheme

class MainActivity : ComponentActivity() {
    private val viewModel: DanChiViewModel by viewModels {
        DanChiViewModelFactory((application as DanChiApplication).repository, application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DanChiTheme {
                DanChiApp(viewModel)
            }
        }
    }
}
