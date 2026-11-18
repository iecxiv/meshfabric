package iecxiv.meshfabric.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R

@Composable internal fun AppRefreshIconButton(onRefresh:()->Unit,enabled:Boolean=true,modifier:Modifier=Modifier,
    contentDescription:String=stringResource(R.string.refresh)) {
    val rotation=remember {Animatable(0f)}
    var refreshing by remember {mutableStateOf(false)}
    val currentRefresh by rememberUpdatedState(onRefresh)
    val restingColor=LocalContentColor.current
    LaunchedEffect(refreshing) {
        if(refreshing) {
            try {
                currentRefresh()
                rotation.animateTo(360f,animationSpec=tween(durationMillis=650,easing=LinearEasing))
            } finally {
                rotation.snapTo(0f)
                refreshing=false
            }
        }
    }
    IconButton(onClick={refreshing=true},enabled=enabled && !refreshing,modifier=modifier.size(38.dp)) {
        Icon(Icons.Default.Refresh,contentDescription,Modifier.size(20.dp).rotate(rotation.value),
            tint=if(refreshing) Color(0xFF2196F3) else if(enabled) restingColor else restingColor.copy(alpha=restingColor.alpha*0.38f))
    }
}
