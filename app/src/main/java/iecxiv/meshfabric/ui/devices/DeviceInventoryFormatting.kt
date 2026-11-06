package iecxiv.meshfabric.ui.devices

import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal data class ReadableInventoryValue(val label: String, val context: String, val value: String)
private fun fieldKey(value: String) = value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
private val labels = mapOf(
    "name" to ("Nombre" to "Name"), "caption" to ("Descripción" to "Description"),
    "description" to ("Descripción" to "Description"), "manufacturer" to ("Fabricante" to "Manufacturer"),
    "vendor" to ("Fabricante" to "Vendor"), "version" to ("Versión" to "Version"),
    "serialnumber" to ("Número de serie" to "Serial number"), "serial" to ("Número de serie" to "Serial number"),
    "uuid" to ("Identificador único" to "Unique identifier"), "model" to ("Modelo" to "Model"),
    "product" to ("Producto" to "Product"), "productname" to ("Nombre del producto" to "Product name"),
    "capacity" to ("Capacidad" to "Capacity"), "size" to ("Capacidad" to "Size"),
    "free" to ("Espacio disponible" to "Free space"), "freespace" to ("Espacio disponible" to "Free space"),
    "filesystem" to ("Sistema de archivos" to "File system"), "mountpoint" to ("Punto de montaje" to "Mount point"),
    "speed" to ("Velocidad" to "Speed"), "configuredclockspeed" to ("Velocidad configurada" to "Configured speed"),
    "devicelocator" to ("Ranura" to "Slot"), "banklabel" to ("Banco de memoria" to "Memory bank"),
    "partnumber" to ("Referencia" to "Part number"), "memorytype" to ("Tipo de memoria" to "Memory type"),
    "arch" to ("Arquitectura" to "Architecture"), "osarchitecture" to ("Arquitectura" to "Architecture"),
    "osdesc" to ("Sistema operativo" to "Operating system"), "osname" to ("Sistema operativo" to "Operating system"),
    "kernelrelease" to ("Versión del núcleo" to "Kernel version"), "kernelbuild" to ("Compilación del núcleo" to "Kernel build"),
    "buildnumber" to ("Número de compilación" to "Build number"), "hostname" to ("Nombre del equipo" to "Host name"),
    "host" to ("Nombre del equipo" to "Host name"), "rname" to ("Nombre del equipo remoto" to "Remote host name"),
    "address" to ("Dirección IP" to "IP address"), "ip" to ("Dirección IP" to "IP address"),
    "mac" to ("Dirección MAC" to "MAC address"), "macaddress" to ("Dirección MAC" to "MAC address"),
    "gateway" to ("Puerta de enlace" to "Gateway"), "netmask" to ("Máscara de red" to "Netmask"),
    "mask" to ("Máscara de red" to "Netmask"), "family" to ("Protocolo" to "Protocol"),
    "cidr" to ("Dirección y prefijo de red" to "Address and prefix"), "scopeid" to ("Ámbito IPv6" to "IPv6 scope"),
    "internal" to ("Interfaz interna" to "Internal interface"), "dhcpenabled" to ("DHCP habilitado" to "DHCP enabled"),
    "dns" to ("Servidor DNS" to "DNS server"), "dnsservers" to ("Servidor DNS" to "DNS server"),
    "cpuname" to ("Procesador" to "Processor"), "gpuname" to ("Tarjeta gráfica" to "Graphics adapter"),
    "numberofcores" to ("Núcleos" to "Cores"), "numberoflogicalprocessors" to ("Procesadores lógicos" to "Logical processors"),
    "maxclockspeed" to ("Frecuencia máxima" to "Maximum clock speed"), "driverversion" to ("Versión del controlador" to "Driver version"),
    "specversion" to ("Versión de la especificación" to "Specification version"), "isenabled" to ("Habilitado" to "Enabled"),
    "isactivated" to ("Activado" to "Activated"), "biosvendor" to ("Fabricante" to "Vendor"),
    "biosversion" to ("Versión de BIOS" to "BIOS version"), "biosdate" to ("Fecha de BIOS" to "BIOS date"),
    "biosmode" to ("Modo de arranque" to "Boot mode"), "boardname" to ("Modelo de placa" to "Board model"),
    "boardvendor" to ("Fabricante de la placa" to "Board vendor"), "boardserial" to ("Número de serie" to "Serial number"),
    "productuuid" to ("Identificador del sistema" to "System identifier"),
    "id" to ("Identificador" to "Identifier"), "ver" to ("Versión" to "Version"), "caps" to ("Capacidades técnicas" to "Capability flags"),
    "agct" to ("Primera conexión del agente" to "First agent connection"),
    "firstconnect" to ("Primera conexión" to "First connection"), "lastconnect" to ("Última conexión" to "Last connection"),
    "lastbootuptime" to ("Último arranque" to "Last boot"), "boottime" to ("Último arranque" to "Last boot"),
    "lastseen" to ("Última actividad" to "Last seen"), "connecttime" to ("Fecha de conexión" to "Connection time"),
    "time" to ("Fecha registrada" to "Recorded time"), "timestamp" to ("Fecha registrada" to "Recorded time"),
    "updatetime" to ("Última actualización" to "Last update"), "lastupdated" to ("Última actualización" to "Last update"),
    "releasedate" to ("Fecha de publicación" to "Release date"), "installdate" to ("Fecha de instalación" to "Install date"),
    "uptimeseconds" to ("Tiempo en funcionamiento" to "Uptime"), "uptimemilliseconds" to ("Tiempo en funcionamiento" to "Uptime")
)
private val containers = mapOf(
    "memory" to ("Módulo de memoria" to "Memory module"), "ram" to ("Memoria" to "Memory"),
    "volumes" to ("Volumen" to "Volume"), "disks" to ("Disco" to "Disk"),
    "cpu" to ("Procesador" to "Processor"), "gpu" to ("Tarjeta gráfica" to "Graphics adapter"),
    "battery" to ("Batería" to "Battery"), "agent" to ("Agente" to "Agent"),
    "netif" to ("Interfaz" to "Interface"), "netif2" to ("Interfaz" to "Interface")
)
private fun translated(pair: Pair<String, String>, es: Boolean) = if (es) pair.first else pair.second
private fun words(value: String): String = value.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
    .replace('_', ' ').replace('-', ' ').replaceFirstChar { it.titlecase() }
