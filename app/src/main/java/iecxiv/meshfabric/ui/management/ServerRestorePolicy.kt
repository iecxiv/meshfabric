package iecxiv.meshfabric.ui.management

import net.lingala.zip4j.ZipFile
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File

internal const val RESTORE_COMPRESSED_LIMIT=256L*1024*1024
internal const val RESTORE_EXPANDED_LIMIT=1024L*1024*1024
internal fun restoreAllowed(rights:Long)=(rights and 4L)!=0L
internal fun safeRestorePath(name:String):Boolean {
    val path=name.replace('\\','/')
    return path.isNotBlank() && !path.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(path) && path.split('/').none { it==".." }
}
internal fun restoreEndpoint(server:String,leaf:String):HttpUrl {
    require(leaf in setOf("control.ashx","restoreserver.ashx"))
    val base=server.toHttpUrl()
    require(base.scheme=="https" && base.username.isEmpty() && base.password.isEmpty())
    return base.newBuilder().addPathSegment(leaf).fragment(null).build()
}
internal fun verifyRestoreArchive(file:File,password:String,systemPassword:Boolean,checkActive:()->Unit={}):Boolean {
    require(file.length() in 22..RESTORE_COMPRESSED_LIMIT)
    val secret=password.toCharArray()
    try {
        ZipFile(file,secret).use { zip->
            require(zip.isValidZipFile)
            val headers=zip.fileHeaders
            require(headers.isNotEmpty() && headers.size<=20000)
            var declared=0L
            headers.forEach { header->
                checkActive()
                require(safeRestorePath(header.fileName))
                val attributes=header.externalFileAttributes
                require(attributes==null || attributes.size<4 || (attributes[3].toInt() and 0xF0)!=0xA0)
                require(header.uncompressedSize>=0 && header.uncompressedSize<=RESTORE_EXPANDED_LIMIT-declared)
                declared+=header.uncompressedSize
            }
            val files=headers.filterNot { it.isDirectory }
            val names=files.map { it.fileName.replace('\\','/').substringAfterLast('/') }.toSet()
            require("config.json" in names && "meshcentral.db" in names)
            val verified=!(systemPassword && files.any { it.isEncrypted })
            var expanded=0L
            files.forEach { header->
                checkActive()
                if(!(systemPassword && header.isEncrypted)) zip.getInputStream(header).use { input->
                    val buffer=ByteArray(65536)
                    while(true) {
                        checkActive()
                        val n=input.read(buffer)
                        if(n<0) break
                        require(n<=RESTORE_EXPANDED_LIMIT-expanded)
                        expanded+=n
                    }
                }
            }
            return verified
        }
    } finally { secret.fill('\u0000') }
}
