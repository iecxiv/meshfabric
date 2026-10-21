package iecxiv.meshfabric.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import iecxiv.meshfabric.ui.desktop.RemoteDesktopScreen
import iecxiv.meshfabric.ui.desktop.RemoteDesktopViewModel
import iecxiv.meshfabric.ui.devices.DeviceListScreen
import iecxiv.meshfabric.ui.devices.DeviceListViewModel
import iecxiv.meshfabric.ui.files.FileManagerScreen
import iecxiv.meshfabric.ui.files.FileManagerViewModel
import iecxiv.meshfabric.ui.login.LoginScreen
import iecxiv.meshfabric.ui.login.LoginViewModel
import iecxiv.meshfabric.ui.settings.SettingsScreen
import iecxiv.meshfabric.ui.settings.SettingsViewModel
import iecxiv.meshfabric.ui.terminal.TerminalScreen
import iecxiv.meshfabric.ui.terminal.TerminalViewModel
import java.net.URLDecoder
import java.net.URLEncoder

private fun encodeArg(value: String): String {
    return try {
        URLEncoder.encode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }
}

private fun decodeArg(value: String): String {
    return try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }
}

@Composable
fun MeshCentralNavGraph(
    navController: NavHostController,
    loginViewModel: LoginViewModel,
    deviceListViewModel: DeviceListViewModel,
    desktopViewModel: RemoteDesktopViewModel,
    terminalViewModel: TerminalViewModel,
    fileManagerViewModel: FileManagerViewModel,
    settingsViewModel: SettingsViewModel
) {
    val hasSavedSession = remember {
        val session = loginViewModel.sessionManager.getSession()
        session != null && session.serverUrl.isNotBlank() && session.authCookie.isNotBlank()
    }

    val startDestination = if (hasSavedSession) "devices" else "login"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("login") {
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    navController.navigate("devices") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onOpenSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("devices") {
            DeviceListScreen(
                viewModel = deviceListViewModel,
                onLogout = {
                    loginViewModel.logout()
                    navController.navigate("login") {
                        popUpTo("devices") { inclusive = true }
                    }
                },
                onOpenSettings = {
                    navController.navigate("settings")
                },
                onOpenDesktop = { nodeId, nodeName ->
                    val encId = encodeArg(nodeId)
                    val encName = encodeArg(nodeName)
                    navController.navigate("desktop/$encId/$encName")
                },
                onOpenTerminal = { nodeId, nodeName ->
                    val encId = encodeArg(nodeId)
                    val encName = encodeArg(nodeName)
                    navController.navigate("terminal/$encId/$encName")
                },
                onOpenFiles = { nodeId, nodeName ->
                    val encId = encodeArg(nodeId)
                    val encName = encodeArg(nodeName)
                    navController.navigate("files/$encId/$encName")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "desktop/{nodeId}/{nodeName}",
            arguments = listOf(
                navArgument("nodeId") { type = NavType.StringType },
                navArgument("nodeName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("nodeId") ?: ""
            val rawName = backStackEntry.arguments?.getString("nodeName") ?: ""
            val nodeId = decodeArg(rawId)
            val nodeName = decodeArg(rawName)
            RemoteDesktopScreen(
                nodeId = nodeId,
                nodeName = nodeName,
                viewModel = desktopViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "terminal/{nodeId}/{nodeName}",
            arguments = listOf(
                navArgument("nodeId") { type = NavType.StringType },
                navArgument("nodeName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("nodeId") ?: ""
            val rawName = backStackEntry.arguments?.getString("nodeName") ?: ""
            val nodeId = decodeArg(rawId)
            val nodeName = decodeArg(rawName)
            TerminalScreen(
                nodeId = nodeId,
                nodeName = nodeName,
                viewModel = terminalViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "files/{nodeId}/{nodeName}",
            arguments = listOf(
                navArgument("nodeId") { type = NavType.StringType },
                navArgument("nodeName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getString("nodeId") ?: ""
            val rawName = backStackEntry.arguments?.getString("nodeName") ?: ""
            val nodeId = decodeArg(rawId)
            val nodeName = decodeArg(rawName)
            FileManagerScreen(
                nodeId = nodeId,
                nodeName = nodeName,
                viewModel = fileManagerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
