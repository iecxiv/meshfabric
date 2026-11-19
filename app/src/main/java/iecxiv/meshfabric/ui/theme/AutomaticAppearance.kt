package iecxiv.meshfabric.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

import android.content.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.R
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

internal data class SolarCity(val id:String,val name:String,val latitude:Double,val longitude:Double,val zone:String)
internal val solarCities = """madrid|Madrid|40.4168|-3.7038|Europe/Madrid
barcelona|Barcelona|41.3874|2.1686|Europe/Madrid
canarias|Las Palmas de Gran Canaria|28.1235|-15.4363|Atlantic/Canary
lisbon|Lisboa|38.7223|-9.1393|Europe/Lisbon
london|London|51.5074|-0.1278|Europe/London
paris|Paris|48.8566|2.3522|Europe/Paris
berlin|Berlin|52.52|13.405|Europe/Berlin
rome|Roma|41.9028|12.4964|Europe/Rome
helsinki|Helsinki|60.1699|24.9384|Europe/Helsinki
tromso|Tromso|69.6492|18.9553|Europe/Oslo
newyork|New York|40.7128|-74.006|America/New_York
losangeles|Los Angeles|34.0522|-118.2437|America/Los_Angeles
mexico|Ciudad de Mexico|19.4326|-99.1332|America/Mexico_City
bogota|Bogota|4.711|-74.0721|America/Bogota
lima|Lima|-12.0464|-77.0428|America/Lima
santiago|Santiago|-33.4489|-70.6693|America/Santiago
buenosaires|Buenos Aires|-34.6037|-58.3816|America/Argentina/Buenos_Aires
saopaulo|Sao Paulo|-23.5505|-46.6333|America/Sao_Paulo
tokyo|Tokyo|35.6762|139.6503|Asia/Tokyo
delhi|New Delhi|28.6139|77.209|Asia/Kolkata
singapore|Singapore|1.3521|103.8198|Asia/Singapore
sydney|Sydney|-33.8688|151.2093|Australia/Sydney
auckland|Auckland|-36.8485|174.7633|Pacific/Auckland
capetown|Cape Town|-33.9249|18.4241|Africa/Johannesburg""".lines().map { line ->
    val v=line.trim().split('|'); SolarCity(v[0],v[1],v[2].toDouble(),v[3].toDouble(),v[4])
}
internal fun scheduledAppearanceDark(minute:Int,light:Int,dark:Int):Boolean? {
    if(minute !in 0..1439 || light !in 0..1439 || dark !in 0..1439 || light==dark) return null
    return !(if(light<dark) minute>=light && minute<dark else minute>=light || minute<dark)
}
internal data class SolarWindow(val sunrise:Instant,val sunset:Instant)
// NOAA solar equations, standard apparent horizon of 90.833 degrees.
// https://gml.noaa.gov/grad/solcalc/calcdetails.html
internal fun solarWindow(date:LocalDate,city:SolarCity):SolarWindow? {
    if(!city.latitude.isFinite() || !city.longitude.isFinite() || city.latitude !in -90.0..90.0 || city.longitude !in -180.0..180.0) return null
    fun rad(v:Double)=Math.toRadians(v)
    fun deg(v:Double)=Math.toDegrees(v)
    val midnight=date.atStartOfDay(ZoneOffset.UTC).toInstant()
    val jd=midnight.epochSecond/86400.0+2440587.5
    fun position(minutes:Double):Pair<Double,Double> {
        val t=(jd+minutes/1440.0-2451545.0)/36525.0
        val l=((280.46646+t*(36000.76983+t*0.0003032))%360+360)%360
        val m=357.52911+t*(35999.05029-0.0001537*t)
        val e=0.016708634-t*(0.000042037+0.0000001267*t)
        val c=sin(rad(m))*(1.914602-t*(0.004817+0.000014*t))+sin(rad(2*m))*(0.019993-0.000101*t)+sin(rad(3*m))*0.000289
        val omega=125.04-1934.136*t
        val lambda=l+c-0.00569-0.00478*sin(rad(omega))
        val epsilon=23.0+(26.0+(21.448-t*(46.815+t*(0.00059-t*0.001813)))/60.0)/60.0+0.00256*cos(rad(omega))
        val dec=asin(sin(rad(epsilon))*sin(rad(lambda)))
        val y=tan(rad(epsilon)/2).pow(2)
        val eq=4*deg(y*sin(2*rad(l))-2*e*sin(rad(m))+4*e*y*sin(rad(m))*cos(2*rad(l))-0.5*y*y*sin(4*rad(l))-1.25*e*e*sin(2*rad(m)))
        return eq to dec
    }
    fun event(rise:Boolean):Double? {
        var minutes=720-4*city.longitude
        repeat(3) {
            val (eq,dec)=position(minutes)
            val cosine=(cos(rad(90.833))-sin(rad(city.latitude))*sin(dec))/(cos(rad(city.latitude))*cos(dec))
            if(!cosine.isFinite() || cosine !in -1.0..1.0) return null
            minutes=720-4*city.longitude-eq+(if(rise) -4 else 4)*deg(acos(cosine))
        }
        return minutes
    }
    val rise=event(true) ?: return null
    val set=event(false) ?: return null
    return SolarWindow(midnight.plusMillis((rise*60000).roundToLong()),midnight.plusMillis((set*60000).roundToLong()))
}
internal fun solarAppearanceDark(now:Instant,city:SolarCity):Boolean? {
    val zone=runCatching { ZoneId.of(city.zone) }.getOrNull() ?: return null
    val window=solarWindow(now.atZone(zone).toLocalDate(),city) ?: return null
    return now<window.sunrise || now>=window.sunset
}
internal data class AppearanceOptions(val mode:String="",val light:Int=420,val dark:Int=1200,val cityId:String="")
private fun appearancePrefs(context:Context)=context.applicationContext.getSharedPreferences("automatic_appearance",Context.MODE_PRIVATE)
private fun readAppearance(p:SharedPreferences)=AppearanceOptions(p.getString("mode","").orEmpty(),p.getInt("light",420),p.getInt("dark",1200),p.getString("city","").orEmpty())
@Composable private fun rememberAppearanceOptions():AppearanceOptions {
    val context=LocalContext.current
    val prefs=remember(context) { appearancePrefs(context) }
    var options by remember(prefs) { mutableStateOf(readAppearance(prefs)) }
    DisposableEffect(prefs) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,_ -> options=readAppearance(prefs) }
        prefs.registerOnSharedPreferenceChangeListener(listener); options=readAppearance(prefs)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return options
}
@Composable private fun rememberAppearanceNow():Instant {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    var millis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(context) {
        val receiver=object:BroadcastReceiver() {
            override fun onReceive(context:Context?,intent:Intent?) { millis=System.currentTimeMillis() }
        }
        val filter=IntentFilter().apply { addAction(Intent.ACTION_TIME_CHANGED);addAction(Intent.ACTION_TIMEZONE_CHANGED);addAction(Intent.ACTION_DATE_CHANGED) }
        ContextCompat.registerReceiver(context,receiver,filter,ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) { millis=System.currentTimeMillis();delay((60000L-millis%60000L).coerceAtLeast(1L)) }
        }
    }
    return Instant.ofEpochMilli(millis)
}
@Composable internal fun automaticAppearanceDark(manualDark:Boolean,systemDark:Boolean):Boolean {
    val options=rememberAppearanceOptions()
    if(options.mode!="scheduled" && options.mode!="solar") return manualDark
    val now=rememberAppearanceNow()
    return if(options.mode=="scheduled") {
        val local=now.atZone(ZoneId.systemDefault())
        scheduledAppearanceDark(local.hour*60+local.minute,options.light,options.dark) ?: systemDark
    } else solarCities.firstOrNull { it.id==options.cityId }?.let { solarAppearanceDark(now,it) } ?: systemDark
}
internal fun appearanceSearch(value:String)=Normalizer.normalize(value,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT)
@Composable internal fun AppearanceModeSettings(mode:AppThemeMode,onMode:(AppThemeMode)->Unit) {
    val context=LocalContext.current
    val prefs=remember(context) { appearancePrefs(context) }
    val options=rememberAppearanceOptions()
    val now=rememberAppearanceNow()
    var expanded by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<Boolean?>(null) }
    val labels=listOf(stringResource(R.string.theme_system),stringResource(R.string.theme_light),stringResource(R.string.theme_dark),stringResource(R.string.appearance_scheduled),stringResource(R.string.appearance_solar))
    val selected=when(options.mode) { "scheduled"->3;"solar"->4;else->when(mode) { AppThemeMode.SYSTEM->0;AppThemeMode.LIGHT->1;AppThemeMode.DARK->2 } }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("appearance_section_layout"),contentAlignment=Alignment.Center) {
        val controlWidth=minOf(280.dp,maxWidth*0.9f)
        Column(Modifier.width(controlWidth).testTag("appearance_controls"),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            val icons=listOf(Icons.Default.SettingsSuggest,Icons.Default.LightMode,Icons.Default.DarkMode,Icons.Default.Schedule,Icons.Default.WbTwilight)
            Box {
                OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth().testTag("appearance_mode_selector"),
                    shape=MaterialTheme.shapes.medium,
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
                    contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(24.dp),contentAlignment=Alignment.Center) { Icon(icons[selected],contentDescription=null,modifier=Modifier.size(20.dp)) }
                        Text(labels[selected],modifier=Modifier.weight(1f),maxLines=2)
                        Icon(Icons.Default.ArrowDropDown,contentDescription=null,modifier=Modifier.size(20.dp))
                    }
                }
                DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},
                    modifier=Modifier.width(controlWidth).heightIn(max=320.dp).testTag("appearance_mode_menu"),
                    shape=MaterialTheme.shapes.medium,containerColor=MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation=2.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                    labels.forEachIndexed { index,label ->
                        val chosen=selected==index
                        DropdownMenuItem(
                            modifier=Modifier.testTag("appearance_mode_option_$index").semantics { this.selected=chosen },
                            text={Text(label,modifier=Modifier.testTag("appearance_mode_label_$index"),maxLines=2)},
                            leadingIcon={Box(Modifier.size(24.dp).testTag("appearance_mode_icon_$index"),contentAlignment=Alignment.Center) {
                                Icon(icons[index],contentDescription=null,modifier=Modifier.size(20.dp),tint=if(chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }},
                            trailingIcon={Box(Modifier.size(24.dp),contentAlignment=Alignment.Center) {
                                Icon(Icons.Default.Check,contentDescription=null,modifier=Modifier.size(18.dp),tint=if(chosen) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                            }},onClick={
                                if(index<3) onMode(listOf(AppThemeMode.SYSTEM,AppThemeMode.LIGHT,AppThemeMode.DARK)[index])
                                prefs.edit().putString("mode",when(index) {3->"scheduled";4->"solar";else->""}).apply();expanded=false
                            })
                    }
                }
            }
            if(selected==3) {
                for(isLight in listOf(true,false)) {
                    val minutes=(if(isLight) options.light else options.dark).coerceIn(0,1439)
                    val label=stringResource(if(isLight) R.string.appearance_light_at else R.string.appearance_dark_at)
                    OutlinedButton(onClick={editingTime=isLight},modifier=Modifier.fillMaxWidth().testTag(if(isLight) "appearance_light_time" else "appearance_dark_time")) {
              Text(label+": "+String.format(Locale.ROOT,"%02d:%02d",minutes/60,minutes%60))
          }
                }
                Text(stringResource(R.string.appearance_schedule_note),style=MaterialTheme.typography.bodySmall)
            }
            if(selected==4) {
                val city=solarCities.firstOrNull { it.id==options.cityId }
                AppearanceCitySelector(city=city,maximumWidth=controlWidth,onSelect={chosen -> prefs.edit().putString("city",chosen.id).apply()})
                Text(stringResource(R.string.appearance_solar_note),style=MaterialTheme.typography.bodySmall)
                if(city==null) Text(stringResource(R.string.appearance_missing_city),style=MaterialTheme.typography.bodySmall)
                else {
                    val zone=ZoneId.of(city.zone)
                    val window=solarWindow(now.atZone(zone).toLocalDate(),city)
                    if(window==null) Text(stringResource(R.string.appearance_no_sun),style=MaterialTheme.typography.bodySmall)
                    else {
                        val fmt=DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
                        Text(stringResource(R.string.appearance_solar_times,fmt.format(window.sunrise),fmt.format(window.sunset),city.zone),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }

        }
    }
    if(selected==3) editingTime?.let { light ->
        key(light) {
            AppearanceAnalogTimeDialog(
                title=stringResource(if(light) R.string.appearance_light_at else R.string.appearance_dark_at),
                initialMinutes=if(light) options.light else options.dark,
                otherMinutes=if(light) options.dark else options.light,
                is24Hour=android.text.format.DateFormat.is24HourFormat(context),
                onDismiss={editingTime=null},onSave={value ->
                    val current=readAppearance(prefs)
                    val other=if(light) current.dark else current.light
                    if(current.mode=="scheduled" && appearanceTimeCanSave(value/60,value%60,other)) {
                        prefs.edit().putInt(if(light) "light" else "dark",value).apply();editingTime=null
                    }
                })
        }
    }

}
