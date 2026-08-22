package com.coinepro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.coinepro.core.auth.SessionController
import com.coinepro.core.marketdata.MarketDataController
import com.coinepro.core.signals.SignalController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessionController: SessionController
    @Inject lateinit var marketDataController: MarketDataController
    @Inject lateinit var signalController: SignalController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoineProApp(sessionController, marketDataController, signalController)
        }
    }
}
