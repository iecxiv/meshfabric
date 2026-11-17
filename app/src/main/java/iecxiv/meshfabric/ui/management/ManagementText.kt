package iecxiv.meshfabric.ui.management
internal fun managementLanguage():String = runCatching {
    iecxiv.meshfabric.MeshFabricApp.instance.resources.configuration.locales[0].language
}.getOrElse { java.util.Locale.getDefault().language }
internal fun managementText(spanish:String,english:String,language:String=managementLanguage()):String =
    if(language.equals("es",ignoreCase=true)) spanish else english
