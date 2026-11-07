package iecxiv.meshfabric.ui.management

import com.google.gson.Gson
import java.net.URI

internal const val SERVER_CONSOLE_URL="https://appassets.androidplatform.net/terminal/server-console.html"
internal const val SERVER_CONSOLE_LIMIT=64000
internal fun serverConsoleAsset(url:String):String?=runCatching {
    val uri=URI(url)
    if(uri.scheme!="https" || uri.host!="appassets.androidplatform.net" || uri.port !in listOf(-1,443) || uri.rawUserInfo!=null || uri.rawQuery!=null || uri.rawFragment!=null) return null
    val allowed=setOf("/terminal/server-console.html","/terminal/server-console.js","/terminal/server-console-input.js","/terminal/xterm.js","/terminal/xterm.css","/terminal/xterm-fit.js")
    uri.rawPath.takeIf { it in allowed }?.removePrefix("/")
}.getOrNull()
internal fun serverConsolePlainText(text:String):String {
    val result=StringBuilder(text.length)
    var i=0
    while(i<text.length) {
        val c=text[i++]
        if(c=='\u001b') {
            if(i>=text.length) break
            when(val kind=text[i++]) {
                '['->{ while(i<text.length && text[i] !in '@'..'~') i++; if(i<text.length) i++ }
                ']','P','X','^','_'->{
                    while(i<text.length) {
                        if(kind==']' && text[i]=='\u0007') { i++; break }
                        if(text[i]=='\u001b' && i+1<text.length && text[i+1]=='\\') { i+=2; break }
                        i++
                    }
                }
                else->Unit
            }
        } else if(c=='\r') {
            if(i>=text.length || text[i]!='\n') result.append('\n')
        } else if(c=='\n' || c=='\t' || !c.isISOControl()) result.append(c)
    }
    return result.toString()
}
internal fun serverConsoleSnapshot(entries:List<String>):String {
    val text=serverConsolePlainText(entries.joinToString("\n"))
    if(text.length<=SERVER_CONSOLE_LIMIT) return text
    val prefix="[… truncated]\n"
    var tail=text.takeLast(SERVER_CONSOLE_LIMIT-prefix.length)
    if(tail.isNotEmpty() && Character.isLowSurrogate(tail[0])) tail=tail.drop(1)
    return prefix+tail
}
internal fun serverConsolePayload(text:String,title:String,empty:String,font:Int,language:String="en"):String {
    require(text.length<=SERVER_CONSOLE_LIMIT && title.length<=200 && empty.length<=200 && font in 10..20 && language in setOf("es","en"))
    return Gson().toJson(mapOf("text" to text,"title" to title,"empty" to empty,"font" to font,"language" to language))
}
