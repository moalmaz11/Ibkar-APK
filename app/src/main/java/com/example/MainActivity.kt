package com.example

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.data.DailyRecord
import com.example.data.DateHelper
import com.example.data.WorshipDatabase
import com.example.ui.WorshipViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.random.Random

// ==========================================
// 1. قاعدة البيانات المحلية للمحافظات
// ==========================================
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

fun getNearestCity(lat: Float, lng: Float): CityLocation = egyptCities.minByOrNull { city ->
    val dLat = city.lat - lat
    val dLng = city.lng - lng
    (dLat * dLat) + (dLng * dLng)
} ?: egyptCities[0]

fun updateLocationOffline(context: Context, onResult: (Boolean, String, Float, Float) -> Unit) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    try {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!isGpsEnabled && !isNetworkEnabled) {
            onResult(false, "الرجاء تفعيل GPS", 0f, 0f)
            return
        }
        val loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (loc != null) onResult(true, "تم التقاط الموقع", loc.latitude.toFloat(), loc.longitude.toFloat())
        else onResult(false, "جاري البحث عن الأقمار..", 0f, 0f)
    } catch (e: SecurityException) {
        onResult(false, "صلاحية الموقع غير ممنوحة", 0f, 0f)
    }
}

// ألوان تصميم Glassmorphism العصري
val GlassBgGradient = listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF111827))
val GlassAccent = Color(0xFF818CF8)
val GlassAccentLight = Color(0xFFA5B4FC)
val GlassWhite = Color.White
val GlassPanelBg = Color.White.copy(alpha = 0.06f)
val GlassPanelBorder = Color.White.copy(alpha = 0.12f)
val GlassSuccess = Color(0xFF34D399)

@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    var mod = modifier
        .clip(RoundedCornerShape(24.dp))
        .background(GlassPanelBg)
        .border(1.dp, GlassPanelBorder, RoundedCornerShape(24.dp))
    if (onClick != null) { mod = mod.clickable { onClick() } }
    Column(modifier = mod.padding(20.dp), content = content)
}

class MainActivity : ComponentActivity() {
    private var initialDhikrTypeState = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.notification.PrayerNotificationManager.createNotificationChannel(this)
        handleIntent(intent)
        setContent {
            val context = LocalContext.current
            val themePrefs = remember(context) { context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE) }
            var isArabic by rememberSaveable { mutableStateOf(themePrefs.getBoolean("is_arabic", true)) }

