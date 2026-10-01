package com.moalmaz.ibkar

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moalmaz.ibkar.data.DailyRecord
import com.moalmaz.ibkar.data.DateHelper
import com.moalmaz.ibkar.data.WorshipDatabase
import com.moalmaz.ibkar.ui.WorshipViewModel
import com.moalmaz.ibkar.ui.theme.MyApplicationTheme
import com.moalmaz.ibkar.ui.theme.SuccessGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.random.Random

data class CityLocation(val nameAr: String, val nameEn: String, val lat: Float, val lng: Float)
val egyptCities = listOf(
    CityLocation("القاهرة", "Cairo", 30.0444f, 31.2357f), CityLocation("الجيزة", "Giza", 30.0131f, 31.2089f),
    CityLocation("الإسكندرية", "Alexandria", 31.2001f, 29.9187f), CityLocation("القليوبية", "Qalyubia", 30.4667f, 31.1833f),
    CityLocation("البحيرة", "Beheira", 31.0333f, 30.4667f), CityLocation("مطروح", "Matrouh", 31.3525f, 27.2373f),
    CityLocation("الغربية", "Gharbia", 30.7865f, 31.0004f), CityLocation("المنوفية", "Monufia", 30.5522f, 31.0090f),
    CityLocation("كفر الشيخ", "Kafr El Sheikh", 31.1107f, 30.9388f), CityLocation("الدقهلية", "Dakahlia", 31.0364f, 31.3801f),
    CityLocation("الشرقية", "Sharqia", 30.5877f, 31.5020f), CityLocation("دمياط", "Damietta", 31.4165f, 31.8133f),
    CityLocation("بورسعيد", "Port Said", 31.2565f, 32.2841f), CityLocation("الإسماعيلية", "Ismailia", 30.6043f, 32.2723f),
    CityLocation("السويس", "Suez", 29.9668f, 32.5498f), CityLocation("شمال سيناء", "North Sinai", 31.1316f, 33.7984f),
    CityLocation("جنوب سيناء", "South Sinai", 28.2364f, 33.6254f), CityLocation("البحر الأحمر", "Red Sea", 27.2579f, 33.8116f),
    CityLocation("الفيوم", "Faiyum", 29.3084f, 30.8428f), CityLocation("بني سويف", "Beni Suef", 29.0661f, 31.0994f),
    CityLocation("المنيا", "Minya", 28.0871f, 30.7618f), CityLocation("أسيوط", "Asyut", 27.1810f, 31.1837f),
    CityLocation("سوهاج", "Sohag", 26.5570f, 31.6948f), CityLocation("قنا", "Qena", 26.1615f, 32.7181f),
    CityLocation("الأقصر", "Luxor", 25.6872f, 32.6396f), CityLocation("أسوان", "Aswan", 24.0889f, 32.8998f),
    CityLocation("الوادي الجديد", "New Valley", 25.4390f, 30.5586f)
)

fun getNearestCity(lat: Float, lng: Float): CityLocation {
    return egyptCities.minByOrNull { city ->
        val dLat = city.lat - lat
        val dLng = city.lng - lng
        (dLat * dLat) + (dLng * dLng)
    } ?: egyptCities[0]
}

fun updateLocationOffline(context: Context, onResult: (Boolean, String, Float, Float) -> Unit) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    try {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!isGpsEnabled && !isNetworkEnabled) {
            onResult(false, "الرجاء تفعيل خدمات الموقع (GPS)", 0f, 0f)
            return
        }
        val lastKnownGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val lastKnownNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val loc = lastKnownGps ?: lastKnownNetwork
        if (loc != null) {
            onResult(true, "تم التقاط الموقع بنجاح", loc.latitude.toFloat(), loc.longitude.toFloat())
        } else {
            onResult(false, "تعذر تحديد الموقع تلقائياً، يرجى اختياره يدوياً", 0f, 0f)
        }
    } catch (e: SecurityException) {
        onResult(false, "صلاحية الموقع غير ممنوحة", 0f, 0f)
    }
}

