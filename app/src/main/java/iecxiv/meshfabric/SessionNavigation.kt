package iecxiv.meshfabric
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
@Composable
internal fun rememberSessionNavController(runtime: RemoteSessionRuntime): NavHostController {
    val controller = rememberNavController()
    remember(controller, runtime) {
        runtime.navigationState?.let { controller.restoreState(it) }
        true
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(controller, runtime, owner) {
        val listener = NavController.OnDestinationChangedListener { _, _, _ ->
            runtime.rememberNavigation(controller.saveState())
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) runtime.rememberNavigation(controller.saveState())
        }
        controller.addOnDestinationChangedListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose {
            runtime.rememberNavigation(controller.saveState())
            controller.removeOnDestinationChangedListener(listener)
            owner.lifecycle.removeObserver(observer)
        }
    }
    return controller
}