            MyApplicationTheme(darkTheme = true) {
                CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF111827)) {
                        MainAppContent(
                            isArabic = isArabic,
                            onToggleLanguage = { isArabic = !isArabic; themePrefs.edit().putBoolean("is_arabic", isArabic).apply() },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    isArabic: Boolean, onToggleLanguage: () -> Unit,
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
    var showStatsDialog by remember { mutableStateOf(false) }
    var showManualLocationDialog by remember { mutableStateOf(false) }
    var activeDhikrTypeForReading by remember { mutableStateOf<String?>(null) }
    var activeHisnCategory by remember { mutableStateOf<String?>(null) }
    
    val celebrationPrefs = remember(context) { context.getSharedPreferences("celebration_prefs", Context.MODE_PRIVATE) }
    var showDaily100Celebration by remember { mutableStateOf(false) }
    var showTotal100Celebration by remember { mutableStateOf(false) }

    val dailyPoints = record?.calculatePoints() ?: 0
    LaunchedEffect(dailyPoints, record?.date) {
        val todayStr = record?.date ?: ""
        val actualToday = DateHelper.getTodayDateString(context)
        if (dailyPoints >= 100 && todayStr == actualToday && todayStr.isNotEmpty()) {
            val lastCelebrated = celebrationPrefs.getString("daily_100_last_date", "")
            if (todayStr != lastCelebrated) showDaily100Celebration = true
        }
    }
    LaunchedEffect(totalPoints) {
        if (totalPoints >= 100) {
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
    var offsetMinutesVal by remember { mutableStateOf(notificationSettingsPrefs.getInt("prayer_offset_minutes", 0)) }

    var cityNameState by remember { mutableStateOf(if (isArabic) notificationSettingsPrefs.getString("user_city_name_ar", "القاهرة") ?: "القاهرة" else notificationSettingsPrefs.getString("user_city_name_en", "Cairo") ?: "Cairo") }

    val todayTimesRaw = remember(userLat, userLng, prayerCalcMethod) {
        val cal = com.example.notification.PrayerTimeCalculator.getLocalCalendar(userLat.toDouble(), userLng.toDouble())
        com.example.notification.PrayerTimeCalculator.calculatePrayerTimes(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), userLat.toDouble(), userLng.toDouble(), prayerCalcMethod)
    }
    val todayTimes = remember(todayTimesRaw) { todayTimesRaw }
    val todayStr = DateHelper.getTodayDateString(context)
    val displayDate = if (isArabic) DateHelper.getArabicDisplayDate(selectedDate) else selectedDate
    val isTodaySelected = selectedDate == todayStr

    var upcomingPrayerInfoState by remember(todayTimes) { mutableStateOf<UpcomingPrayerInfo?>(null) }
    fun getPrayerTimeStr(key: String): String {
        val t = todayTimes[key] ?: return ""
        val h12 = if (t.first % 12 == 0) 12 else t.first % 12
        val amPm = if (t.first >= 12) { if (isArabic) "م" else "PM" } else { if (isArabic) "ص" else "AM" }
        return "%d:%02d %s".format(h12, t.second, amPm)
    }

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
                    userLat = newLat; userLng = newLng
                    val nearest = getNearestCity(newLat, newLng)
                    cityNameState = if (isArabic) nearest.nameAr else nearest.nameEn
                    notificationSettingsPrefs.edit().putFloat("user_latitude", nearest.lat).putFloat("user_longitude", nearest.lng).putString("user_city_name_ar", nearest.nameAr).putString("user_city_name_en", nearest.nameEn).apply()
                    Toast.makeText(context, if (isArabic) "تم التحديث لـ ${nearest.nameAr}" else "Updated to ${nearest.nameEn}", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(initialDhikrType) {
        if (initialDhikrType != null) { activeDhikrTypeForReading = initialDhikrType; onInitialDhikrHandled() }
    }

    var hasNotifyPermission by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED } else true) }
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        hasNotifyPermission = isGranted
        if (isGranted) com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
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
            else com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
        } else { com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }
    }

    val activeRecord = record ?: DailyRecord(date = selectedDate)
    val prayersDoneCount = listOf(activeRecord.fajrDone, activeRecord.dhuhrDone, activeRecord.asrDone, activeRecord.maghribDone, activeRecord.ishaDone).count { it }
    val isQuranDone = activeRecord.quranPages > 0
    val totalDoneItems = prayersDoneCount + (if (isQuranDone) 1 else 0) + (if (activeRecord.morningDhikrDone) 1 else 0) + (if (activeRecord.eveningDhikrDone) 1 else 0)

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(GlassBgGradient)), contentAlignment = Alignment.TopCenter) {
        LazyColumn(modifier = Modifier.fillMaxWidth().widthIn(max = 660.dp).windowInsetsPadding(WindowInsets.safeDrawing), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            
            // Header
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(44.dp).clip(CircleShape).background(GlassPanelBg).border(1.dp, GlassPanelBorder, CircleShape)) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = GlassAccentLight, modifier = Modifier.size(22.dp))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (isArabic) "إِبْكَـار" else "Ibkar", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = GlassWhite, fontSize = 26.sp, letterSpacing = 1.sp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable { showManualLocationDialog = true }) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = GlassAccentLight, modifier = Modifier.size(12.dp))
                                Text(text = cityNameState, style = MaterialTheme.typography.labelSmall.copy(color = GlassAccentLight, fontWeight = FontWeight.Bold))
                            }
                        }
                        IconButton(onClick = { showStatsDialog = true }, modifier = Modifier.size(44.dp).clip(CircleShape).background(GlassPanelBg).border(1.dp, GlassPanelBorder, CircleShape)) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = GlassAccentLight, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // Date Navigator (Glass)
            item {
                GlassCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.selectPreviousDay() }) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = GlassAccent, modifier = Modifier.size(28.dp).scale(if(isArabic) 1f else -1f)) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = displayDate, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                            if (!isTodaySelected) { Text(text = if (isArabic) "العودة لليوم" else "Back to Today", style = MaterialTheme.typography.labelSmall.copy(color = GlassAccent, fontWeight = FontWeight.Bold), modifier = Modifier.clickable { viewModel.selectToday() }.padding(vertical = 4.dp)) }
                        }
                        val isNextDisabled = selectedDate >= todayStr
                        IconButton(onClick = { viewModel.selectNextDay() }, enabled = !isNextDisabled) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = GlassAccent.copy(alpha = if (isNextDisabled) 0.3f else 1f), modifier = Modifier.size(28.dp).scale(if(isArabic) 1f else -1f)) }
                    }
                }
            }

            // Tracker Card (Glass)
            item {
                GlassCard {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column {
                            Text(text = if (isArabic) "إجمالي النقاط اليوم" else "Today's Points", style = MaterialTheme.typography.labelMedium.copy(color = GlassAccentLight, fontWeight = FontWeight.Bold))
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = "$dailyPoints", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, color = GlassAccent, fontSize = 40.sp))
                                Text(text = "/100", style = MaterialTheme.typography.bodyLarge.copy(color = GlassAccentLight, fontWeight = FontWeight.Bold, fontSize = 18.sp), modifier = Modifier.padding(bottom = 6.dp))
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(GlassAccent.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GlassAccent, modifier = Modifier.size(14.dp))
                                Text(text = if (streak > 0) { if (isArabic) "التتابع: $streak أيام" else "Streak: $streak" } else { if (isArabic) "ابدأ التتابع" else "Start Streak" }, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = GlassAccent))
                            }
                        }
                    }
                    val dayProgress = totalDoneItems.toFloat() / 8f
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(10.dp)).background(GlassWhite.copy(alpha = 0.1f))) {
                        Box(modifier = Modifier.fillMaxWidth(dayProgress).height(8.dp).clip(RoundedCornerShape(10.dp)).background(GlassAccent))
                    }
                }
            }

            if (!isTodaySelected) {
                item {
                    Text(text = if (isArabic) "سجل الأيام السابقة للعرض فقط حفاظاً على دقة البيانات" else "Past records are view-only.", style = MaterialTheme.typography.labelSmall.copy(color = GlassAccentLight), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            // Prayers List (Glass rows)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = if (isArabic) "الصلوات المفروضة" else "Obligatory Prayers", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite), modifier = Modifier.padding(horizontal = 4.dp))
                    if (isTodaySelected) upcomingPrayerInfoState?.let { NextPrayerCountdownCard(upcoming = it, isArabic = isArabic) }

                    PrayerItemRow(if (isArabic) "الفجر" else "Fajr", activeRecord.fajrDone, getPrayerTimeStr("fajr")) { if (isTodaySelected) viewModel.togglePrayer("fajr") }
                    PrayerItemRow(if (isArabic) "الظهر" else "Dhuhr", activeRecord.dhuhrDone, getPrayerTimeStr("dhuhr")) { if (isTodaySelected) viewModel.togglePrayer("dhuhr") }
                    PrayerItemRow(if (isArabic) "العصر" else "Asr", activeRecord.asrDone, getPrayerTimeStr("asr")) { if (isTodaySelected) viewModel.togglePrayer("asr") }
                    PrayerItemRow(if (isArabic) "المغرب" else "Maghrib", activeRecord.maghribDone, getPrayerTimeStr("maghrib")) { if (isTodaySelected) viewModel.togglePrayer("maghrib") }
                    PrayerItemRow(if (isArabic) "العشاء" else "Isha", activeRecord.ishaDone, getPrayerTimeStr("isha")) { if (isTodaySelected) viewModel.togglePrayer("isha") }
                }
            }
            // Quran & Worship (Glass rows)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = if (isArabic) "العبادات اليومية" else "Daily Worship", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite), modifier = Modifier.padding(horizontal = 4.dp, top = 16.dp))
                    
                    // Quran
                    GlassCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(text = if (isArabic) "ورد القرآن الكريم" else "Quran Wird", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                                Text(text = if (isArabic) "عدد الصفحات المقروءة" else "Pages Read", style = MaterialTheme.typography.labelSmall.copy(color = GlassAccentLight))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                IconButton(onClick = { if (isTodaySelected) viewModel.setQuranPages(activeRecord.quranPages - 1) }, enabled = isTodaySelected, modifier = Modifier.size(36.dp).clip(CircleShape).background(GlassPanelBorder)) { Text("-", fontWeight = FontWeight.Bold, color = GlassWhite, fontSize = 18.sp) }
                                Text("${activeRecord.quranPages}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = GlassAccent), modifier = Modifier.widthIn(min = 28.dp), textAlign = TextAlign.Center)
                                IconButton(onClick = { if (isTodaySelected) viewModel.setQuranPages(activeRecord.quranPages + 1) }, enabled = isTodaySelected, modifier = Modifier.size(36.dp).clip(CircleShape).background(GlassAccent)) { Text("+", fontWeight = FontWeight.Bold, color = GlassWhite, fontSize = 18.sp) }
                            }
                        }
                    }

                    // Dhikr
                    GlassCard(onClick = { activeDhikrTypeForReading = "morning" }) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(text = if (isArabic) "أذكار الصباح" else "Morning Dhikr", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                                Text(text = if (isArabic) "اضغط للقراءة" else "Tap to read", style = MaterialTheme.typography.labelSmall.copy(color = GlassAccentLight))
                            }
                            Checkbox(checked = activeRecord.morningDhikrDone, enabled = isTodaySelected, onCheckedChange = { if (isTodaySelected) viewModel.toggleMorningDhikr() }, colors = CheckboxDefaults.colors(checkedColor = GlassSuccess, uncheckedColor = GlassPanelBorder))
                        }
                    }

                    GlassCard(onClick = { activeDhikrTypeForReading = "evening" }) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(text = if (isArabic) "أذكار المساء" else "Evening Dhikr", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                                Text(text = if (isArabic) "اضغط للقراءة" else "Tap to read", style = MaterialTheme.typography.labelSmall.copy(color = GlassAccentLight))
                            }
                            Checkbox(checked = activeRecord.eveningDhikrDone, enabled = isTodaySelected, onCheckedChange = { if (isTodaySelected) viewModel.toggleEveningDhikr() }, colors = CheckboxDefaults.colors(checkedColor = GlassSuccess, uncheckedColor = GlassPanelBorder))
                        }
                    }

                    // Tasbeeh
                    GlassCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = if (isArabic) "المسبحة الإلكترونية" else "Digital Rosary", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                                if (isTodaySelected) { Text(text = if (isArabic) "إعادة ضبط ↺" else "Reset ↺", style = MaterialTheme.typography.labelSmall.copy(color = GlassAccentLight), modifier = Modifier.clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.resetDhikr() }.padding(top = 4.dp)) }
                            }
                            Box(modifier = Modifier.size(70.dp).clip(CircleShape).background(GlassAccent.copy(alpha = 0.2f)).border(2.dp, GlassAccent, CircleShape).clickable { if (isTodaySelected) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.incrementDhikr() } }, contentAlignment = Alignment.Center) {
                                Text("${activeRecord.dhikrCount}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = GlassWhite))
                            }
                        }
                    }
                }
            }

            // Hisn Al-Muslim (Glass Grid)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = if (isArabic) "حصن المسلم" else "Hisn Al-Muslim", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite), modifier = Modifier.padding(horizontal = 4.dp, top = 16.dp))
                    GlassCard {
                        val hisnCats = listOf("sleep" to (if (isArabic) "النوم" else "Sleep"), "wakeup" to (if (isArabic) "الاستيقاظ" else "Wakeup"), "food" to (if (isArabic) "الطعام" else "Food"), "travel" to (if (isArabic) "السفر" else "Travel"), "home" to (if (isArabic) "المنزل" else "Home"), "mosque" to (if (isArabic) "المسجد" else "Mosque"), "toilet" to (if (isArabic) "الخلاء" else "Toilet"), "rain" to (if (isArabic) "المطر" else "Rain"))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            hisnCats.chunked(2).forEach { rowCats ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    rowCats.forEach { cat ->
                                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(GlassPanelBorder.copy(alpha = 0.04f)).border(1.dp, GlassPanelBorder, RoundedCornerShape(16.dp)).clickable { activeHisnCategory = cat.first }.padding(14.dp), contentAlignment = Alignment.Center) {
                                            Text(cat.second, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    if (showStatsDialog) { StatsScreen(isArabic = isArabic, onDismiss = { showStatsDialog = false }) }
    if (activeHisnCategory != null) { HisnAlMuslimDialog(category = activeHisnCategory!!, isArabic = isArabic, onDismiss = { activeHisnCategory = null }) }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text(text = if (isArabic) "الإعدادات" else "Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Switch(checked = !isArabic, onCheckedChange = { onToggleLanguage() }, modifier = Modifier.scale(0.85f))
                        Text(text = if (isArabic) "English Language" else "اللغة العربية")
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Switch(checked = hasNotifyPermission && notifyAll, onCheckedChange = { checked -> if (!hasNotifyPermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else { notifyAll = checked; notificationSettingsPrefs.edit().putBoolean("notify_all", checked).apply(); if (checked) com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) } }, modifier = Modifier.scale(0.85f))
                        Text(text = if (isArabic) "تفعيل الإشعارات" else "Enable Notifications", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    if (hasNotifyPermission && notifyAll) {
                        Button(onClick = { showNotificationDetailsDialog = true }, modifier = Modifier.fillMaxWidth()) { Text(text = if (isArabic) "تخصيص أوقات التنبيهات" else "Customize Alert Times") }
                    }
                    HorizontalDivider()
                    Text(text = if (isArabic) "الموقع الجغرافي" else "Location", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, modifier = Modifier.weight(1f)) { Text(text = if (isArabic) "تلقائي" else "Auto GPS") }
                        Button(onClick = { showManualLocationDialog = true }, modifier = Modifier.weight(1f)) { Text(text = if (isArabic) "يدوي" else "Manual") }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = if (isArabic) "$offsetMinutesVal دقيقة" else "$offsetMinutesVal min", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        Text(text = if (isArabic) "إزاحة المواقيت:" else "Time Offset:", style = MaterialTheme.typography.labelSmall)
                    }
                    Slider(value = offsetMinutesVal.toFloat(), onValueChange = { newValue -> offsetMinutesVal = newValue.toInt(); notificationSettingsPrefs.edit().putInt("prayer_offset_minutes", newValue.toInt()).apply() }, onValueChangeFinished = { com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, valueRange = 0f..30f, steps = 6, modifier = Modifier.height(28.dp))
                }
            },
            confirmButton = { Button(onClick = { showSettingsDialog = false }) { Text(if (isArabic) "تم" else "Done") } }
        )
    }

    if (showNotificationDetailsDialog) {
        val mHour = notificationSettingsPrefs.getInt("morning_dhikr_hour", 6)
        val mMin = notificationSettingsPrefs.getInt("morning_dhikr_minute", 0)
        val eHour = notificationSettingsPrefs.getInt("evening_dhikr_hour", 17)
        val eMin = notificationSettingsPrefs.getInt("evening_dhikr_minute", 0)
        var currentMHour by remember { mutableStateOf(mHour) }
        var currentMMin by remember { mutableStateOf(mMin) }
        var currentEHour by remember { mutableStateOf(eHour) }
        var currentEMin by remember { mutableStateOf(eMin) }

        val formatTime = { h: Int, m: Int ->
            val amPm = if (h >= 12) (if (isArabic) "م" else "PM") else (if (isArabic) "ص" else "AM")
            val h12 = if (h % 12 == 0) 12 else h % 12
            String.format("%02d:%02d %s", h12, m, amPm)
        }

        AlertDialog(
            onDismissRequest = { showNotificationDetailsDialog = false },
            title = { Text(text = if (isArabic) "تخصيص الإشعارات" else "Notifications", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Switch(checked = notifyPrayers, onCheckedChange = { notifyPrayers = it; notificationSettingsPrefs.edit().putBoolean("notify_prayers", it).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) })
                        Text(text = if (isArabic) "تنبيهات الصلوات" else "Adhan Alerts", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Switch(checked = notifyMorningDhikr, onCheckedChange = { notifyMorningDhikr = it; notificationSettingsPrefs.edit().putBoolean("notify_morning_dhikr", it).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) })
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = if (isArabic) "أذكار الصباح" else "Morning Dhikr", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            Text(text = formatTime(currentMHour, currentMMin), color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { TimePickerDialog(context, { _, h, m -> currentMHour = h; currentMMin = m; notificationSettingsPrefs.edit().putInt("morning_dhikr_hour", h).putInt("morning_dhikr_minute", m).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, currentMHour, currentMMin, false).show() }.padding(4.dp))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Switch(checked = notifyEveningDhikr, onCheckedChange = { notifyEveningDhikr = it; notificationSettingsPrefs.edit().putBoolean("notify_evening_dhikr", it).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) })
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = if (isArabic) "أذكار المساء" else "Evening Dhikr", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            Text(text = formatTime(currentEHour, currentEMin), color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { TimePickerDialog(context, { _, h, m -> currentEHour = h; currentEMin = m; notificationSettingsPrefs.edit().putInt("evening_dhikr_hour", h).putInt("evening_dhikr_minute", m).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, currentEHour, currentEMin, false).show() }.padding(4.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNotificationDetailsDialog = false }) { Text(if (isArabic) "إغلاق" else "Close") } }
        )
    }

    if (activeDhikrTypeForReading != null) {
        DhikrReadingFlow(
            type = activeDhikrTypeForReading!!, isArabic = isArabic,
            onDismiss = { activeDhikrTypeForReading = null },
            onComplete = {
                if (isTodaySelected) {
                    val isDoneCurrently = if (activeDhikrTypeForReading == "morning") activeRecord.morningDhikrDone else activeRecord.eveningDhikrDone
                    if (!isDoneCurrently) { if (activeDhikrTypeForReading == "morning") viewModel.toggleMorningDhikr() else viewModel.toggleEveningDhikr() }
                }
                activeDhikrTypeForReading = null
            }
        )
    }

    if (showDaily100Celebration) {
        WorshipCelebrationDialog(
            title = if (isArabic) "مبارك! حققت العلامة الكاملة" else "Congrats! Perfect Score",
            description = if (isArabic) "أتممت جميع عبادات اليوم وحققت 100 نقطة كاملة." else "You completed all daily worships and achieved 100 points.",
            isArabic = isArabic, onDismiss = { celebrationPrefs.edit().putString("daily_100_last_date", record?.date ?: "").apply(); showDaily100Celebration = false; hasDismissedDailyCelebrationToday = true }
        )
    }

    if (showTotal100Celebration) {
        WorshipCelebrationDialog(
            title = if (isArabic) "إنجاز مبارك!" else "Blessed Achievement!",
            description = if (isArabic) "تجاوزت حاجز 100 نقطة في مجموع طاعاتك." else "You have surpassed 100 total points. Keep going!",
            isArabic = isArabic, onDismiss = { celebrationPrefs.edit().putBoolean("total_100_celebrated", true).apply(); showTotal100Celebration = false; hasDismissedTotalCelebration = true }
        )
    }
}

// ==========================================
// 3. المكونات المساعدة للزجاج العصري
// ==========================================

@Composable
fun PrayerItemRow(name: String, isDone: Boolean, timeText: String, onToggle: () -> Unit) {
    val bgColor = if (isDone) GlassAccent.copy(alpha = 0.2f) else GlassPanelBg
    val borderColor = if (isDone) GlassAccent.copy(alpha = 0.5f) else GlassPanelBorder
    val textColor = if (isDone) GlassWhite else GlassWhite.copy(alpha = 0.8f)
    
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bgColor).border(1.dp, borderColor, RoundedCornerShape(16.dp)).clickable { onToggle() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = timeText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GlassAccentLight), modifier = Modifier.width(65.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = textColor), modifier = Modifier.weight(1f))
        
        if (isDone) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GlassAccent, modifier = Modifier.size(24.dp))
        } else {
            Canvas(modifier = Modifier.size(22.dp)) { drawCircle(color = GlassWhite.copy(alpha = 0.3f), style = Stroke(width = 4f)) }
        }
    }
}