class MainActivity : ComponentActivity() {
    private var initialDhikrTypeState = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.moalmaz.ibkar.notification.PrayerNotificationManager.createNotificationChannel(this)
        handleIntent(intent)
        setContent {
            val context = LocalContext.current
            val themePrefs = remember(context) { context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE) }
            var useDarkTheme by rememberSaveable { mutableStateOf(themePrefs.getBoolean("dark_theme", true)) }
            var isArabic by rememberSaveable { mutableStateOf(themePrefs.getBoolean("is_arabic", true)) }
            MyApplicationTheme(darkTheme = useDarkTheme) {
                CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        MainAppContent(
                            darkTheme = useDarkTheme, isArabic = isArabic,
                            onToggleTheme = { val nv = !useDarkTheme; useDarkTheme = nv; themePrefs.edit().putBoolean("dark_theme", nv).apply() },
                            onToggleLanguage = { val nl = !isArabic; isArabic = nl; themePrefs.edit().putBoolean("is_arabic", nl).apply() },
                            initialDhikrType = initialDhikrTypeState.value,
                            onInitialDhikrHandled = { initialDhikrTypeState.value = null }
                        )
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    private fun handleIntent(intent: Intent?) {
        val type = intent?.getStringExtra("OPEN_DHIKR")
        if (type != null) initialDhikrTypeState.value = type
    }
    override fun onResume() {
        super.onResume()
        try {
            sendBroadcast(Intent(this, Class.forName("com.moalmaz.ibkar.widget.CountdownWidgetProvider")).apply { action = "com.moalmaz.ibkar.widget.REFRESH_COUNTDOWN" })
            sendBroadcast(Intent(this, Class.forName("com.moalmaz.ibkar.widget.PrayerTimesWidgetProvider")).apply { action = "com.moalmaz.ibkar.widget.REFRESH_TIMES" })
        } catch (e: Exception) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    darkTheme: Boolean, isArabic: Boolean, onToggleTheme: () -> Unit, onToggleLanguage: () -> Unit,
    viewModel: WorshipViewModel = viewModel(), initialDhikrType: String? = null, onInitialDhikrHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val record by viewModel.currentRecord.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val totalPoints by viewModel.totalPoints.collectAsStateWithLifecycle()
    val streak by viewModel.currentStreak.collectAsStateWithLifecycle()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showManualLocationDialog by remember { mutableStateOf(false) }
    var activeDhikrTypeForReading by remember { mutableStateOf<String?>(null) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var activeHisnCategory by remember { mutableStateOf<String?>(null) }

    val celebrationPrefs = remember(context) { context.getSharedPreferences("celebration_prefs", Context.MODE_PRIVATE) }
    var showDaily100Celebration by remember { mutableStateOf(false) }
    var showTotal100Celebration by remember { mutableStateOf(false) }
    var hasDismissedDailyCelebrationToday by remember { mutableStateOf(false) }
    var hasDismissedTotalCelebration by remember { mutableStateOf(false) }

    val dailyPoints = record?.calculatePoints() ?: 0

    LaunchedEffect(dailyPoints, record?.date) {
        val todayStr = record?.date ?: ""
        val actualToday = DateHelper.getTodayDateString(context)
        if (dailyPoints >= 100 && todayStr == actualToday && todayStr.isNotEmpty() && !hasDismissedDailyCelebrationToday) {
            val lastCelebrated = celebrationPrefs.getString("daily_100_last_date", "")
            if (todayStr != lastCelebrated) showDaily100Celebration = true
        }
    }

    LaunchedEffect(totalPoints) {
        if (totalPoints >= 100 && !hasDismissedTotalCelebration) {
            val alreadyCelebrated = celebrationPrefs.getBoolean("total_100_celebrated", false)
            if (!alreadyCelebrated) showTotal100Celebration = true
        }
    }

    val notificationSettingsPrefs = remember(context) { context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE) }
    var notifyAll by remember { mutableStateOf(notificationSettingsPrefs.getBoolean("notify_all", true)) }
    var notifyPrayers by remember { mutableStateOf(notificationSettingsPrefs.getBoolean("notify_prayers", true)) }
    var notifyMorningDhikr by remember { mutableStateOf(notificationSettingsPrefs.getBoolean("notify_morning_dhikr", true)) }
    var notifyEveningDhikr by remember { mutableStateOf(notificationSettingsPrefs.getBoolean("notify_evening_dhikr", true)) }
    var showNotificationDetailsDialog by remember { mutableStateOf(false) }

    var userLat by remember { mutableStateOf(notificationSettingsPrefs.getFloat("user_latitude", 30.0444f)) }
    var userLng by remember { mutableStateOf(notificationSettingsPrefs.getFloat("user_longitude", 31.2357f)) }
    var prayerCalcMethod by remember { mutableStateOf(notificationSettingsPrefs.getInt("prayer_calc_method", 0)) }
    var cityNameState by remember { mutableStateOf(if (isArabic) notificationSettingsPrefs.getString("user_city_name_ar", "القاهرة") ?: "القاهرة" else notificationSettingsPrefs.getString("user_city_name_en", "Cairo") ?: "Cairo") }

    LaunchedEffect(isArabic) {
        val lat = notificationSettingsPrefs.getFloat("user_latitude", 30.0444f)
        val lng = notificationSettingsPrefs.getFloat("user_longitude", 31.2357f)
        val nearest = getNearestCity(lat, lng)
        cityNameState = if (isArabic) nearest.nameAr else nearest.nameEn
    }

    val todayTimesRaw = remember(userLat, userLng, prayerCalcMethod) {
        val cal = com.moalmaz.ibkar.notification.PrayerTimeCalculator.getLocalCalendar(userLat.toDouble(), userLng.toDouble())
        com.moalmaz.ibkar.notification.PrayerTimeCalculator.calculatePrayerTimes(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), userLat.toDouble(), userLng.toDouble(), prayerCalcMethod)
    }
    var offsetMinutesVal by remember { mutableStateOf(notificationSettingsPrefs.getInt("prayer_offset_minutes", 0)) }
    val todayTimes = remember(todayTimesRaw) { todayTimesRaw }

    fun getPrayerTimeStr(key: String): String {
        val t = todayTimes[key] ?: return ""
        val h12 = if (t.first % 12 == 0) 12 else t.first % 12
        val amPm = if (t.first >= 12) { if (isArabic) "م" else "PM" } else { if (isArabic) "ص" else "AM" }
        return "%d:%02d %s".format(h12, t.second, amPm)
    }

    val todayStr = DateHelper.getTodayDateString(context)
    val displayDate = if (isArabic) DateHelper.getArabicDisplayDate(selectedDate) else selectedDate
    val isTodaySelected = selectedDate == todayStr
    var upcomingPrayerInfoState by remember(todayTimes) { mutableStateOf<UpcomingPrayerInfo?>(null) }

    LaunchedEffect(todayTimes, userLat, userLng, isTodaySelected, isArabic) {
        if (isTodaySelected) {
            while (true) {
                upcomingPrayerInfoState = getUpcomingPrayer(todayTimes, userLat.toDouble(), userLng.toDouble(), isArabic)
                delay(1000L)
            }
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            updateLocationOffline(context) { success, msg, newLat, newLng ->
                if (success) {
                    userLat = newLat
                    userLng = newLng
                    val nearest = getNearestCity(newLat, newLng)
                    cityNameState = if (isArabic) nearest.nameAr else nearest.nameEn
                    notificationSettingsPrefs.edit().putFloat("user_latitude", nearest.lat).putFloat("user_longitude", nearest.lng).putString("user_city_name_ar", nearest.nameAr).putString("user_city_name_en", nearest.nameEn).apply()
                    Toast.makeText(context, if (isArabic) "تم التحديث لـ ${nearest.nameAr}" else "Updated to ${nearest.nameEn}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, if (isArabic) msg else "Failed to locate", Toast.LENGTH_LONG).show()
                    showManualLocationDialog = true
                }
            }
        } else {
            Toast.makeText(context, if (isArabic) "تم رفض إذن الموقع." else "Location permission denied.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(initialDhikrType) {
        if (initialDhikrType != null) { activeDhikrTypeForReading = initialDhikrType; onInitialDhikrHandled() }
    }

    var hasNotifyPermission by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED } else true) }
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        hasNotifyPermission = isGranted
        if (isGranted) com.moalmaz.ibkar.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }
                    context.startActivity(intent)
                } catch (e: Exception) {}
            }
        }

        val hasCoarseLocation = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasCoarseLocation) { delay(800L); locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) } 
        else {
            updateLocationOffline(context) { success, _, newLat, newLng ->
                if (success) {
                    userLat = newLat; userLng = newLng
                    val nearest = getNearestCity(newLat, newLng)
                    cityNameState = if (isArabic) nearest.nameAr else nearest.nameEn
                    notificationSettingsPrefs.edit().putFloat("user_latitude", nearest.lat).putFloat("user_longitude", nearest.lng).putString("user_city_name_ar", nearest.nameAr).putString("user_city_name_en", nearest.nameEn).apply()
                }
            }
        }
        delay(1200L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotifyPermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else com.moalmaz.ibkar.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
        } else { com.moalmaz.ibkar.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }
    }

    val activeRecord = record ?: DailyRecord(date = selectedDate)
    val prayersDoneCount = listOf(activeRecord.fajrDone, activeRecord.dhuhrDone, activeRecord.asrDone, activeRecord.maghribDone, activeRecord.ishaDone).count { it }
    val isQuranDone = activeRecord.quranPages > 0
    val totalDoneItems = prayersDoneCount + (if (isQuranDone) 1 else 0) + (if (activeRecord.morningDhikrDone) 1 else 0) + (if (activeRecord.eveningDhikrDone) 1 else 0)

    Box(modifier = Modifier.fillMaxSize().background(brush = Brush.verticalGradient(colors = if (darkTheme) listOf(Color(0xFF0B0F19), Color(0xFF111827)) else listOf(Color(0xFFF4F6FA), Color(0xFFE8EDF4)))), contentAlignment = Alignment.TopCenter) {
        LazyColumn(modifier = Modifier.fillMaxWidth().widthIn(max = 660.dp).windowInsetsPadding(WindowInsets.safeDrawing), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = if (isArabic) "إِبْكَـار" else "Ibkar", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 24.sp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).clickable { showManualLocationDialog = true }.padding(horizontal = 8.dp, vertical = 5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                Text(text = cityNameState, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { showStatsDialog = true }, modifier = Modifier.size(36.dp).clip(CircleShape).background(if (darkTheme) Color(0xFF1E293B) else Color.White)) {
                            Icon(imageVector = Icons.Outlined.Info, contentDescription = "Stats", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(36.dp).clip(CircleShape).background(if (darkTheme) Color(0xFF1E293B) else Color.White)) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { viewModel.changeDate(-1) }) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Previous Day", modifier = Modifier.scale(if (isArabic) 1f else -1f)) }
                    Text(text = displayDate, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    IconButton(onClick = { viewModel.changeDate(1) }, enabled = selectedDate < todayStr) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Next Day", modifier = Modifier.scale(if (isArabic) 1f else -1f)) }
                }
            }

            item { MotivationHeaderCard(totalDoneItems = totalDoneItems, isQuranDone = isQuranDone, isFajrDone = activeRecord.fajrDone, isTodaySelected = isTodaySelected, darkTheme = darkTheme, isArabic = isArabic) }

            if (isTodaySelected && upcomingPrayerInfoState != null) {
                item { NextPrayerCountdownCard(upcoming = upcomingPrayerInfoState!!, darkTheme = darkTheme, isArabic = isArabic) }
            }

            item {
                Text(text = if (isArabic) "الصلوات المفروضة" else "Obligatory Prayers", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
            item { PrayerItemRow(name = if (isArabic) "الفجر" else "Fajr", description = if (isArabic) "ركعتان" else "2 Rak'ahs", isDone = activeRecord.fajrDone, tag = "fajr", isArabic = isArabic, darkTheme = darkTheme, timeText = getPrayerTimeStr("fajr")) { if (isTodaySelected) viewModel.togglePrayer("fajr") } }
            item { PrayerItemRow(name = if (isArabic) "الظهر" else "Dhuhr", description = if (isArabic) "أربع ركعات" else "4 Rak'ahs", isDone = activeRecord.dhuhrDone, tag = "dhuhr", isArabic = isArabic, darkTheme = darkTheme, timeText = getPrayerTimeStr("dhuhr")) { if (isTodaySelected) viewModel.togglePrayer("dhuhr") } }
            item { PrayerItemRow(name = if (isArabic) "العصر" else "Asr", description = if (isArabic) "أربع ركعات" else "4 Rak'ahs", isDone = activeRecord.asrDone, tag = "asr", isArabic = isArabic, darkTheme = darkTheme, timeText = getPrayerTimeStr("asr")) { if (isTodaySelected) viewModel.togglePrayer("asr") } }
            item { PrayerItemRow(name = if (isArabic) "المغرب" else "Maghrib", description = if (isArabic) "ثلاث ركعات" else "3 Rak'ahs", isDone = activeRecord.maghribDone, tag = "maghrib", isArabic = isArabic, darkTheme = darkTheme, timeText = getPrayerTimeStr("maghrib")) { if (isTodaySelected) viewModel.togglePrayer("maghrib") } }
            item { PrayerItemRow(name = if (isArabic) "العشاء" else "Isha", description = if (isArabic) "أربع ركعات" else "4 Rak'ahs", isDone = activeRecord.ishaDone, tag = "isha", isArabic = isArabic, darkTheme = darkTheme, timeText = getPrayerTimeStr("isha")) { if (isTodaySelected) viewModel.togglePrayer("isha") } }

            item {
                Text(text = if (isArabic) "السنن والعبادات الإضافية" else "Sunnah & Extra Worships", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).border(1.dp, if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0), RoundedCornerShape(16.dp)).clickable { activeDhikrTypeForReading = "morning" }, colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF111827) else Color.White)) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Column(modifier = Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFFDE68A).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Text("🌅", fontSize = 18.sp) }
                                Text(text = if (isArabic) "أذكار الصباح" else "Morning Dhikr", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            }
                            IconButton(onClick = { if (isTodaySelected) viewModel.toggleMorningDhikr() }, modifier = Modifier.align(Alignment.BottomEnd).size(36.dp)) {
                                if (activeRecord.morningDhikrDone) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(28.dp))
                                else Canvas(modifier = Modifier.size(24.dp)) { drawCircle(color = if (darkTheme) Color.Gray else Color.LightGray, style = Stroke(width = 3f)) }
                            }
                        }
                    }

                    Card(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).border(1.dp, if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0), RoundedCornerShape(16.dp)).clickable { activeDhikrTypeForReading = "evening" }, colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF111827) else Color.White)) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Column(modifier = Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFC7D2FE).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { CrescentMoonIcon(modifier = Modifier.size(16.dp), color = Color(0xFF818CF8)) }
                                Text(text = if (isArabic) "أذكار المساء" else "Evening Dhikr", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            }
                            IconButton(onClick = { if (isTodaySelected) viewModel.toggleEveningDhikr() }, modifier = Modifier.align(Alignment.BottomEnd).size(36.dp)) {
                                if (activeRecord.eveningDhikrDone) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(28.dp))
                                else Canvas(modifier = Modifier.size(24.dp)) { drawCircle(color = if (darkTheme) Color.Gray else Color.LightGray, style = Stroke(width = 3f)) }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0), RoundedCornerShape(16.dp)), colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF111827) else Color.White)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF10B981).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Text("📖", fontSize = 20.sp) }
                                Column {
                                    Text(text = if (isArabic) "ورد القرآن الكريم" else "Quran Wird", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(text = if (isArabic) "الصفحات المقروءة: ${activeRecord.quranPages}" else "Pages read: ${activeRecord.quranPages}", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)))
                                }
                            }
                            if (activeRecord.quranPages > 0) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(modifier = Modifier.fillMaxWidth().background(if (darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if (isTodaySelected && activeRecord.quranPages > 0) viewModel.setQuranPages(activeRecord.quranPages - 1) }) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
                                Text(text = "${activeRecord.quranPages}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                                IconButton(onClick = { if (isTodaySelected) viewModel.setQuranPages(activeRecord.quranPages + 1) }) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) }
                            }
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { activeHisnCategory = "categories" }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = if (darkTheme) Color(0xFF1E293B) else Color.White, contentColor = MaterialTheme.colorScheme.onSurface), border = BorderStroke(1.dp, if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)), elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🛡️", fontSize = 18.sp)
                            Text(text = if (isArabic) "حصن المسلم" else "Hisn Al-Muslim", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(onClick = { activeHisnCategory = "tasbeeh" }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = if (darkTheme) Color(0xFF1E293B) else Color.White, contentColor = MaterialTheme.colorScheme.onSurface), border = BorderStroke(1.dp, if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)), elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📿", fontSize = 18.sp)
                            Text(text = if (isArabic) "التسبيح" else "Tasbeeh", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (activeDhikrTypeForReading != null) {
        DhikrReadingFlow(type = activeDhikrTypeForReading!!, darkTheme = darkTheme, isArabic = isArabic, onDismiss = { activeDhikrTypeForReading = null }) {
            if (activeDhikrTypeForReading == "morning" && !activeRecord.morningDhikrDone) viewModel.toggleMorningDhikr()
            else if (activeDhikrTypeForReading == "evening" && !activeRecord.eveningDhikrDone) viewModel.toggleEveningDhikr()
            activeDhikrTypeForReading = null
        }
    }

    if (showDaily100Celebration) {
        WorshipCelebrationDialog(title = if (isArabic) "إنجاز عظيم! 🌟" else "Great Achievement! 🌟", description = if (isArabic) "ما شاء الله! أتممت جميع عباداتك لهذا اليوم بنسبة 100%. استمر على هذا الدرب المنير." else "Masha'Allah! You've completed 100% of your daily worships. Keep shining on this luminous path.", darkTheme = darkTheme, isArabic = isArabic) {
            hasDismissedDailyCelebrationToday = true
            showDaily100Celebration = false
            celebrationPrefs.edit().putString("daily_100_last_date", record?.date ?: "").apply()
        }
    }

    if (showTotal100Celebration) {
        WorshipCelebrationDialog(title = if (isArabic) "مبارك! وصلت لـ 100 نقطة إجمالية 🎉" else "Congratulations! 100 Total Points 🎉", description = if (isArabic) "ثباتك على الطاعة يثمر! لقد جمعت 100 نقطة في رصيدك التراكمي. هذا دليل على همتك العالية." else "Your consistency bears fruit! You've accumulated 100 points. This is proof of your high resolve.", darkTheme = darkTheme, isArabic = isArabic) {
            hasDismissedTotalCelebration = true
            showTotal100Celebration = false
            celebrationPrefs.edit().putBoolean("total_100_celebrated", true).apply()
        }
    }

    if (showStatsDialog) {
        StatsScreen(darkTheme = darkTheme, isArabic = isArabic, onDismiss = { showStatsDialog = false })
    }

    if (activeHisnCategory == "categories") {
        AlertDialog(
            onDismissRequest = { activeHisnCategory = null },
            title = { Text(if (isArabic) "أقسام حصن المسلم" else "Hisn Al-Muslim Categories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val cats = listOf("sleep" to (if (isArabic) "أذكار النوم" else "Sleep"), "wakeup" to (if (isArabic) "أذكار الاستيقاظ" else "Waking up"), "food" to (if (isArabic) "الطعام والشراب" else "Food & Drink"), "travel" to (if (isArabic) "الركوب والسفر" else "Travel"), "home" to (if (isArabic) "المنزل" else "Home"), "mosque" to (if (isArabic) "المسجد" else "Mosque"), "toilet" to (if (isArabic) "الخلاء" else "Toilet"), "rain" to (if (isArabic) "المطر والرياح" else "Rain & Wind"))
                    items(cats) { cat ->
                        TextButton(onClick = { activeHisnCategory = cat.first }, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(cat.second, color = MaterialTheme.colorScheme.onSurface)
                                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { activeHisnCategory = null }) { Text(if (isArabic) "إغلاق" else "Close") } },
            containerColor = if (darkTheme) Color(0xFF0F172A) else Color.White
        )
    } else if (activeHisnCategory == "tasbeeh") {
        AlertDialog(
            onDismissRequest = { activeHisnCategory = null },
            title = { Text(if (isArabic) "المسبحة الإلكترونية" else "Digital Tasbeeh", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.size(160.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.incrementDhikr() }, contentAlignment = Alignment.Center) {
                        Text("${activeRecord.dhikrCount}", fontSize = 56.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.resetDhikr() }) { Text(if (isArabic) "تصفير العداد ↺" else "Reset Counter ↺") }
                }
            },
            confirmButton = { Button(onClick = { activeHisnCategory = null }, modifier = Modifier.fillMaxWidth()) { Text(if (isArabic) "إغلاق" else "Close") } },
            containerColor = if (darkTheme) Color(0xFF0F172A) else Color.White
        )
    } else if (activeHisnCategory != null) {
        HisnAlMuslimDialog(category = activeHisnCategory!!, darkTheme = darkTheme, isArabic = isArabic, onDismiss = { activeHisnCategory = null })
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text(if (isArabic) "الإعدادات ⚙️" else "Settings ⚙️", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().clickable { showEditNameDialog = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(if (isArabic) "تغيير الاسم" else "Change Name", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(profile.name.ifEmpty { if (isArabic) "ضيف" else "Guest" }, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary))
                        }
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    HorizontalDivider(color = if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isArabic) "الوضع الداكن" else "Dark Mode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Switch(checked = darkTheme, onCheckedChange = { onToggleTheme() }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha=0.5f)))
                    }
                    HorizontalDivider(color = if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isArabic) "اللغة (عربي/English)" else "Language (Ar/En)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Switch(checked = !isArabic, onCheckedChange = { onToggleLanguage() }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha=0.5f)))
                    }
                    HorizontalDivider(color = if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isArabic) "الإشعارات" else "Notifications", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        TextButton(onClick = { showNotificationDetailsDialog = true }) { Text(if (isArabic) "تخصيص" else "Customize") }
                    }
                    HorizontalDivider(color = if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isArabic) "طريقة حساب المواقيت" else "Calculation Method", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        var showMethodDropdown by remember { mutableStateOf(false) }
                        val methods = if (isArabic) listOf("أم القرى", "رابطة العالم الإسلامي", "الهيئة العامة المصرية", "جامعة العلوم الإسلامية بكراتشي", "الجمعية الإسلامية لأمريكا الشمالية") else listOf("Umm Al-Qura", "Muslim World League", "Egyptian General Authority", "University of Islamic Sciences, Karachi", "Islamic Society of North America")
                        val methodValues = listOf(1, 2, 5, 4, 3)
                        Box {
                            TextButton(onClick = { showMethodDropdown = true }) { Text(methods.getOrElse(methodValues.indexOf(prayerCalcMethod)) { if (isArabic) "تغيير" else "Change" }, fontSize = 12.sp, maxLines = 1) }
                            DropdownMenu(expanded = showMethodDropdown, onDismissRequest = { showMethodDropdown = false }) {
                                methods.forEachIndexed { index, name ->
                                    DropdownMenuItem(text = { Text(name) }, onClick = { prayerCalcMethod = methodValues[index]; notificationSettingsPrefs.edit().putInt("prayer_calc_method", methodValues[index]).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context); showMethodDropdown = false })
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                    Row(modifier = Modifier.fillMaxWidth().clickable { showManualLocationDialog = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(if (isArabic) "تحديد الموقع يدوياً" else "Manual Location", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(cityNameState, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary))
                        }
                        Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    HorizontalDivider(color = if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isArabic) "إِبْكَـار - صُنع بكل حب بواسطة مصطفى الماظ" else "Ibkar - Made with love by Mostafa Almaz", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ibkar.vercel.app"))) } catch (e: Exception) {} }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)) { Text(if (isArabic) "الموقع الإلكتروني" else "Website") }
                            Button(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/ibkar.application"))) } catch (e: Exception) {} }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2).copy(alpha = 0.1f), contentColor = Color(0xFF1877F2))) { Text("Facebook") }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showSettingsDialog = false }) { Text(if (isArabic) "إغلاق" else "Close") } },
            containerColor = if (darkTheme) Color(0xFF0F172A) else Color.White
        )
    }

    if (showNotificationDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationDetailsDialog = false },
            title = { Text(if (isArabic) "تخصيص الإشعارات" else "Customize Notifications", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isArabic) "تفعيل كل الإشعارات" else "Enable All Notifications", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Switch(checked = notifyAll, onCheckedChange = { 
                            notifyAll = it; notifyPrayers = it; notifyMorningDhikr = it; notifyEveningDhikr = it
                            notificationSettingsPrefs.edit().putBoolean("notify_all", it).putBoolean("notify_prayers", it).putBoolean("notify_morning_dhikr", it).putBoolean("notify_evening_dhikr", it).apply()
                            com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
                        }, colors = SwitchDefaults.colors(checkedThumbColor = SuccessGreen, checkedTrackColor = SuccessGreen.copy(alpha=0.5f)))
                    }
                    HorizontalDivider(color = if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isArabic) "تنبيهات الصلوات" else "Prayer Alerts", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = notifyPrayers, onCheckedChange = { notifyPrayers = it; notificationSettingsPrefs.edit().putBoolean("notify_prayers", it).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, enabled = notifyAll)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isArabic) "تذكير أذكار الصباح (بعد الفجر)" else "Morning Dhikr Reminder", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = notifyMorningDhikr, onCheckedChange = { notifyMorningDhikr = it; notificationSettingsPrefs.edit().putBoolean("notify_morning_dhikr", it).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, enabled = notifyAll)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isArabic) "تذكير أذكار المساء (بعد العصر)" else "Evening Dhikr Reminder", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = notifyEveningDhikr, onCheckedChange = { notifyEveningDhikr = it; notificationSettingsPrefs.edit().putBoolean("notify_evening_dhikr", it).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, enabled = notifyAll)
                    }
                    HorizontalDivider(color = if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isArabic) "تعديل وقت الأذان (دقائق)" else "Adjust Adhan Time (Mins)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { offsetMinutesVal--; notificationSettingsPrefs.edit().putInt("prayer_offset_minutes", offsetMinutesVal).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                                Text("$offsetMinutesVal", style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { offsetMinutesVal++; notificationSettingsPrefs.edit().putInt("prayer_offset_minutes", offsetMinutesVal).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                            }
                        }
                    }
                    Text(if (isArabic) "إذا كان الأذان يسبق مساحد مدينتك أو يتأخر عنها، قم بضبط الفارق بالدقائق هنا." else "Adjust if adhan differs from your local mosque.", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray))
                }
            },
            confirmButton = { Button(onClick = { showNotificationDetailsDialog = false }) { Text(if (isArabic) "إغلاق" else "Close") } },
            containerColor = if (darkTheme) Color(0xFF0F172A) else Color.White
        )
    }

    if (showManualLocationDialog) {
        AlertDialog(
            onDismissRequest = { showManualLocationDialog = false },
            title = { Text(if (isArabic) "اختر المحافظة / المدينة" else "Choose City", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(egyptCities) { city ->
                        TextButton(
                            onClick = {
                                userLat = city.lat
                                userLng = city.lng
                                cityNameState = if (isArabic) city.nameAr else city.nameEn
                                notificationSettingsPrefs.edit().putFloat("user_latitude", city.lat).putFloat("user_longitude", city.lng).putString("user_city_name_ar", city.nameAr).putString("user_city_name_en", city.nameEn).apply()
                                com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
                                Toast.makeText(context, if (isArabic) "تم التحديث لـ ${city.nameAr}" else "Updated to ${city.nameEn}", Toast.LENGTH_SHORT).show()
                                showManualLocationDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isArabic) city.nameAr else city.nameEn, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showManualLocationDialog = false }) { Text(if (isArabic) "إغلاق" else "Close") } },
            containerColor = if (darkTheme) Color(0xFF0F172A) else Color.White
        )
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text(if (isArabic) "كيف تحب أن نناديك؟" else "What should we call you?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text(if (isArabic) "الاسم" else "Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.updateUserName(inputName.trim()); showEditNameDialog = false }) { Text(if (isArabic) "حفظ" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text(if (isArabic) "إلغاء" else "Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
            },
            containerColor = if (darkTheme) Color(0xFF0F172A) else Color.White
        )
    }
}