private fun partLabel(part: String, es: Boolean): String {
    val base = part.replace(Regex(" \\[\\d+]$"), "")
    val index = part.removePrefix(base).trim().removePrefix("[").removeSuffix("]")
    val label = labels[fieldKey(base)]?.let { translated(it, es) }
        ?: containers[fieldKey(base)]?.let { translated(it, es) } ?: words(base)
    return if (index.isBlank()) label else "$label $index"
}

internal fun readableInventory(row: InventoryValue, section: String, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): ReadableInventoryValue {
    val es = locale.language == "es"
    val parts = row.path.split(" / ")
    val leaf = parts.last()
    val key = fieldKey(leaf.replace(Regex(" \\[\\d+]$"), ""))
    val hidden = setOf("windows", "linux", "darwin", "identifiers", "mobile", "osinfo")
    val context = parts.dropLast(1).filter { it.lowercase(Locale.ROOT) !in hidden }
        .joinToString(" · ") { partLabel(it, es) }
    val value = readableInventoryTime(row.value, key, section, locale, zone)
        ?: when (row.value) {
            "true" -> if (es) "Sí" else "Yes"
            "false" -> if (es) "No" else "No"
            else -> row.value
        }
    return ReadableInventoryValue(partLabel(leaf, es), context, value)
}

internal fun readableInventoryTime(raw: String, key: String, section: String, locale: Locale, zone: ZoneId): String? {
    val es = locale.language == "es"
    if (key in setOf("uptimeseconds", "uptimemilliseconds")) {
        val number = raw.toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val seconds = if (key == "uptimemilliseconds") number / 1000 else number
        return "${seconds / 86400} d · ${(seconds % 86400) / 3600} h · ${(seconds % 3600) / 60} min · ${seconds % 60} s"
    }
    val dates = setOf("agct", "firstconnect", "lastconnect", "lastbootuptime", "boottime", "lastseen", "connecttime", "updatetime", "lastupdated", "releasedate", "installdate", "biosdate", "timestamp")
    if (key !in dates && !(key == "time" && section in setOf("metadata", "connection"))) return null
    if (raw == "0" || raw.isBlank()) return if (es) "No disponible" else "Not available"
    val format = DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm:ss z", locale).withZone(zone)
    val wmi = Regex("^(\\d{14})\\.(\\d{6})([+-])(\\d{3})$").matchEntire(raw)
    if (wmi != null) return runCatching {
        val date = LocalDateTime.parse(wmi.groupValues[1], DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            .withNano(wmi.groupValues[2].toInt() * 1000)
        val minutes = wmi.groupValues[4].toInt() * if (wmi.groupValues[3] == "-") -1 else 1
        format.format(date.toInstant(ZoneOffset.ofTotalSeconds(minutes * 60)))
    }.getOrNull()
    val numeric = runCatching { BigDecimal(raw).longValueExact() }.getOrNull()
    if (numeric != null) {
        val instant = when (numeric) {
            in 946684800L..4102444800L -> Instant.ofEpochSecond(numeric)
            in 946684800000L..4102444800000L -> Instant.ofEpochMilli(numeric)
            else -> null
        }
        return instant?.let(format::format)
    }
    val instant = runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(raw).toInstant() }.getOrNull()
    if (instant != null) return format.format(instant)
    val dateOnly = runCatching { LocalDate.parse(raw) }.getOrNull()
    if (dateOnly != null) return dateOnly.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    val local = runCatching { LocalDateTime.parse(raw) }.getOrNull()
    return local?.format(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm:ss", locale))
        ?.plus(if (es) " (zona no indicada)" else " (zone unspecified)")
}