@Composable
fun NextPrayerCountdownCard(upcoming: UpcomingPrayerInfo, isArabic: Boolean) {
    val h = upcoming.diffMinutes / 60
    val m = upcoming.diffMinutes % 60
    val s = upcoming.diffSeconds
    val countdownFormatted = if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)

    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = if (isArabic) "الوقت المتبقي للأذان:" else "Time until Adhan:", style = MaterialTheme.typography.labelSmall.copy(color = GlassAccentLight, fontWeight = FontWeight.Bold))
                Text(text = countdownFormatted, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = GlassAccent, fontSize = 24.sp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = upcoming.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = GlassWhite))
                Box(modifier = Modifier.clip(RoundedCornerShape(50.dp)).background(GlassWhite.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(text = "${upcoming.timeStr}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                }
            }
        }
    }
}

data class StepDhikr(val text: String, val count: Int, val benefit: String = "")

@Composable
fun DhikrReadingFlow(type: String, isArabic: Boolean = true, onDismiss: () -> Unit, onComplete: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val athkarList = if (type == "morning") {
        listOf(
            StepDhikr("أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لا إِلَهَ إِلا اللَّهُ وَحْدَهُ لا شَرِيكَ لَهُ.", 1, "سؤال خير اليوم كله"),
            StepDhikr("اللّهُـمَّ أَنْتَ رَبِّـي لا إِلهَ إِلاّ أَنْتَ، خَلَقْتَنـي وَأَنا عَبْـدُك.", 1, "سيد الاستغفار"),
            StepDhikr("حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ.", 7, "كفاه الله ما أهمه"),
            StepDhikr("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ.", 3, "لم يضره شيء قط"),
            StepDhikr("رَضِيتُ بِاللَّهِ رَبّاً، وَبِالْإِسْلَامِ دِيناً، وَبِمُحَمَّدٍ نَبِيّاً.", 3, "كان حقاً على الله أن يرضيه"),
            StepDhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.", 100, "حطت خطاياه"),
            StepDhikr("أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ.", 100, "ممحاة للذنوب")
        )
    } else {
        listOf(
            StepDhikr("أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لا إِلَهَ إِلا اللَّهُ وَحْدَهُ لا شَرِيكَ لَهُ.", 1, "سؤال خير الليلة"),
            StepDhikr("اللّهُـمَّ أَنْتَ رَبِّـي لا إِلهَ إِلاّ أَنْتَ، خَلَقْتَنـي وَأَنا عَبْـدُك.", 1, "سيد الاستغفار"),
            StepDhikr("حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ.", 7, "كفاه الله ما أهمه"),
            StepDhikr("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ.", 3, "لم يضره شيء قط"),
            StepDhikr("أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.", 3, "لم يضره شيء في تلك الليلة"),
            StepDhikr("رَضِيتُ بِاللَّهِ رَبّاً، وَبِالْإِسْلَامِ دِيناً، وَبِمُحَمَّدٍ نَبِيّاً.", 3, "حق على الله أن يرضي قائله"),
            StepDhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.", 100, "مغفرة الذنوب")
        )
    }

    var currentIndex by remember { mutableStateOf(0) }
    val totalCount = athkarList.size
    val currentCountsLeft = remember { mutableStateListOf<Int>().apply { addAll(athkarList.map { it.count }) } }
    val currentDhikr = athkarList.getOrNull(currentIndex)
    val curCountLeft = currentCountsLeft.getOrNull(currentIndex) ?: 0
    var isFinished by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(onDismissRequest = { onDismiss() }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF111827)).padding(20.dp), contentAlignment = Alignment.TopCenter) {
                Column(modifier = Modifier.fillMaxHeight().widthIn(max = 660.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onDismiss() }) { Icon(Icons.Default.Close, contentDescription = null, tint = GlassWhite) }
                        Text(text = if (type == "morning") "أذكار الصباح" else "أذكار المساء", style = MaterialTheme.typography.titleLarge.copy(color = GlassAccent, fontWeight = FontWeight.Bold))
                        IconButton(onClick = { currentIndex = 0; isFinished = false; currentCountsLeft.clear(); currentCountsLeft.addAll(athkarList.map { it.count }) }) { Icon(Icons.Default.Refresh, contentDescription = null, tint = GlassWhite) }
                    }

                    if (!isFinished && currentDhikr != null) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "${currentIndex + 1} / $totalCount", style = MaterialTheme.typography.labelSmall.copy(color = GlassAccentLight))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (i in 0 until totalCount) {
                                    val segmentColor = when { i < currentIndex -> GlassSuccess; i == currentIndex -> GlassAccent; else -> GlassPanelBorder }
                                    Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(segmentColor))
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp).clip(RoundedCornerShape(24.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(24.dp)).padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(text = currentDhikr.text, style = MaterialTheme.typography.titleLarge.copy(lineHeight = 36.sp, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GlassWhite), textAlign = TextAlign.Center)
                                if (currentDhikr.benefit.isNotEmpty()) {
                                    Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(GlassPanelBorder).padding(horizontal = 16.dp, vertical = 10.dp)) {
                                        Text(text = "الفضل: ${currentDhikr.benefit}", style = MaterialTheme.typography.bodySmall.copy(color = GlassAccentLight), textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.size(115.dp).clip(CircleShape).background(GlassAccent.copy(alpha = 0.2f)).border(2.dp, GlassAccent, CircleShape).clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (curCountLeft > 1) { currentCountsLeft[currentIndex] = curCountLeft - 1 } 
                            else {
                                currentCountsLeft[currentIndex] = 0
                                if (currentIndex < totalCount - 1) { currentIndex += 1 } else { isFinished = true }
                            }
                        }, contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$curCountLeft", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = GlassWhite)
                                Text(if (isArabic) "متبقي" else "Left", fontSize = 11.sp, color = GlassWhite.copy(alpha = 0.8f))
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { if (currentIndex > 0) { currentIndex -= 1; currentCountsLeft[currentIndex] = athkarList[currentIndex].count } }, enabled = currentIndex > 0) { Text(if (isArabic) "السابق" else "Previous", color = if (currentIndex > 0) GlassAccent else Color.Gray) }
                            TextButton(onClick = { currentCountsLeft[currentIndex] = 0; if (currentIndex < totalCount - 1) { currentIndex += 1 } else { isFinished = true } }) { Text(if (isArabic) "تخطي" else "Skip", color = GlassAccent) }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GlassSuccess, modifier = Modifier.size(80.dp))
                            Text(text = if (isArabic) "تقبل الله طاعتك!" else "Accepted!", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = GlassSuccess))
                            Button(onClick = onComplete, colors = ButtonDefaults.buttonColors(containerColor = GlassSuccess), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (isArabic) "إتمام" else "Done", color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorshipCelebrationDialog(title: String, description: String, isArabic: Boolean, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        CelebrationEffect()
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = { Text(description, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(if (isArabic) "متابعة" else "Continue") } }
        )
    }
}