@Composable
fun CrescentMoonIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.75f, h * 0.15f)
            quadraticTo(w * 0.05f, h * 0.5f, w * 0.75f, h * 0.85f)
            quadraticTo(w * 0.35f, h * 0.5f, w * 0.75f, h * 0.15f)
            close()
        }
        drawPath(path = path, color = color)
    }
}

data class UpcomingPrayerInfo(val tag: String, val name: String, val timeStr: String, val diffMinutes: Int, val diffSeconds: Int)

fun getUpcomingPrayer(todayTimes: Map<String, Pair<Int, Int>>, latitude: Double, longitude: Double, isArabic: Boolean): UpcomingPrayerInfo? {
    val now = com.example.notification.PrayerTimeCalculator.getLocalCalendar(latitude, longitude)
    val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val currentSeconds = now.get(Calendar.SECOND)
    val currentSecsFromMidnight = currentMinutes * 60 + currentSeconds

    val prayerList = if (isArabic) listOf("fajr" to "الفجر", "dhuhr" to "الظهر", "asr" to "العصر", "maghrib" to "المغرب", "isha" to "العشاء")
    else listOf("fajr" to "Fajr", "dhuhr" to "Dhuhr", "asr" to "Asr", "maghrib" to "Maghrib", "isha" to "Isha")

    for (p in prayerList) {
        val t = todayTimes[p.first]
        if (t != null) {
            val pMinutes = t.first * 60 + t.second
            val prayerSecsFromMidnight = pMinutes * 60
            if (prayerSecsFromMidnight > currentSecsFromMidnight) {
                val remainingSeconds = prayerSecsFromMidnight - currentSecsFromMidnight
                val diffMin = (remainingSeconds / 60).toInt()
                val diffSec = (remainingSeconds % 60).toInt()
                val h12 = if (t.first % 12 == 0) 12 else t.first % 12
                val amPm = if (t.first >= 12) { if (isArabic) "م" else "PM" } else { if (isArabic) "ص" else "AM" }
                return UpcomingPrayerInfo(p.first, p.second, "%d:%02d %s".format(h12, t.second, amPm), diffMin, diffSec)
            }
        }
    }

    val t = todayTimes["fajr"]
    if (t != null) {
        val pMinutes = t.first * 60 + t.second
        val prayerSecsFromMidnight = (pMinutes + 24 * 60) * 60
        val remainingSeconds = prayerSecsFromMidnight - currentSecsFromMidnight
        val diffMin = (remainingSeconds / 60).toInt()
        val diffSec = (remainingSeconds % 60).toInt()
        val h12 = if (t.first % 12 == 0) 12 else t.first % 12
        val amPm = if (isArabic) "ص" else "AM"
        return UpcomingPrayerInfo("fajr", if (isArabic) "فجر الغد" else "Tomorrow's Fajr", "%d:%02d %s".format(h12, t.second, amPm), diffMin, diffSec)
    }
    return null
}

