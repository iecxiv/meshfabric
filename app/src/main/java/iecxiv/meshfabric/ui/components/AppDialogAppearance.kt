package iecxiv.meshfabric.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

internal fun appDialogBackground(scheme:ColorScheme):Color=scheme.surfaceColorAtElevation(6.dp)
internal object AppDialogAppearance {
    val cornerRadius=16.dp
    val borderWidth=1.dp
    val shape:Shape=RoundedCornerShape(cornerRadius)
    val tonalElevation=0.dp
    @Composable fun containerColor():Color=appDialogBackground(MaterialTheme.colorScheme)
    @Composable fun contentColor():Color=MaterialTheme.colorScheme.onSurface
    @Composable fun border():BorderStroke=BorderStroke(borderWidth,MaterialTheme.colorScheme.outlineVariant)
    @Composable fun cardColors():CardColors=CardDefaults.cardColors(
        containerColor=containerColor(),contentColor=contentColor(),
        disabledContainerColor=containerColor(),disabledContentColor=contentColor())
}
@Composable internal fun AppAlertDialog(
    onDismissRequest:()->Unit,confirmButton:@Composable ()->Unit,modifier:Modifier=Modifier,
    dismissButton:(@Composable ()->Unit)?=null,icon:(@Composable ()->Unit)?=null,
    title:(@Composable ()->Unit)?=null,text:(@Composable ()->Unit)?=null,
    shape:Shape=AppDialogAppearance.shape,containerColor:Color=AppDialogAppearance.containerColor(),
    iconContentColor:Color=MaterialTheme.colorScheme.primary,titleContentColor:Color=MaterialTheme.colorScheme.onSurface,
    textContentColor:Color=MaterialTheme.colorScheme.onSurfaceVariant,tonalElevation:Dp=AppDialogAppearance.tonalElevation,
    properties:DialogProperties=DialogProperties()
) {
    CompositionLocalProvider(LocalAbsoluteTonalElevation provides 0.dp) {
        AlertDialog(onDismissRequest=onDismissRequest,confirmButton=confirmButton,
            modifier=modifier.border(AppDialogAppearance.border(),shape),dismissButton=dismissButton,icon=icon,
            title=title,text=text,shape=shape,containerColor=containerColor,iconContentColor=iconContentColor,
            titleContentColor=titleContentColor,textContentColor=textContentColor,tonalElevation=tonalElevation,properties=properties)
    }
}
@Composable internal fun AppDialogSurface(
    modifier:Modifier=Modifier,shape:Shape=AppDialogAppearance.shape,color:Color=AppDialogAppearance.containerColor(),
    contentColor:Color=MaterialTheme.colorScheme.onSurface,tonalElevation:Dp=AppDialogAppearance.tonalElevation,
    shadowElevation:Dp=0.dp,border:BorderStroke?=AppDialogAppearance.border(),content:@Composable ()->Unit
) {
    CompositionLocalProvider(LocalAbsoluteTonalElevation provides 0.dp) {
        Surface(modifier=modifier,shape=shape,color=color,contentColor=contentColor,tonalElevation=tonalElevation,
            shadowElevation=shadowElevation,border=border,content=content)
    }
}
@Composable internal fun AppDialogCard(
    modifier:Modifier=Modifier,shape:Shape=AppDialogAppearance.shape,colors:CardColors=AppDialogAppearance.cardColors(),
    elevation:CardElevation=CardDefaults.cardElevation(),border:BorderStroke?=AppDialogAppearance.border(),
    content:@Composable ColumnScope.()->Unit
) {
    CompositionLocalProvider(LocalAbsoluteTonalElevation provides 0.dp) {
        Card(modifier=modifier,shape=shape,colors=colors,elevation=elevation,border=border,content=content)
    }
}