data class Particle(var x: Float, var y: Float, var speedY: Float, var speedX: Float, val color: Color, val isBalloon: Boolean, val size: Float)

@Composable
fun CelebrationEffect() {
    val particles = remember { 
        List(60) { 
            val isBalloon = Random.nextFloat() > 0.7f
            Particle(
                x = Random.nextFloat() * 1000f, y = if (isBalloon) 2500f + Random.nextFloat() * 500f else -100f - Random.nextFloat() * 500f, 
                speedY = if (isBalloon) -(3f + Random.nextFloat() * 4f) else (5f + Random.nextFloat() * 6f), speedX = (Random.nextFloat() - 0.5f) * 4f, 
                color = listOf(Color(0xFF818CF8), Color(0xFF34D399), Color(0xFFFBBF24)).random(),
                isBalloon = isBalloon, size = if (isBalloon) 40f + Random.nextFloat() * 20f else 10f + Random.nextFloat() * 10f
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
        trigger.let {
            particles.forEach { p ->
                if (p.isBalloon) drawCircle(color = p.color.copy(alpha = 0.8f), radius = p.size, center = Offset(p.x, p.y))
                else drawRect(color = p.color, topLeft = Offset(p.x, p.y), size = Size(p.size, p.size))
            }
        }
    }
}

@Composable
fun StatsScreen(isArabic: Boolean, onDismiss: () -> Unit) {
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
                    if (dateIdx != -1) {
                        list.add(DailyRecord(
                            date = cursor.getString(dateIdx), fajrDone = cursor.getInt(cursor.getColumnIndex("fajrDone")) == 1, 
                            dhuhrDone = cursor.getInt(cursor.getColumnIndex("dhuhrDone")) == 1, asrDone = cursor.getInt(cursor.getColumnIndex("asrDone")) == 1, 
                            maghribDone = cursor.getInt(cursor.getColumnIndex("maghribDone")) == 1, ishaDone = cursor.getInt(cursor.getColumnIndex("ishaDone")) == 1,
                            quranPages = cursor.getInt(cursor.getColumnIndex("quranPages")), morningDhikrDone = cursor.getInt(cursor.getColumnIndex("morningDhikrDone")) == 1, 
                            eveningDhikrDone = cursor.getInt(cursor.getColumnIndex("eveningDhikrDone")) == 1
                        ))
                    }
                }
                cursor.close(); history = list
            } catch (e: Exception) { history = emptyList() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isArabic) "الإحصائيات" else "Stats", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (history.isEmpty()) { Text(if (isArabic) "لا توجد بيانات مسجلة." else "No records.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } 
                else {
                    val totalScore = history.sumOf { it.calculatePoints() }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(GlassAccent.copy(alpha = 0.1f)).padding(8.dp)) {
                            Text(if (isArabic) "النقاط" else "Points", style = MaterialTheme.typography.labelSmall)
                            Text("$totalScore", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = GlassAccent))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(GlassSuccess.copy(alpha = 0.1f)).padding(8.dp)) {
                            Text(if (isArabic) "الأيام" else "Days", style = MaterialTheme.typography.labelSmall)
                            Text("${history.size}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = GlassSuccess))
                        }
                    }
                    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha=0.05f)).padding(16.dp)) {
                        val barWidth = size.width / (history.size.coerceAtLeast(1) * 2f)
                        val maxH = size.height
                        history.forEachIndexed { i, rec ->
                            val pts = rec.calculatePoints().toFloat()
                            val h = (pts / 100f) * maxH
                            val x = i * (barWidth * 2) + barWidth / 2
                            drawRect(color = if (pts >= 100) GlassSuccess else GlassAccent, topLeft = Offset(x, maxH - h), size = Size(barWidth, h))
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(if (isArabic) "إغلاق" else "Close") } }
    )
}

