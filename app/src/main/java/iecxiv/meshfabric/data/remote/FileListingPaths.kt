package iecxiv.meshfabric.data.remote

/** Keep browser paths canonical while adapting the Android agent virtual roots on the wire. */
internal object FileListingPaths {
    fun wire(path: String, android: Boolean, windows: Boolean): String =
        if (android) path.trim('/') else WindowsDriveSupport.wire(path, windows)

    fun browser(path: String, android: Boolean, windows: Boolean): String = when {
        android -> "/" + path.trim('/')
        windows -> WindowsDriveSupport.directory(path)
        else -> path
    }
}
