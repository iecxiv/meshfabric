package iecxiv.meshfabric.ui.management
import androidx.compose.runtime.staticCompositionLocalOf
internal val LocalUserGroupActionsInline=staticCompositionLocalOf {false}
internal fun userGroupSubviewActive(vararg states:Boolean)=states.any {it}