@Composable
fun HisnAlMuslimDialog(category: String, isArabic: Boolean, onDismiss: () -> Unit) {
    val title = when(category) { "sleep" -> "أذكار النوم"; "wakeup" -> "الاستيقاظ"; "food" -> "الطعام"; "travel" -> "السفر"; "home" -> "المنزل"; "mosque" -> "المسجد"; "toilet" -> "الخلاء"; "rain" -> "المطر"; else -> "" }
    val text = when(category) {
        "sleep" -> "بِاسْمِكَ رَبِّـي وَضَعْـتُ جَنْـبي، وَبِكَ أَرْفَعُـه..."
        "wakeup" -> "الحَمْـدُ لِلّهِ الّذي أَحْـيانا بَعْـدَ ما أَماتَـنا وَإليه النُّـشور."
        "food" -> "بِسْمِ اللَّهِ. (عند البدء)\n\nالْحَمْدُ لِلَّهِ الَّذِي أَطْعَمَنِي... (عند الانتهاء)"
        "travel" -> "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ."
        "home" -> "بِسْـمِ اللهِ وَلَجْنـا، وَبِسْـمِ اللهِ خَـرَجْنـا..."
        "mosque" -> "اللَّهُمَّ افْتَحْ لِي أَبْوَابَ رَحْمَتِكَ."
        "toilet" -> "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْخُبْثِ وَالْخَبَائِثِ."
        "rain" -> "اللَّهُمَّ صَيِّباً نَافِعاً."
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 32.sp, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(if (isArabic) "إغلاق" else "Close") } }
    )
}