fun getPrayerSkyGradient(tag: String, isDark: Boolean): List<Color> {
    return if (isDark) {
        when (tag) {
            "fajr" -> listOf(Color(0xFF0F1E36), Color(0xFF1D3557))
            "dhuhr" -> listOf(Color(0xFF4D342F), Color(0xFF3E2723))
            "asr" -> listOf(Color(0xFF37474F), Color(0xFF263238))
            "maghrib" -> listOf(Color(0xFF4A148C), Color(0xFF311B92))
            "isha" -> listOf(Color(0xFF0D1B2A), Color(0xFF1B263B))
            else -> listOf(Color(0xFF1E293B), Color(0xFF0F172A))
        }
    } else {
        when (tag) {
            "fajr" -> listOf(Color(0xFFF3ECE0), Color(0xFFFFCC80))
            "dhuhr" -> listOf(Color(0xFFE8F5E9), Color(0xFFA5D6A7))
            "asr" -> listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2))
            "maghrib" -> listOf(Color(0xFFFFF0F5), Color(0xFFFFB6C1))
            "isha" -> listOf(Color(0xFFE8EAF6), Color(0xFFC5CAE9))
            else -> listOf(Color(0xFFF4F6FA), Color(0xFFEBF1FA))
        }
    }
}

@Composable
fun NextPrayerCountdownCard(upcoming: UpcomingPrayerInfo, darkTheme: Boolean, isArabic: Boolean) {
    val isDark = darkTheme
    val skyGradient = getPrayerSkyGradient(upcoming.tag, isDark)
    val h = upcoming.diffMinutes / 60
    val m = upcoming.diffMinutes % 60
    val s = upcoming.diffSeconds
    val countdownFormatted = if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    val countdownLabel = if (h > 0) { if (isArabic) "ساعة ودقيقة وثانية" else "Hr : Min : Sec" } else { if (isArabic) "دقيقة وثانية" else "Min : Sec" }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(modifier = Modifier.fillMaxWidth().background(brush = Brush.linearGradient(colors = skyGradient)).padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(text = if (isArabic) "الوقت المتبقي للأذان:" else "Time until Adhan:", style = MaterialTheme.typography.labelSmall.copy(color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.7f), fontWeight = FontWeight.Bold))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(text = countdownFormatted, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 24.sp, color = if (isDark) Color(0xFFFFD54F) else Color(0xFF065F46)))
                    }
                    Text(text = countdownLabel, style = MaterialTheme.typography.labelSmall.copy(color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.45f), fontSize = 9.sp))
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = if (isArabic) "الصلاة القادمة" else "Next Prayer", style = MaterialTheme.typography.labelSmall.copy(color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.7f), fontWeight = FontWeight.Bold))
                    Text(text = upcoming.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = if (isDark) Color.White else Color(0xFF111318)))
                    Box(modifier = Modifier.clip(RoundedCornerShape(50.dp)).background((if (isDark) Color.White else Color.Black).copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(text = if (isArabic) "الأذان: ${upcoming.timeStr}" else "Adhan: ${upcoming.timeStr}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color.Black))
                    }
                }
            }
        }
    }
}

