package iecxiv.meshfabric.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationRegressionTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    private lateinit var controller:NavHostController

    @Before fun setUp() {
        rule.setContent {
            MaterialTheme {
                controller=rememberNavController()
                NavHost(controller,startDestination="devices",
                    enterTransition={ EnterTransition.None },exitTransition={ ExitTransition.None },
                    popEnterTransition={ EnterTransition.None },popExitTransition={ ExitTransition.None }) {
                    composable("devices") { Text("devices") }
                    composable("settings") { Text("settings") }
                    composable("files/{nodeId}/{nodeName}",arguments=listOf(
                        navArgument("nodeId") { type=NavType.StringType },
                        navArgument("nodeName") { type=NavType.StringType }
                    )) { Text("files") }
                }
            }
        }
        settle()
    }

    private fun settle() {
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(Lifecycle.State.RESUMED,controller.currentBackStackEntry?.lifecycle?.currentState)
        }
    }

    @Test fun repeatedForwardAndBackDoNotEmptyTheStack() {
        rule.runOnIdle {
            val source=controller.currentBackStackEntry!!
            assertTrue(controller.navigateOnceFrom(source,"settings"))
            repeat(30) { assertFalse(controller.navigateOnceFrom(source,"settings")) }
        }
        settle()
        rule.runOnIdle {
            assertEquals("settings",controller.currentDestination?.route)
            val source=controller.currentBackStackEntry!!
            assertTrue(controller.backOnceFrom(source,"devices"))
            repeat(30) { assertFalse(controller.backOnceFrom(source,"devices")) }
        }
        settle()
        rule.runOnIdle {
            assertEquals("devices",controller.currentDestination?.route)
            assertNull(controller.previousBackStackEntry)
        }
    }

    @Test fun repeatedBackAtRootKeepsAVisibleDestination() {
        rule.runOnIdle {
            val source=controller.currentBackStackEntry!!
            repeat(30) { assertFalse(controller.backOnceFrom(source,"devices")) }
            assertEquals("devices",controller.currentDestination?.route)
            assertNotNull(controller.currentBackStackEntry)
        }
    }

    @Test fun callbacksFromAnOldScreenAreIgnored() {
        lateinit var old:androidx.navigation.NavBackStackEntry
        rule.runOnIdle {
            old=controller.currentBackStackEntry!!
            assertTrue(controller.navigateOnceFrom(old,"settings"))
        }
        settle()
        rule.runOnIdle {
            assertFalse(controller.navigateOnceFrom(old,remoteRoute("files","node//test","test")!!))
            assertFalse(controller.backOnceFrom(old,"devices"))
            assertEquals("settings",controller.currentDestination?.route)
        }
    }

    @Test fun missingParentRecoversWithoutPoppingTheLastDestination() {
        rule.runOnIdle {
            controller.navigate("settings") { popUpTo("devices") { inclusive=true } }
        }
        settle()
        rule.runOnIdle {
            assertNull(controller.previousBackStackEntry)
            assertTrue(controller.backOnceFrom(controller.currentBackStackEntry!!,"devices"))
        }
        settle()
        rule.runOnIdle { assertEquals("devices",controller.currentDestination?.route) }
    }

    @Test fun routeArgumentsPreservePercentPlusSlashAndUnicode() {
        val id="node//alpha%+literal%2F"
        val name="PC + 50% / nódo #? &"
        rule.runOnIdle {
            assertTrue(controller.navigateOnceFrom(controller.currentBackStackEntry!!,remoteRoute("files",id,name)!!))
        }
        settle()
        rule.runOnIdle {
            assertEquals(id,controller.currentBackStackEntry!!.arguments!!.getString("nodeId"))
            assertEquals(name,controller.currentBackStackEntry!!.arguments!!.getString("nodeName"))
        }
    }

    @Test fun emptyDisplayNameFallsBackToTheDeviceId() {
        val id="node//test"
        rule.runOnIdle {
            assertNull(remoteRoute("files","", "name"))
            assertNull(remoteRoute("unknown",id,"name"))
            assertTrue(controller.navigateOnceFrom(controller.currentBackStackEntry!!,remoteRoute("files",id,"")!!))
        }
        settle()
        rule.runOnIdle { assertEquals(id,controller.currentBackStackEntry!!.arguments!!.getString("nodeName")) }
    }
}
