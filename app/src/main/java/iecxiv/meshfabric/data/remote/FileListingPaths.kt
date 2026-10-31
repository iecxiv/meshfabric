package iecxiv.meshfabric.data.remote

/** Adapt only the Android agent virtual roots; Windows and Linux keep their existing paths. */
internal object FileListingPaths {
    fun wire(path: String, android: Boolean, windows: Boolean): String =
        if (android) path.trim('/') else WindowsDriveSupport.wire(path, windows)

    fun browser(path: String, android: Boolean, windows: Boolean): String = when {
        android -> "/" + path.trim('/')
        windows -> WindowsDriveSupport.directory(path)
        else -> path
    }
}