@Composable
fun PrayerCustomIcon(tag: String, isDone: Boolean, modifier: Modifier = Modifier) {
    val emoji = when (tag) { "fajr" -> "🌅"; "dhuhr" -> "☀️"; "asr" -> "🌤️"; "maghrib" -> "🌇"; "isha" -> "🌙"; else -> "🕌" }
    Box(modifier = modifier.fillMaxSize().background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Text(text = emoji, fontSize = 20.sp) }
}

@Composable
fun PrayerItemRow(name: String, description: String, isDone: Boolean, tag: String, isArabic: Boolean, darkTheme: Boolean, timeText: String? = null, onToggle: () -> Unit) {
    val isDark = darkTheme
    val skyGradient = remember(tag, isDark) { getPrayerSkyGradient(tag, isDark) }
    val bgBrush = remember(skyGradient, isDone, isDark) { Brush.horizontalGradient(colors = if (isDone) { if (isDark) listOf(Color(0xFF064E3B), Color(0xFF022C22)) else listOf(Color(0xFFD1FAE5), Color(0xFFA7F3D0)) } else { if (isDark) listOf(skyGradient[0].copy(alpha = 0.18f), skyGradient[1].copy(alpha = 0.08f)) else listOf(skyGradient[0].copy(alpha = 0.65f), skyGradient[1].copy(alpha = 0.35f)) }) }
    val borderStrokeColor by animateColorAsState(targetValue = if (isDone) Color(0xFF10B981).copy(alpha = 0.6f) else { if (isDark) Color(0xFF1E293B) else Color(0xFFECEFF1) }, animationSpec = spring(), label = "")
    val rightAccentBarColor = if (isDone) SuccessGreen else { if (isDark) Color(0xFF10B981) else Color(0xFFCFD8DC) }

    Card(modifier = Modifier.fillMaxWidth().testTag("prayer_card_$tag").clip(RoundedCornerShape(14.dp)).border(1.dp, borderStrokeColor, RoundedCornerShape(14.dp)).clickable { onToggle() }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 1.dp else 0.dp)) {
        Box(modifier = Modifier.fillMaxWidth().background(if (isDark) Color.Transparent else Color.White).background(brush = bgBrush)) {
            Box(modifier = Modifier.width(4.dp).height(54.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 0.dp, bottomEnd = 0.dp)).background(rightAccentBarColor))
            Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(if (isDone) SuccessGreen else Color.Transparent).border(width = 2.dp, color = if (isDone) SuccessGreen else (if (isDark) Color(0xFF5A6270) else Color(0xFFB0BEC5)), shape = CircleShape), contentAlignment = Alignment.Center) {
                    if (isDone) Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (isDone) { if (isDark) Color(0xFFD1FAE5) else Color(0xFF065F46) } else MaterialTheme.colorScheme.onSurface))
                            if (isDone) Box(modifier = Modifier.clip(RoundedCornerShape(50.dp)).background(if (isDark) Color(0xFF065F46).copy(alpha = 0.3f) else Color(0xFFD1FAE5)).padding(horizontal = 6.dp, vertical = 1.dp)) { Text(text = if (isArabic) "مؤداة" else "Done", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF34D399) else Color(0xFF059669), fontSize = 9.sp)) }
                        }
                        if (timeText != null) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(text = timeText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black, color = if (isDone) { if (isDark) Color(0xFF34D399) else Color(0xFF059669) } else { if (isDark) Color(0xFFFFD54F) else Color(0xFF065F46) }))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = description, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), fontSize = 10.5.sp, lineHeight = 14.sp), maxLines = 1)
                }
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).border(width = 1.2.dp, color = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.08f), shape = RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    PrayerCustomIcon(tag = tag, isDone = isDone, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

data class MotivationHeaderData(val title: String, val text: String, val icon: String, val badgeColor: Color)

@Composable
fun MotivationHeaderCard(totalDoneItems: Int, isQuranDone: Boolean, isFajrDone: Boolean, isTodaySelected: Boolean, darkTheme: Boolean = true, isArabic: Boolean) {
    val motivation = when {
        totalDoneItems == 8 -> MotivationHeaderData(if (isArabic) "هنيئاً لك التمام والكمال!" else "Congratulations on Perfection!", if (isArabic) "أتممت عباداتك اليومية كاملة، جعلك الله من أهل الفردوس الأعلى." else "You have completed all daily worships. May Allah grant you Paradise.", "👑", Color(0xFFFFD700))
        totalDoneItems >= 5 -> MotivationHeaderData(if (isArabic) "همة عالية وخطى ثابتة" else "High Resolve & Steady Steps", if (isArabic) "أنجزت معظم فرائض وسنن اليوم، واصل حتى تختم يومك بتمام الأجر." else "You have accomplished most of today's worships. Keep it up!", "🌟", Color(0xFF34D399))
        !isFajrDone && isTodaySelected -> MotivationHeaderData(if (isArabic) "انطلاقة اليوم تبدأ بالفجر" else "The Day Starts with Fajr", if (isArabic) "ركعتا الفجر خير من الدنيا وما فيها، ابدأ يومك بنور الصلاة وذكر الله." else "The two Rak'ahs of Fajr are better than the world and everything in it.", "🌅", Color(0xFFFFB74D))
        else -> MotivationHeaderData(if (isArabic) "يوم جديد.. وباب أجر مفتوح" else "A New Day, A New Reward", if (isArabic) "استعن بالله وحافظ على صلواتك في وقتها لتنال بركة يومك وحفظه." else "Seek help from Allah and maintain your prayers to attain blessings.", "🌿", Color(0xFF10B981))
    }
    Card(modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)).border(width = 1.dp, color = if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0), shape = RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF111827) else Color(0xFFFFFFFF))) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(motivation.badgeColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Text(text = motivation.icon, fontSize = 24.sp) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = motivation.title, style = MaterialTheme.typography.titleMedium, color = if (darkTheme) Color.White else Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                Text(text = motivation.text, style = MaterialTheme.typography.bodySmall, color = if (darkTheme) Color(0xFF94A3B8) else Color(0xFF475569))
            }
        }
    }
}

