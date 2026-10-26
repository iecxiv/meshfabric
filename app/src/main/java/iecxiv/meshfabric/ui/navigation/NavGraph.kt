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
import java.net.URLDecoder
import java.net.URLEncoder

private fun encodeArg(value:String):String=URLEncoder.encode(value,"UTF-8")
private fun decodeArg(value:String):String=URLDecoder.decode(value,"UTF-8")

@Composable fun MeshCentralNavGraph(navController:NavHostController,loginViewModel:LoginViewModel,
    deviceListViewModel:DeviceListViewModel,desktopViewModel:RemoteDesktopViewModel,
    terminalViewModel:TerminalViewModel,fileManagerViewModel:FileManagerViewModel,settingsViewModel:SettingsViewModel) {
    val hasSavedSession=remember {
        val session=loginViewModel.sessionManager.getSession()
        session!=null && session.serverUrl.isNotBlank() && session.authCookie.isNotBlank()
    }
    NavHost(navController=navController,startDestination=if(hasSavedSession) "devices" else "login") {
        composable("login") {
            LoginScreen(viewModel=loginViewModel,onLoginSuccess={ navController.navigate("devices") {
                popUpTo("login") { inclusive=true }
            } },onOpenSettings={ navController.navigate("settings") })
        }
        composable("devices") {
            MeshFabricHome(viewModel=deviceListViewModel,
                onLogout={ loginViewModel.logout(); navController.navigate("login") { popUpTo("devices") { inclusive=true } } },
                onOpenSettings={ navController.navigate("settings") },
                onOpenDesktop={ id,name->navController.navigate("desktop/${encodeArg(id)}/${encodeArg(name)}") },
                onOpenTerminal={ id,name->navController.navigate("terminal/${encodeArg(id)}/${encodeArg(name)}") },
                onOpenFiles={ id,name->navController.navigate("files/${encodeArg(id)}/${encodeArg(name)}") })
        }
        composable("settings") { SettingsScreen(viewModel=settingsViewModel,onBack={ navController.popBackStack() }) }
        listOf("desktop","terminal","files").forEach { screen->
            composable(route="$screen/{nodeId}/{nodeName}",arguments=listOf(
                navArgument("nodeId") { type=NavType.StringType },navArgument("nodeName") { type=NavType.StringType }
            )) { entry->
                val id=decodeArg(entry.arguments?.getString("nodeId") ?: "")
                val name=decodeArg(entry.arguments?.getString("nodeName") ?: "")
                when(screen) {
                    "desktop"->RemoteDesktopScreen(nodeId=id,nodeName=name,viewModel=desktopViewModel,onBack={ navController.popBackStack() })
                    "terminal"->TerminalScreen(nodeId=id,nodeName=name,viewModel=terminalViewModel,onBack={ navController.popBackStack() })
                    "files"->FileManagerScreen(nodeId=id,nodeName=name,viewModel=fileManagerViewModel,onBack={ navController.popBackStack() })
                    else->Unit
                }
            }
        }
    }
}
