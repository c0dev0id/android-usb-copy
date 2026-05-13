package de.codevoid.usbcopy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.codevoid.usbcopy.ui.DetailScreen
import de.codevoid.usbcopy.ui.SetupScreen
import de.codevoid.usbcopy.ui.TransferScreen
import de.codevoid.usbcopy.viewmodel.SetupViewModel
import de.codevoid.usbcopy.viewmodel.TransferViewModel

class MainActivity : ComponentActivity() {

    private val setupVm: SetupViewModel by viewModels()
    private val transferVm: TransferViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        transferVm.bind()
        setContent {
            MaterialTheme {
                AppNavigation(
                    setupVm = setupVm,
                    transferVm = transferVm,
                    onStartTransfer = {
                        val intent = setupVm.buildStartIntent()
                        startForegroundService(intent)
                    },
                )
            }
        }
    }
}

@Composable
private fun AppNavigation(
    setupVm: SetupViewModel,
    transferVm: TransferViewModel,
    onStartTransfer: () -> Unit,
) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "setup") {
        composable("setup") {
            SetupScreen(
                vm = setupVm,
                onStartTransfer = {
                    onStartTransfer()
                    nav.navigate("transfer")
                },
            )
        }
        composable("transfer") {
            TransferScreen(
                vm = transferVm,
                onTaskClick = { taskId -> nav.navigate("detail/$taskId") },
                onBack = { nav.popBackStack("setup", inclusive = false) },
            )
        }
        composable(
            route = "detail/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) { back ->
            val taskId = back.arguments?.getString("taskId") ?: return@composable
            DetailScreen(
                vm = transferVm,
                taskId = taskId,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
