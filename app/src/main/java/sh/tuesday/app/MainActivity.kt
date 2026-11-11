package sh.tuesday.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import sh.tuesday.app.navigation.DeepLinkDestination
import sh.tuesday.app.services.ConnectivityMonitor
import sh.tuesday.app.services.GeminiDiscoveryService
import sh.tuesday.app.services.TuesdayNotificationManager
import sh.tuesday.app.services.TuesdayRepository
import sh.tuesday.app.theme.TuesdayAppTheme
import sh.tuesday.app.ui.home.HomeScreen
import sh.tuesday.app.ui.wizard.AddDeviceScreen

class MainActivity : ComponentActivity() {
    private lateinit var repository: TuesdayRepository
    private lateinit var connectivity: ConnectivityMonitor
    private lateinit var discoveryService: GeminiDiscoveryService
    private lateinit var notificationManager: TuesdayNotificationManager

    private val _pendingDestination = MutableStateFlow<DeepLinkDestination?>(null)
    val pendingDestination = _pendingDestination.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TuesdayRepository(applicationContext)
        connectivity = ConnectivityMonitor(applicationContext)
        discoveryService = GeminiDiscoveryService(applicationContext)
        notificationManager = TuesdayNotificationManager(applicationContext)

        handleIntent(intent)

        setContent {
            TuesdayAppTheme {
                val dest by pendingDestination.collectAsState()
                AppNavigation(
                    repository = repository,
                    connectivity = connectivity,
                    discoveryService = discoveryService,
                    notificationManager = notificationManager,
                    onRequestNotificationPermission = {
                        requestNotificationPermissionIfNeeded()
                    },
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

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        val deliveryId = intent?.getIntExtra(TuesdayNotificationManager.EXTRA_NOTIFICATION_DELIVERY_ID, -1) ?: -1
        if (deliveryId > 0) {
            repository.acknowledgeNotificationAsync(deliveryId, "opened")
        }
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
    notificationManager: TuesdayNotificationManager? = null,
    onRequestNotificationPermission: () -> Unit = {},
    pendingDestination: DeepLinkDestination? = null,
    onConsumePendingDestination: () -> Unit = {}
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                repository = repository,
                connectivity = connectivity,
                notificationManager = notificationManager,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onNavigateToAddDevice = { mode, synth, query, clues, autoSearch ->
                    val params = buildList {
                        mode?.let { add("mode=${android.net.Uri.encode(it)}") }
                        synth?.let { add("synth=${android.net.Uri.encode(it)}") }
                        query?.let { add("query=${android.net.Uri.encode(it)}") }
                        clues?.let { add("clues=${android.net.Uri.encode(it)}") }
                        if (autoSearch) add("search=true")
                    }
                    val route = if (params.isEmpty()) "add_device" else "add_device?${params.joinToString("&")}"
                    navController.navigate(route)
                },
                pendingDestination = if (pendingDestination is DeepLinkDestination.AddDevice) null else pendingDestination,
                onConsumePendingDestination = onConsumePendingDestination
            )
        }
        composable(
            "add_device?mode={mode}&synth={synth}&query={query}&clues={clues}&search={search}",
            arguments = listOf(
                androidx.navigation.navArgument("mode") {
                    nullable = true
                    defaultValue = null
                },
                androidx.navigation.navArgument("synth") {
                    nullable = true
                    defaultValue = null
                },
                androidx.navigation.navArgument("query") {
                    nullable = true
                    defaultValue = null
                },
                androidx.navigation.navArgument("clues") {
                    nullable = true
                    defaultValue = null
                },
                androidx.navigation.navArgument("search") {
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val modeArg = backStackEntry.arguments?.getString("mode")
            val synthArg = backStackEntry.arguments?.getString("synth")
            val queryArg = backStackEntry.arguments?.getString("query")
            val cluesArg = backStackEntry.arguments?.getString("clues")
            val searchArg = backStackEntry.arguments?.getString("search") == "true"
            val initialMode = when (modeArg) {
                "scan" -> sh.tuesday.app.ui.wizard.EnrollmentMode.BARCODE_SCAN
                "manual" -> sh.tuesday.app.ui.wizard.EnrollmentMode.MANUAL_AGENT
                else -> sh.tuesday.app.ui.wizard.EnrollmentMode.PHOTO_WIZARD
            }
            AddDeviceScreen(
                repository = repository,
                discoveryService = discoveryService,
                initialEnrollmentMode = initialMode,
                initialSynthPreset = synthArg,
                initialClueQuery = queryArg,
                initialCluesCsv = cluesArg,
                initialAutoSearch = searchArg,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }

    LaunchedEffect(pendingDestination) {
        if (pendingDestination is DeepLinkDestination.AddDevice) {
            val params = buildList {
                pendingDestination.mode?.let { add("mode=${android.net.Uri.encode(it)}") }
                pendingDestination.synthPreset?.let { add("synth=${android.net.Uri.encode(it)}") }
                pendingDestination.clueQuery?.let { add("query=${android.net.Uri.encode(it)}") }
                pendingDestination.cluesCsv?.let { add("clues=${android.net.Uri.encode(it)}") }
                if (pendingDestination.autoSearch) add("search=true")
            }
            val route = if (params.isEmpty()) "add_device" else "add_device?${params.joinToString("&")}"
            navController.popBackStack("home", inclusive = false)
            navController.navigate(route)
            onConsumePendingDestination()
        } else if (pendingDestination != null) {
            navController.popBackStack("home", inclusive = false)
        }
    }
}
