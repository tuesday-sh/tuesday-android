package sh.tuesday.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TuesdayRepository(applicationContext)
        connectivity = ConnectivityMonitor(applicationContext)
        discoveryService = GeminiDiscoveryService(applicationContext)

        setContent {
            TuesdayAppTheme {
                AppNavigation(
                    repository = repository,
                    connectivity = connectivity,
                    discoveryService = discoveryService
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    repository: TuesdayRepository,
    connectivity: ConnectivityMonitor,
    discoveryService: GeminiDiscoveryService
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                repository = repository,
                connectivity = connectivity,
                onNavigateToAddDevice = {
                    navController.navigate("add_device")
                }
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
