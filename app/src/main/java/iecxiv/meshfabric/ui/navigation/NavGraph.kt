package iecxiv.meshfabric.ui.navigation

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import iecxiv.meshfabric.R
import iecxiv.meshfabric.ui.desktop.RemoteDesktopScreen
import iecxiv.meshfabric.ui.desktop.RemoteDesktopViewModel
import iecxiv.meshfabric.ui.devices.DeviceListViewModel
import iecxiv.meshfabric.ui.management.MeshFabricHome
import iecxiv.meshfabric.ui.files.FileManagerScreen
import iecxiv.meshfabric.ui.files.FileManagerViewModel
import iecxiv.meshfabric.ui.login.LoginScreen
import iecxiv.meshfabric.ui.login.LoginViewModel
import iecxiv.meshfabric.ui.settings.SettingsScreen
import iecxiv.meshfabric.ui.settings.SettingsViewModel
import iecxiv.meshfabric.ui.terminal.TerminalScreen
import iecxiv.meshfabric.ui.terminal.TerminalViewModel
import kotlinx.coroutines.flow.collect

private fun NavHostController.acceptsNavigationFrom(source:NavBackStackEntry):Boolean =
    currentBackStackEntry?.id==source.id && source.lifecycle.currentState==Lifecycle.State.RESUMED

internal fun NavHostController.navigateOnceFrom(source:NavBackStackEntry,route:String,
    options:NavOptionsBuilder.()->Unit={}):Boolean {
    if(!acceptsNavigationFrom(source)) return false
    navigate(route) { options(); launchSingleTop=true }
    return true
}

internal fun NavHostController.backOnceFrom(source:NavBackStackEntry,fallback:String):Boolean {
    if(!acceptsNavigationFrom(source)) return false
    if(previousBackStackEntry==null) {
        if(currentDestination?.route==fallback) return false
    } else if(popBackStack() && currentDestination!=null) return true
    iecxiv.meshfabric.diagnostics.AppDiagnostics.fault("MeshNavigation", "NavGraph.kt:56 Recovering a destination without a usable parent")
    navigate(fallback) {
        popUpTo(graph.id) { inclusive=false }
        launchSingleTop=true
    }
    return true
}

internal fun remoteRoute(screen:String,id:String,name:String):String? {
    if(screen !in setOf("desktop","terminal","files") || id.isBlank()) return null
    return "$screen/${Uri.encode(id)}/${Uri.encode(name.ifBlank { id })}"
}

@Composable private fun UnavailableDestination(onBack:()->Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Text(stringResource(R.string.navigation_unavailable),textAlign=TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick=onBack) { Text(stringResource(R.string.back)) }
    }
}

@Composable fun MeshCentralNavGraph(navController:NavHostController,loginViewModel:LoginViewModel,
    deviceListViewModel:DeviceListViewModel,desktopViewModel:RemoteDesktopViewModel,
    terminalViewModel:TerminalViewModel,fileManagerViewModel:FileManagerViewModel,settingsViewModel:SettingsViewModel) {
    fun recoveryRoute():String {
        val session=loginViewModel.sessionManager.getSession()
        val saved=session!=null && session.serverUrl.isNotBlank() && session.authCookie.isNotBlank()
        return if(loginViewModel.loginSuccess.value || saved) "devices" else "login"
    }
    val initialDestination=remember { recoveryRoute() }
    fun openRemote(source:NavBackStackEntry,screen:String,id:String,name:String) {
        remoteRoute(screen,id,name)?.let { navController.navigateOnceFrom(source,it) }
    }
    NavHost(navController=navController,startDestination=initialDestination) {
        composable("login") { entry->
            LaunchedEffect(entry,loginViewModel) {
                entry.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    loginViewModel.loginSuccess.collect { success->
                        if(success) navController.navigateOnceFrom(entry,"devices") {
                            popUpTo("login") { inclusive=true }
                        }
                    }
                }
            }
            LoginScreen(viewModel=loginViewModel,onLoginSuccess={},
                onOpenSettings={ navController.navigateOnceFrom(entry,"settings") })
        }
        composable("devices") { entry->
            MeshFabricHome(viewModel=deviceListViewModel,
                onLogout={
                    if(navController.acceptsNavigationFrom(entry)) {
                        terminalViewModel.clearRendering()
                        loginViewModel.logout()
                        navController.navigateOnceFrom(entry,"login") { popUpTo("devices") { inclusive=true } }
                    }
                },
                onOpenSettings={ navController.navigateOnceFrom(entry,"settings") },
                onOpenDesktop={ id,name->openRemote(entry,"desktop",id,name) },
                onOpenTerminal={ id,name->openRemote(entry,"terminal",id,name) },
                onOpenFiles={ id,name->openRemote(entry,"files",id,name) })
        }
        composable("settings") { entry->
            val back:()->Unit={ navController.backOnceFrom(entry,recoveryRoute()) }
            BackHandler(onBack=back)
            SettingsScreen(viewModel=settingsViewModel,onBack=back,
                onLogout=if(recoveryRoute()=="devices") ({
                    if(navController.acceptsNavigationFrom(entry)) {
                        deviceListViewModel.closeAllActiveSessions()
                        terminalViewModel.clearRendering()
                        loginViewModel.logout()
                        navController.navigateOnceFrom(entry,"login") {
                            popUpTo(navController.graph.id) { inclusive=false }
                        }
                    }
                }) else null)
        }
        listOf("desktop","terminal","files").forEach { screen->
            composable(route="$screen/{nodeId}/{nodeName}",arguments=listOf(
                navArgument("nodeId") { type=NavType.StringType },navArgument("nodeName") { type=NavType.StringType }
            )) { entry->
                // Navigation already decodes URI path arguments. Do not decode them twice.
                val id=entry.arguments?.getString("nodeId").orEmpty()
                val name=entry.arguments?.getString("nodeName").orEmpty().ifBlank { id }
                val back:()->Unit={ navController.backOnceFrom(entry,recoveryRoute()) }
                BackHandler(onBack=back)
                if(id.isBlank()) UnavailableDestination(back) else when(screen) {
                    "desktop"->RemoteDesktopScreen(nodeId=id,nodeName=name,viewModel=desktopViewModel,onBack=back)
                    "terminal"->TerminalScreen(nodeId=id,nodeName=name,viewModel=terminalViewModel,onBack=back)
                    "files"->FileManagerScreen(nodeId=id,nodeName=name,viewModel=fileManagerViewModel,onBack=back)
                    else->UnavailableDestination(back)
                }
            }
        }
    }
}