@Composable
fun DhikrReadingFlow(type: String, darkTheme: Boolean = true, isArabic: Boolean = true, onDismiss: () -> Unit, onComplete: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val athkarList = if (type == "morning") morningAdhkarList else eveningAdhkarList
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("dhikr_flow_prefs", Context.MODE_PRIVATE) }
    val todayDateStr = remember { DateHelper.getTodayDateString(context) }
    val savedDateKey = "dhikr_${type}_date"
    val savedIndexKey = "dhikr_${type}_index"
    val savedCountKey = "dhikr_${type}_count"
    val lastDate = sharedPrefs.getString(savedDateKey, "")
    val isNewDay = lastDate != todayDateStr

    if (isNewDay) {
        sharedPrefs.edit().putString(savedDateKey, todayDateStr).putInt(savedIndexKey, 0).putInt(savedCountKey, athkarList[0].count).apply()
    }

    var currentIndex by remember { mutableStateOf(if (isNewDay) 0 else sharedPrefs.getInt(savedIndexKey, 0)) }
    val totalCount = athkarList.size

    val currentCountsLeft = remember(type) {
        mutableStateListOf<Int>().apply {
            val savedIndex = if (isNewDay) 0 else sharedPrefs.getInt(savedIndexKey, 0)
            for (i in 0 until totalCount) {
                if (i < savedIndex) add(0)
                else if (i == savedIndex) {
                    val defaultCount = athkarList[i].count
                    val savedLeft = if (isNewDay) defaultCount else sharedPrefs.getInt(savedCountKey, defaultCount)
                    if (savedLeft in 1..defaultCount) add(savedLeft) else add(defaultCount)
                } else add(athkarList[i].count)
            }
        }
    }

    fun saveProgress(index: Int, countLeft: Int) {
        sharedPrefs.edit().putString(savedDateKey, todayDateStr).putInt(savedIndexKey, index).putInt(savedCountKey, countLeft).apply()
    }

    val currentDhikr = athkarList.getOrNull(currentIndex)
    val curCountLeft = currentCountsLeft.getOrNull(currentIndex) ?: 0
    var isFinished by remember { mutableStateOf(currentIndex >= totalCount) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        val backgroundColor = if (darkTheme) Color(0xFF0B0F19) else Color(0xFFF8FAFC)
        val cardColor = if (darkTheme) Color(0xFF111827) else Color(0xFFFFFFFF)
        val textColor = if (darkTheme) Color.White else Color(0xFF0F172A)
        val brandColor = if (type == "morning") Color(0xFF10B981) else Color(0xFF3B82F6)

        CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Box(modifier = Modifier.fillMaxSize().background(backgroundColor).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
                Column(modifier = Modifier.fillMaxHeight().widthIn(max = 660.dp).padding(20.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onDismiss() }, modifier = Modifier.clip(CircleShape).background(if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = if (isArabic) "إغلاق" else "Close", tint = if (darkTheme) Color.White else Color(0xFF475569))
                        }
                        Text(text = if (type == "morning") { if (isArabic) "أذكار الصباح" else "Morning Dhikr" } else { if (isArabic) "أذكار المساء" else "Evening Dhikr" }, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = brandColor), textAlign = TextAlign.Center)
                        IconButton(onClick = { currentIndex = 0; isFinished = false; saveProgress(0, athkarList[0].count); currentCountsLeft.clear(); currentCountsLeft.addAll(athkarList.map { it.count }) }, modifier = Modifier.clip(CircleShape).background(if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = if (isArabic) "إعادة البدء" else "Restart", tint = if (darkTheme) Color.White else Color(0xFF475569))
                        }
                    }

                    if (!isFinished && currentDhikr != null) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = if (isArabic) "الذكر ${currentIndex + 1} من $totalCount" else "Dhikr ${currentIndex + 1} of $totalCount", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, color = if (darkTheme) Color.LightGray else Color(0xFF64748B)))
                                val percent = (((currentIndex + 1).toFloat() / totalCount) * 100).toInt()
                                Text(text = "$percent%", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = brandColor))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (i in 0 until totalCount) {
                                    val segmentColor = when { i < currentIndex -> SuccessGreen; i == currentIndex -> brandColor; else -> if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0) }
                                    Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(segmentColor))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardColor).border(1.dp, if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0), RoundedCornerShape(24.dp)).padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(text = currentDhikr.text, style = MaterialTheme.typography.titleLarge.copy(lineHeight = 36.sp, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = textColor), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    if (!isArabic && currentDhikr.translation.isNotEmpty()) {
                                        Text(text = currentDhikr.translation, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = if (darkTheme) Color(0xFF94A3B8) else Color(0xFF475569)), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                if (currentDhikr.benefit.isNotEmpty()) {
                                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                                        Text(text = if (isArabic) "الفضل: ${currentDhikr.benefit}" else "Benefit: ${currentDhikr.benefit}", style = MaterialTheme.typography.bodySmall.copy(color = if (darkTheme) Color(0xFF94A3B8) else Color(0xFF475569), lineHeight = 16.sp), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier.size(115.dp).clip(CircleShape).background(Brush.radialGradient(colors = listOf(brandColor, brandColor.copy(alpha = 0.7f)))).clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (curCountLeft > 1) { val newCount = curCountLeft - 1; currentCountsLeft[currentIndex] = newCount; saveProgress(currentIndex, newCount) } 
                                    else {
                                        currentCountsLeft[currentIndex] = 0
                                        if (currentIndex < totalCount - 1) { val nextIndex = currentIndex + 1; currentIndex = nextIndex; saveProgress(nextIndex, athkarList[nextIndex].count) } 
                                        else { isFinished = true; saveProgress(0, athkarList[0].count) }
                                    }
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(text = "$curCountLeft", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    Text(text = if (isArabic) "متبقي" else "Left", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                            Text(text = if (isArabic) "انقر على الدائرة للعد" else "Tap the circle to count", style = MaterialTheme.typography.labelSmall.copy(color = if (darkTheme) Color.Gray else Color(0xFF64748B)))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { if (currentIndex > 0) { val prevIndex = currentIndex - 1; currentIndex = prevIndex; val prevDefaultCount = athkarList[prevIndex].count; currentCountsLeft[prevIndex] = prevDefaultCount; saveProgress(prevIndex, prevDefaultCount) } }, enabled = currentIndex > 0) {
                                Text(text = if (isArabic) "السابق" else "Previous", fontWeight = FontWeight.Bold, color = if (currentIndex > 0) brandColor else Color.Gray)
                            }
                            TextButton(onClick = {
                                currentCountsLeft[currentIndex] = 0
                                if (currentIndex < totalCount - 1) { val nextIndex = currentIndex + 1; currentIndex = nextIndex; saveProgress(nextIndex, athkarList[nextIndex].count) } 
                                else { isFinished = true; saveProgress(0, athkarList[0].count) }
                            }) {
                                Text(text = if (isArabic) "تخطي" else "Skip", fontWeight = FontWeight.Bold, color = brandColor)
                            }
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(SuccessGreen.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Text(text = "✨", fontSize = 32.sp) }
                                Text(text = if (isArabic) "تقبل الله طاعتك!" else "May Allah accept your deeds!", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = SuccessGreen), textAlign = TextAlign.Center)
                                Text(text = if (isArabic) "أتممت قراءة ${if (type == "morning") "أذكار الصباح" else "أذكار المساء"} بنجاح، حفظك الله ورعاك." else "You have successfully completed reading the ${if (type == "morning") "Morning Dhikr" else "Evening Dhikr"}.", style = MaterialTheme.typography.bodyMedium.copy(color = if (darkTheme) Color.LightGray else Color(0xFF475569), lineHeight = 22.sp), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { saveProgress(0, athkarList[0].count); onComplete() }, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                    Text(text = if (isArabic) "تم وحفظ الإنجاز" else "Save Achievement", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorshipCelebrationDialog(title: String, description: String, darkTheme: Boolean, isArabic: Boolean, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        CelebrationEffect()
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = if (darkTheme) Color.White else Color(0xFF0F172A)), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = { Text(text = description, style = MaterialTheme.typography.bodyMedium.copy(color = if (darkTheme) Color.LightGray else Color(0xFF475569)), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), modifier = Modifier.fillMaxWidth()) { Text(if (isArabic) "متابعة" else "Continue", color = Color.White, fontWeight = FontWeight.Bold) } },
            containerColor = if (darkTheme) Color(0xFF1E293B) else Color.White
        )
    }
}

data class Particle(var x: Float, var y: Float, var speedY: Float, var speedX: Float, val color: Color, val isBalloon: Boolean, val size: Float)

@Composable
fun CelebrationEffect() {
    val particles = remember { 
        List(80) { 
            val isBalloon = Random.nextFloat() > 0.7f
            Particle(
                x = Random.nextFloat() * 1000f, 
                y = if (isBalloon) 2500f + Random.nextFloat() * 500f else -100f - Random.nextFloat() * 500f, 
                speedY = if (isBalloon) -(3f + Random.nextFloat() * 4f) else (5f + Random.nextFloat() * 6f), 
                speedX = (Random.nextFloat() - 0.5f) * 4f, 
                color = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFF8B5CF6)).random(),
                isBalloon = isBalloon,
                size = if (isBalloon) 40f + Random.nextFloat() * 20f else 10f + Random.nextFloat() * 10f
            )
        } 
    }
    var trigger by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { trigger += 1f }
            particles.forEach { 
                it.y += it.speedY; it.x += it.speedX 
                if (!it.isBalloon && it.y > 3000f) it.y = -100f
                if (it.isBalloon && it.y < -500f) it.y = 2500f
            }
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        trigger.let { _ ->
            particles.forEach { p ->
                if (p.isBalloon) drawCircle(color = p.color.copy(alpha = 0.8f), radius = p.size, center = Offset(p.x, p.y))
                else drawRect(color = p.color, topLeft = Offset(p.x, p.y), size = Size(p.size, p.size))
            }
        }
    }
}

