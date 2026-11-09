package iecxiv.meshfabric.ui.management

internal fun loginHistoryLabel(value:Any?,es:Boolean):String {
val code=when(value) {
is Number->value.toDouble().takeIf { it.isFinite() && it%1.0==0.0 && it>=Int.MIN_VALUE.toDouble() && it<=Int.MAX_VALUE.toDouble() }?.toInt()
is String->value.toIntOrNull()
else->null
}
return when(code) {
107->if(es) "Inicio de sesión válido" else "Successful sign-in"
108->if(es) "Segundo factor de autenticación no válido" else "Invalid second authentication factor"
109->if(es) "Cuenta bloqueada" else "Account locked"
110->if(es) "Contraseña no válida" else "Invalid password"
null->if(es) "Evento sin código válido" else "Event without a valid code"
else->if(es) "Evento desconocido (código $code)" else "Unknown event (code $code)"
}
}
