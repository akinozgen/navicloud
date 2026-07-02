package com.ozgen.navicloud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ozgen.navicloud.ui.NaviCloudRoot
import com.ozgen.navicloud.ui.theme.NaviCloudTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NaviCloudTheme {
                NaviCloudRoot()
            }
        }
    }
}