@Composable
fun StatsScreen(darkTheme: Boolean, isArabic: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var history by remember { mutableStateOf<List<DailyRecord>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val db = WorshipDatabase.getDatabase(context)
                val cursor = db.openHelper.readableDatabase.query("SELECT * FROM daily_record ORDER BY date ASC")
                val list = mutableListOf<DailyRecord>()
                while (cursor.moveToNext()) {
                    val dateIdx = cursor.getColumnIndex("date")
                    val fIdx = cursor.getColumnIndex("fajrDone")
                    val dIdx = cursor.getColumnIndex("dhuhrDone")
                    val aIdx = cursor.getColumnIndex("asrDone")
                    val mIdx = cursor.getColumnIndex("maghribDone")
                    val iIdx = cursor.getColumnIndex("ishaDone")
                    val qIdx = cursor.getColumnIndex("quranPages")
                    val mdIdx = cursor.getColumnIndex("morningDhikrDone")
                    val edIdx = cursor.getColumnIndex("eveningDhikrDone")
                    val dcIdx = cursor.getColumnIndex("dhikrCount")
                    if (dateIdx != -1) {
                        list.add(DailyRecord(
                            date = cursor.getString(dateIdx), fajrDone = cursor.getInt(fIdx) == 1, dhuhrDone = cursor.getInt(dIdx) == 1,
                            asrDone = cursor.getInt(aIdx) == 1, maghribDone = cursor.getInt(mIdx) == 1, ishaDone = cursor.getInt(iIdx) == 1,
                            quranPages = cursor.getInt(qIdx), morningDhikrDone = cursor.getInt(mdIdx) == 1, eveningDhikrDone = cursor.getInt(edIdx) == 1,
                            dhikrCount = cursor.getInt(dcIdx)
                        ))
                    }
                }
                cursor.close()
                history = list
            } catch (e: Exception) { history = emptyList() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isArabic) "إحصائيات الإنجاز 📊" else "Worship Stats 📊", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (history.isEmpty()) {
                    Text(if (isArabic) "لا توجد بيانات مسجلة بعد." else "No records found.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                } else {
                    val totalScore = history.sumOf { it.calculatePoints() }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(SuccessGreen.copy(alpha = 0.1f)).padding(8.dp)) {
                            Text(if (isArabic) "إجمالي النقاط" else "Total Points", style = MaterialTheme.typography.labelSmall)
                            Text("$totalScore", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = SuccessGreen))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF3B82F6).copy(alpha = 0.1f)).padding(8.dp)) {
                            Text(if (isArabic) "الأيام المسجلة" else "Days Tracked", style = MaterialTheme.typography.labelSmall)
                            Text("${history.size}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = Color(0xFF3B82F6)))
                        }
                    }
                    Text(if (isArabic) "أداء الأيام المسجلة (من 100)" else "Days Performance (Out of 100)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)).background(if (darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9)).padding(16.dp)) {
                        val barWidth = size.width / (history.size.coerceAtLeast(1) * 2f)
                        val maxH = size.height
                        history.forEachIndexed { i, rec ->
                            val pts = rec.calculatePoints().toFloat()
                            val h = (pts / 100f) * maxH
                            val x = i * (barWidth * 2) + barWidth / 2
                            drawRect(color = if (pts >= 100) SuccessGreen else Color(0xFF3B82F6), topLeft = Offset(x, maxH - h), size = Size(barWidth, h))
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(if (isArabic) "إغلاق" else "Close") } },
        containerColor = if (darkTheme) Color(0xFF0F172A) else Color.White
    )
}

