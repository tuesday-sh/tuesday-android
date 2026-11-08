package sh.tuesday.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import sh.tuesday.app.navigation.DeepLinkDestination
import sh.tuesday.app.services.ConnectivityMonitor
import sh.tuesday.app.services.GeminiDiscoveryService
import sh.tuesday.app.services.TuesdayRepository
import sh.tuesday.app.theme.TuesdayAppTheme
import sh.tuesday.app.ui.home.HomeScreen
import sh.tuesday.app.ui.wizard.AddDeviceScreen

class MainActivity : ComponentActivity() {
    private lateinit var repository: TuesdayRepository
    private lateinit var connectivity: ConnectivityMonitor
    private lateinit var discoveryService: GeminiDiscoveryService

    private val _pendingDestination = MutableStateFlow<DeepLinkDestination?>(null)
    val pendingDestination = _pendingDestination.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TuesdayRepository(applicationContext)
        connectivity = ConnectivityMonitor(applicationContext)
        discoveryService = GeminiDiscoveryService(applicationContext)

        handleIntent(intent)

        setContent {
            TuesdayAppTheme {
                val dest by pendingDestination.collectAsState()
                AppNavigation(
                    repository = repository,
                    connectivity = connectivity,
                    discoveryService = discoveryService,
                    pendingDestination = dest,
                    onConsumePendingDestination = {
                        _pendingDestination.value = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val dataUri = intent?.dataString ?: return
        val destination = DeepLinkDestination.fromUri(dataUri)
        if (destination != null) {
            _pendingDestination.value = destination
        }
    }
}

@Composable
fun AppNavigation(
    repository: TuesdayRepository,
    connectivity: ConnectivityMonitor,
    discoveryService: GeminiDiscoveryService,
    pendingDestination: DeepLinkDestination? = null,
    onConsumePendingDestination: () -> Unit = {}
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                repository = repository,
                connectivity = connectivity,
                onNavigateToAddDevice = {
                    navController.navigate("add_device")
                },
                pendingDestination = pendingDestination,
                onConsumePendingDestination = onConsumePendingDestination
            )
        }
        composable("add_device") {
            AddDeviceScreen(
                repository = repository,
                discoveryService = discoveryService,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