@Composable
fun HisnAlMuslimDialog(category: String, darkTheme: Boolean, isArabic: Boolean, onDismiss: () -> Unit) {
    val title = when(category) {
        "sleep" -> if (isArabic) "أذكار النوم" else "Sleep"
        "wakeup" -> if (isArabic) "أذكار الاستيقاظ" else "Waking up"
        "food" -> if (isArabic) "الطعام والشراب" else "Food & Drink"
        "travel" -> if (isArabic) "الركوب والسفر" else "Travel"
        "home" -> if (isArabic) "المنزل" else "Home"
        "mosque" -> if (isArabic) "المسجد" else "Mosque"
        "toilet" -> if (isArabic) "الخلاء" else "Toilet"
        "rain" -> if (isArabic) "المطر والرياح" else "Rain & Wind"
        else -> ""
    }
    
    val text = when(category) {
        "sleep" -> "بِاسْمِكَ رَبِّـي وَضَعْـتُ جَنْـبي، وَبِكَ أَرْفَعُـه، فَإِن أَمْسَـكْتَ نَفْسـي فارْحَـمْها ، وَإِنْ أَرْسَلْتَـها فاحْفَظْـها بِمـا تَحْفَـظُ بِه عِبـادَكَ الصّـالِحـين."
        "wakeup" -> "الحَمْـدُ لِلّهِ الّذي أَحْـيانا بَعْـدَ ما أَماتَـنا وَإليه النُّـشور."
        "food" -> "بِسْمِ اللَّهِ. (عند البدء)\n\nالْحَمْدُ لِلَّهِ الَّذِي أَطْعَمَنِي هَذَا وَرَزَقَنِيهِ مِنْ غَيْرِ حَوْلٍ مِنِّي وَلَا قُوَّةٍ. (عند الانتهاء)"
        "travel" -> "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ * وَإِنَّا إِلَى رَبِّنَا لَمُنْقَلِبُونَ."
        "home" -> "بِسْـمِ اللهِ وَلَجْنـا، وَبِسْـمِ اللهِ خَـرَجْنـا، وَعَلـى رَبِّنـا تَوَكّّلْـنا. (الدخول)\n\nبِسْمِ اللَّهِ، تَوَكَّلْتُ عَلَى اللَّهِ، وَلَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ. (الخروج)"
        "mosque" -> "اللَّهُمَّ افْتَحْ لِي أَبْوَابَ رَحْمَتِكَ. (الدخول)\n\nاللَّهُمَّ إِنِّي أَسْأَلُكَ مِنْ فَضْلِكَ. (الخروج)"
        "toilet" -> "بِسْمِ الله، اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْخُبْثِ وَالْخَبَائِثِ. (الدخول)\n\nغُفْرَانَكَ. (الخروج)"
        "rain" -> "اللَّهُمَّ صَيِّباً نَافِعاً. (المطر)\n\nسُبْحَانَ الَّذِي يُسَبِّحُ الرَّعْدُ بِحَمْدِهِ وَالْمَلَائِكَةُ مِنْ خِيفَتِهِ. (الرعد)"
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = if (darkTheme) Color(0xFF10B981) else Color(0xFF065F46)), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = if (darkTheme) Color.White else Color(0xFF000000)), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = if (darkTheme) Color(0xFF10B981) else Color(0xFF065F46)), modifier = Modifier.fillMaxWidth()) { Text(if (isArabic) "إغلاق" else "Close", color = Color.White, fontWeight = FontWeight.Bold) } },
        containerColor = if (darkTheme) Color(0xFF1E293B) else Color.White
    )
}
