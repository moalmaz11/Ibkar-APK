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
import com.example.data.DailyRecord
import com.example.data.DateHelper
import com.example.data.WorshipDatabase
import com.example.ui.WorshipViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SuccessGreen
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
        else onResult(false, "جاري البحث عن الأقمار.. حدد يدوياً", 0f, 0f)
    } catch (e: SecurityException) {
        onResult(false, "صلاحية الموقع غير ممنوحة", 0f, 0f)
    }
}

// ==========================================
// 2. الواجهة الرئيسية
// ==========================================
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
            var useDarkTheme by rememberSaveable { mutableStateOf(themePrefs.getBoolean("dark_theme", true)) }
            var isArabic by rememberSaveable { mutableStateOf(themePrefs.getBoolean("is_arabic", true)) }

            MyApplicationTheme(darkTheme = useDarkTheme) {
                CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        MainAppContent(
                            darkTheme = useDarkTheme, isArabic = isArabic,
                            onToggleTheme = { useDarkTheme = !useDarkTheme; themePrefs.edit().putBoolean("dark_theme", useDarkTheme).apply() },
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
    override fun onResume() {
        super.onResume()
        try {
            sendBroadcast(Intent(this, Class.forName("com.example.widget.CountdownWidgetProvider")).apply { action = "com.example.widget.REFRESH_COUNTDOWN" })
            sendBroadcast(Intent(this, Class.forName("com.example.widget.PrayerTimesWidgetProvider")).apply { action = "com.example.widget.REFRESH_TIMES" })
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

    var cityNameState by remember { 
        mutableStateOf(if (isArabic) notificationSettingsPrefs.getString("user_city_name_ar", "القاهرة") ?: "القاهرة" else notificationSettingsPrefs.getString("user_city_name_en", "Cairo") ?: "Cairo")
    }

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

    var hasNotifyPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        hasNotifyPermission = isGranted
        if (isGranted) com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) { }
            }
        }

        val hasCoarseLocation = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasCoarseLocation) {
            delay(800L); locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
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
            if (!hasNotifyPermission) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
            }
        } else {
            com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
        }
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
                                Text(text = "📍 $cityNameState", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { showStatsDialog = true }, modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))) {
                            Text("📊", fontSize = 18.sp)
                        }
                        IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            item { MotivationHeaderCard(totalDoneItems, isQuranDone, activeRecord.fajrDone, isTodaySelected, darkTheme, isArabic) }

            item {
                val rankSpec = viewModel.getRankInfo(dailyPoints)
                val englishRank = when {
                    dailyPoints >= 100 -> "Foremost in Good Deeds"
                    dailyPoints >= 80 -> "Righteous Believer"
                    dailyPoints >= 60 -> "Devoted Worshipper"
                    dailyPoints >= 40 -> "Steadfast Muslim"
                    dailyPoints >= 20 -> "Mindful Believer"
                    else -> "Seeker of Reward"
                }

                Card(modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp)).border(1.dp, if (darkTheme) Color(0xFF1E293B) else Color(0xFFB0CDE8), RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                    Box(modifier = Modifier.fillMaxWidth().background(brush = Brush.linearGradient(colors = if (darkTheme) listOf(Color(0xFF064E3B), Color(0xFF0F172A)) else listOf(Color(0xFFD1FAE5), Color(0xFFFFFFFF))))) {
                        CrescentMoonIcon(modifier = Modifier.size(110.dp).align(Alignment.BottomEnd).padding(bottom = 12.dp, end = 12.dp).scale(1.3f), color = (if (darkTheme) Color(0xFF10B981) else Color(0xFF065F46)).copy(alpha = 0.08f))
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (darkTheme) Color(0xFF10B981) else Color(0xFF065F46)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                        Text(text = if (isArabic) "المستخدم" else "User", style = MaterialTheme.typography.labelSmall.copy(color = if (darkTheme) Color(0xFF022C22) else Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clickable { inputName = profile?.userName ?: if (isArabic) "عابد لله" else "Mostafa"; showEditNameDialog = true }) {
                                        Text(text = profile?.userName ?: if (isArabic) "عابد لله" else "Mostafa", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = if (darkTheme) Color.White else Color(0xFF065F46), fontSize = 22.sp))
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = (if (darkTheme) Color(0xFF34D399) else Color(0xFF065F46)).copy(alpha = 0.8f))
                                    }
                                }
                                Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (darkTheme) Color.White.copy(alpha = 0.12f) else Color(0xFF065F46).copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(text = if (streak > 0) { if (isArabic) "التتابع: $streak أيام" else "Streak: $streak days" } else { if (isArabic) "ابدأ التتابع اليوم" else "Start streak today" }, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = if (darkTheme) Color(0xFF34D399) else Color(0xFF065F46)))
                                        Text(text = "🔥", style = MaterialTheme.typography.bodySmall, fontSize = 13.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(text = if (isArabic) "الرتبة اليومية" else "Daily Rank", style = MaterialTheme.typography.labelMedium.copy(color = (if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF065F46)).copy(alpha = 0.8f), fontWeight = FontWeight.Medium))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = if (isArabic) rankSpec.title else englishRank, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, color = if (darkTheme) Color.White else Color(0xFF047857)))
                                }
                                Column(horizontalAlignment = if (isArabic) Alignment.End else Alignment.Start) {
                                    Text(text = if (isArabic) "نقاط اليوم" else "Today's Points", style = MaterialTheme.typography.labelMedium.copy(color = (if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF065F46)).copy(alpha = 0.8f), fontWeight = FontWeight.Medium))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(text = "$dailyPoints", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = if (darkTheme) Color.White else Color(0xFF065F46)))
                                        Text(text = "/ 100", style = MaterialTheme.typography.bodyLarge.copy(color = (if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF065F46)).copy(alpha = 0.8f), fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                            val dayProgress = totalDoneItems.toFloat() / 8f
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = if (isArabic) "نسبة إتمام عبادات اليوم" else "Daily Worship Progress", style = MaterialTheme.typography.labelMedium.copy(color = (if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF065F46)).copy(alpha = 0.8f), fontWeight = FontWeight.Bold))
                                    Text(text = "${(dayProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, color = if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF065F46)))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(progress = { dayProgress }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), color = if (darkTheme) Color(0xFF10B981) else Color(0xFF059669), trackColor = (if (darkTheme) Color.White else Color(0xFF065F46)).copy(alpha = 0.15f))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = if (totalDoneItems == 8) { if (isArabic) "ما شاء الله! أتممت جميع عبادات اليوم بالكامل 🎉" else "Mashallah! All daily worships completed 🎉" } else { if (isArabic) "أتممت $totalDoneItems من 8 عبادات، واصل الطاعة!" else "Completed $totalDoneItems of 8, keep going!" }, style = MaterialTheme.typography.labelSmall.copy(color = (if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF065F46)).copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth().border(1.dp, if (darkTheme) Color(0xFF1E293B) else Color(0xFFD4DCE5), RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF111827) else Color(0xFFFFFFFF))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.selectPreviousDay() }) { Text(text = if (isArabic) "◀" else "▶", style = MaterialTheme.typography.bodyLarge, color = if (darkTheme) Color(0xFF10B981) else Color(0xFF059669), fontSize = 18.sp) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(text = displayDate, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = if (darkTheme) Color.White else Color(0xFF111318)), textAlign = TextAlign.Center)
                            if (!isTodaySelected) { Text(text = if (isArabic) "العودة لليوم" else "Back to Today", style = MaterialTheme.typography.labelSmall.copy(color = if (darkTheme) Color(0xFF10B981) else Color(0xFF059669), fontWeight = FontWeight.Bold, fontSize = 11.sp), modifier = Modifier.clickable { viewModel.selectToday() }.padding(vertical = 2.dp)) }
                            else { Text(text = if (isArabic) "اليوم" else "Today", style = MaterialTheme.typography.labelSmall.copy(color = if (darkTheme) Color(0xFF909196) else Color(0xFF5A5E6B), fontWeight = FontWeight.Medium)) }
                        }
                        val isNextDisabled = selectedDate >= todayStr
                        IconButton(onClick = { viewModel.selectNextDay() }, enabled = !isNextDisabled) { Text(text = if (isArabic) "▶" else "◀", style = MaterialTheme.typography.bodyLarge, color = (if (darkTheme) Color(0xFF10B981) else Color(0xFF059669)).copy(alpha = if (isNextDisabled) 0.3f else 1f), fontSize = 18.sp) }
                    }
                }
            }

            if (!isTodaySelected) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(text = if (isArabic) "🔒 سجل الأيام السابقة للعرض فقط حفاظاً على دقة البيانات" else "🔒 Past records are view-only to preserve data accuracy.", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = if (darkTheme) Color(0xFF94A3B8) else Color(0xFF475569)), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = if (isArabic) "الصلوات الخمس المفروضة" else "The Five Obligatory Prayers", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary), modifier = Modifier.padding(horizontal = 4.dp))
                    if (isTodaySelected) upcomingPrayerInfoState?.let { NextPrayerCountdownCard(upcoming = it, darkTheme = darkTheme, isArabic = isArabic) }

                    PrayerItemRow(if (isArabic) "الفجر" else "Fajr", if (isArabic) "ركعتان مفروضتان" else "2 Fard Rak'ahs", activeRecord.fajrDone, "fajr", isArabic, darkTheme, getPrayerTimeStr("fajr")) { if (isTodaySelected) viewModel.togglePrayer("fajr") }
                    PrayerItemRow(if (isArabic) "الظهر" else "Dhuhr", if (isArabic) "أربع ركعات مفروضة" else "4 Fard Rak'ahs", activeRecord.dhuhrDone, "dhuhr", isArabic, darkTheme, getPrayerTimeStr("dhuhr")) { if (isTodaySelected) viewModel.togglePrayer("dhuhr") }
                    PrayerItemRow(if (isArabic) "العصر" else "Asr", if (isArabic) "أربع ركعات مفروضة" else "4 Fard Rak'ahs", activeRecord.asrDone, "asr", isArabic, darkTheme, getPrayerTimeStr("asr")) { if (isTodaySelected) viewModel.togglePrayer("asr") }
                    PrayerItemRow(if (isArabic) "المغرب" else "Maghrib", if (isArabic) "ثلاث ركعات مفروضة" else "3 Fard Rak'ahs", activeRecord.maghribDone, "maghrib", isArabic, darkTheme, getPrayerTimeStr("maghrib")) { if (isTodaySelected) viewModel.togglePrayer("maghrib") }
                    PrayerItemRow(if (isArabic) "العشاء" else "Isha", if (isArabic) "أربع ركعات مفروضة" else "4 Fard Rak'ahs", activeRecord.ishaDone, "isha", isArabic, darkTheme, getPrayerTimeStr("isha")) { if (isTodaySelected) viewModel.togglePrayer("isha") }

                    AnimatedVisibility(visible = prayersDoneCount == 5) {
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)).padding(10.dp)) {
                            Text(text = if (isArabic) "مبارك! أتممت جميع الصلوات المفروضة (+20 نقطة مكافأة)" else "Congrats! All prayers completed (+20 Bonus Points)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth().border(1.dp, if (darkTheme) Color(0xFF1E293B) else Color(0xFFD4DCE5), RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF111827) else Color(0xFFFFFFFF))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (darkTheme) Color(0xFF065F46).copy(alpha = 0.4f) else Color(0xFFD1FAE5)), contentAlignment = Alignment.Center) { Text("📖", fontSize = 18.sp) }
                                Column {
                                    Text(text = if (isArabic) "ورد القرآن الكريم" else "Quran Wird", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = if (darkTheme) Color.White else Color(0xFF111318)))
                                    Text(text = if (isArabic) "صفحة واحدة على الأقل يومياً" else "At least one page daily", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)))
                                }
                            }
                            Box(modifier = Modifier.clip(CircleShape).background(if (darkTheme) Color(0xFF10B981) else Color(0xFF065F46)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(text = if (isArabic) "+1 نقطة / صفحة" else "+1 Point/Page", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = if (darkTheme) Color(0xFF022C22) else Color.White))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = if (isArabic) "عدد الصفحات المقروءة:" else "Pages Read:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                IconButton(onClick = { if (isTodaySelected) viewModel.setQuranPages(activeRecord.quranPages - 1) }, enabled = isTodaySelected, modifier = Modifier.size(36.dp).clip(CircleShape).background(if (darkTheme) Color(0xFF1E293B) else Color(0xFFF4F6FA))) { Text("-", fontWeight = FontWeight.Bold, color = (if (darkTheme) Color(0xFF34D399) else Color(0xFF065F46)).copy(alpha = if (isTodaySelected) 1f else 0.35f), fontSize = 18.sp) }
                                Text("${activeRecord.quranPages}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = if (darkTheme) Color(0xFF34D399) else Color(0xFF065F46)), modifier = Modifier.widthIn(min = 28.dp), textAlign = TextAlign.Center)
                                IconButton(onClick = { if (isTodaySelected) viewModel.setQuranPages(activeRecord.quranPages + 1) }, enabled = isTodaySelected, modifier = Modifier.size(36.dp).clip(CircleShape).background(if (darkTheme) Color(0xFF10B981) else Color(0xFF059669))) { Text("+", fontWeight = FontWeight.Bold, color = (if (darkTheme) Color(0xFF022C22) else Color.White).copy(alpha = if (isTodaySelected) 1f else 0.35f), fontSize = 18.sp) }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth().border(1.dp, if (darkTheme) Color(0xFF1E293B) else Color(0xFFD4DCE5), RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF111827) else Color(0xFFFFFFFF))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (darkTheme) Color(0xFF065F46).copy(alpha = 0.4f) else Color(0xFFD1FAE5)), contentAlignment = Alignment.Center) { Text("🤲", fontSize = 18.sp) }
                                Column {
                                    Text(text = if (isArabic) "الأذكار اليومية" else "Daily Dhikr", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = if (darkTheme) Color.White else Color(0xFF111318)))
                                    Text(text = if (isArabic) "حصن المسلم اليومي" else "Daily Hisn al-Muslim", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)))
                                }
                            }
                            Box(modifier = Modifier.clip(CircleShape).background(if (darkTheme) Color(0xFF10B981) else Color(0xFF065F46)).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(text = if (isArabic) "+5 نقاط لكل ذكر" else "+5 Points", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = if (darkTheme) Color(0xFF022C22) else Color.White)) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (activeRecord.morningDhikrDone) { if (darkTheme) Color(0xFF064E3B) else Color(0xFFD1FAE5) } else { if (darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9) }).clickable { activeDhikrTypeForReading = "morning" }.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🌅", fontSize = 16.sp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = if (isArabic) "أذكار الصباح (انقر للقراءة)" else "Morning Dhikr (Tap)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = if (darkTheme) Color.White else Color(0xFF1F2937)))
                                }
                            }
                            Checkbox(checked = activeRecord.morningDhikrDone, enabled = isTodaySelected, onCheckedChange = { if (isTodaySelected) viewModel.toggleMorningDhikr() }, colors = CheckboxDefaults.colors(checkedColor = SuccessGreen, uncheckedColor = if (darkTheme) Color(0xFF6B7280) else Color(0xFF9CA3AF)))
                        }
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (activeRecord.eveningDhikrDone) { if (darkTheme) Color(0xFF064E3B) else Color(0xFFD1FAE5) } else { if (darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9) }).clickable { activeDhikrTypeForReading = "evening" }.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🌇", fontSize = 16.sp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = if (isArabic) "أذكار المساء (انقر للقراءة)" else "Evening Dhikr (Tap)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = if (darkTheme) Color.White else Color(0xFF1F2937)))
                                }
                            }
                            Checkbox(checked = activeRecord.eveningDhikrDone, enabled = isTodaySelected, onCheckedChange = { if (isTodaySelected) viewModel.toggleEveningDhikr() }, colors = CheckboxDefaults.colors(checkedColor = SuccessGreen, uncheckedColor = if (darkTheme) Color(0xFF6B7280) else Color(0xFF9CA3AF)))
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth().border(1.dp, if (darkTheme) Color(0xFF1E293B) else Color(0xFFD4DCE5), RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF111827) else Color(0xFFFFFFFF))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (darkTheme) Color(0xFF065F46).copy(alpha = 0.4f) else Color(0xFFD1FAE5)), contentAlignment = Alignment.Center) { Text("🛡️", fontSize = 18.sp) }
                                Column {
                                    Text(text = if (isArabic) "حصن المسلم" else "Hisn Al-Muslim", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = if (darkTheme) Color.White else Color(0xFF111318)))
                                    Text(text = if (isArabic) "أذكار متنوعة للحفظ والقراءة" else "Various daily supplications", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)))
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        val hisnCats = listOf(
                            "sleep" to (if (isArabic) "أذكار النوم" else "Sleep") to "💤",
                            "wakeup" to (if (isArabic) "الاستيقاظ" else "Wake up") to "☀️",
                            "food" to (if (isArabic) "الطعام والشراب" else "Food") to "🍽️",
                            "travel" to (if (isArabic) "الركوب والسفر" else "Travel") to "🚗",
                            "home" to (if (isArabic) "المنزل" else "Home") to "🏠",
                            "mosque" to (if (isArabic) "المسجد" else "Mosque") to "🕌",
                            "toilet" to (if (isArabic) "الخلاء" else "Toilet") to "💧",
                            "rain" to (if (isArabic) "المطر" else "Rain") to "🌧️"
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            hisnCats.chunked(2).forEach { rowCats ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowCats.forEach { cat ->
                                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9)).clickable { activeHisnCategory = cat.first.first }.padding(12.dp), contentAlignment = Alignment.Center) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(cat.second, fontSize = 16.sp)
                                                Text(cat.first.second, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = if (darkTheme) Color.White else Color(0xFF1F2937)))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth().border(1.dp, if (darkTheme) Color(0xFF1E293B) else Color(0xFFD4DCE5), RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF111827) else Color(0xFFFFFFFF))) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(if (darkTheme) Color(0xFF065F46).copy(alpha = 0.4f) else Color(0xFFD1FAE5)), contentAlignment = Alignment.Center) { Text("📿", fontSize = 18.sp) }
                                Column {
                                    Text(text = if (isArabic) "المسبحة الإلكترونية" else "Digital Rosary", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = if (darkTheme) Color.White else Color(0xFF111318)))
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        Box(modifier = Modifier.size(130.dp).clip(CircleShape).background(brush = Brush.radialGradient(colors = if (darkTheme) listOf(Color(0xFF065F46), Color(0xFF022C22)) else listOf(Color(0xFFD1FAE5), Color(0xFFFFFFFF)))).border(3.dp, if (darkTheme) Color(0xFF10B981) else Color(0xFF059669), CircleShape).clickable { if (isTodaySelected) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.incrementDhikr() } }, contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${activeRecord.dhikrCount}", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, color = if (darkTheme) Color.White else Color(0xFF065F46), fontSize = 34.sp))
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (isTodaySelected) Text(text = if (isArabic) "إعادة ضبط العداد ↺" else "Reset ↺", style = MaterialTheme.typography.labelSmall.copy(color = if (darkTheme) Color(0xFF34D399) else Color(0xFF059669), fontWeight = FontWeight.Bold), modifier = Modifier.clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.resetDhikr() }.padding(4.dp))
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showStatsDialog) {
        StatsScreen(darkTheme = darkTheme, isArabic = isArabic, onDismiss = { showStatsDialog = false })
    }

    if (activeHisnCategory != null) {
        HisnAlMuslimDialog(category = activeHisnCategory!!, darkTheme = darkTheme, isArabic = isArabic, onDismiss = { activeHisnCategory = null })
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text(text = if (isArabic) "الإعدادات" else "Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF1E293B) else Color(0xFFF7F9FC)), border = BorderStroke(1.dp, if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))) {
                        Row(modifier = Modifier.fillMaxWidth().clickable { onToggleLanguage() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Switch(checked = !isArabic, onCheckedChange = { onToggleLanguage() }, modifier = Modifier.scale(0.85f))
                            Text(text = if (isArabic) "English Language" else "اللغة العربية")
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF1E293B) else Color(0xFFF7F9FC)), border = BorderStroke(1.dp, if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))) {
                        Row(modifier = Modifier.fillMaxWidth().clickable { onToggleTheme() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Switch(checked = !darkTheme, onCheckedChange = { onToggleTheme() }, modifier = Modifier.scale(0.85f))
                            Text(text = if (isArabic) "الوضع الفاتح" else "Light Mode")
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF1E293B) else Color(0xFFF7F9FC)), border = BorderStroke(1.dp, if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Switch(checked = hasNotifyPermission && notifyAll, onCheckedChange = { checked -> if (!hasNotifyPermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else { notifyAll = checked; notificationSettingsPrefs.edit().putBoolean("notify_all", checked).apply(); if (checked) com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) } }, modifier = Modifier.scale(0.85f))
                                Text(text = if (isArabic) "تفعيل الإشعارات" else "Enable Notifications", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            if (hasNotifyPermission && notifyAll) {
                                HorizontalDivider(color = if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                                Button(onClick = { showNotificationDetailsDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f), contentColor = MaterialTheme.colorScheme.onSurface), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Text(text = if (isArabic) "تخصيص أوقات التنبيهات" else "Customize Alert Times", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF1E293B) else Color(0xFFF7F9FC)), border = BorderStroke(1.dp, if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(text = if (isArabic) "الموقع الجغرافي" else "Location", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), shape = RoundedCornerShape(8.dp)) {
                                    Text(text = if (isArabic) "تحديث تلقائي" else "Auto GPS")
                                }
                                Button(onClick = { showManualLocationDialog = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(8.dp)) {
                                    Text(text = if (isArabic) "تحديد يدوي" else "Manual")
                                }
                            }
                        }
                    }
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
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Switch(checked = notifyPrayers, onCheckedChange = { notifyPrayers = it; notificationSettingsPrefs.edit().putBoolean("notify_prayers", it).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, colors = SwitchDefaults.colors(checkedThumbColor = SuccessGreen))
                        Text(text = if (isArabic) "تنبيهات الصلوات" else "Adhan Alerts", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Switch(checked = notifyMorningDhikr, onCheckedChange = { notifyMorningDhikr = it; notificationSettingsPrefs.edit().putBoolean("notify_morning_dhikr", it).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, colors = SwitchDefaults.colors(checkedThumbColor = SuccessGreen))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = if (isArabic) "أذكار الصباح" else "Morning Dhikr", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            Text(text = formatTime(currentMHour, currentMMin), color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { TimePickerDialog(context, { _, h, m -> currentMHour = h; currentMMin = m; notificationSettingsPrefs.edit().putInt("morning_dhikr_hour", h).putInt("morning_dhikr_minute", m).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, currentMHour, currentMMin, false).show() }.padding(4.dp))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Switch(checked = notifyEveningDhikr, onCheckedChange = { notifyEveningDhikr = it; notificationSettingsPrefs.edit().putBoolean("notify_evening_dhikr", it).apply(); com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, colors = SwitchDefaults.colors(checkedThumbColor = SuccessGreen))
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

    if (showDaily100Celebration) {
        WorshipCelebrationDialog(
            title = if (isArabic) "مبارك! حققت العلامة الكاملة" else "Congrats! Perfect Score",
            description = if (isArabic) "أتممت جميع عبادات اليوم وحققت 100 نقطة كاملة." else "You completed all daily worships and achieved 100 points.",
            darkTheme = darkTheme, isArabic = isArabic,
            onDismiss = { celebrationPrefs.edit().putString("daily_100_last_date", record?.date ?: "").apply(); showDaily100Celebration = false }
        )
    }

    if (showTotal100Celebration) {
        WorshipCelebrationDialog(
            title = if (isArabic) "إنجاز مبارك!" else "Blessed Achievement!",
            description = if (isArabic) "تجاوزت حاجز 100 نقطة في مجموع طاعاتك." else "You have surpassed 100 total points. Keep going!",
            darkTheme = darkTheme, isArabic = isArabic,
            onDismiss = { celebrationPrefs.edit().putBoolean("total_100_celebrated", true).apply(); showTotal100Celebration = false }
        )
    }
}

// ==========================================
// 3. الدوال والمكونات المساعدة
// ==========================================

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

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(modifier = Modifier.fillMaxWidth().background(brush = Brush.linearGradient(colors = skyGradient)).padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(text = if (isArabic) "الوقت المتبقي للأذان:" else "Time until Adhan:", style = MaterialTheme.typography.labelSmall.copy(color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.7f), fontWeight = FontWeight.Bold))
                    Text(text = countdownFormatted, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 24.sp, color = if (isDark) Color(0xFFFFD54F) else Color(0xFF065F46)))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = upcoming.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = if (isDark) Color.White else Color(0xFF111318)))
                    Box(modifier = Modifier.clip(RoundedCornerShape(50.dp)).background((if (isDark) Color.White else Color.Black).copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(text = "${upcoming.timeStr}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color.Black))
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

    Card(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, borderStrokeColor, RoundedCornerShape(14.dp)).clickable { onToggle() }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 1.dp else 0.dp)) {
        Box(modifier = Modifier.fillMaxWidth().background(if (isDark) Color.Transparent else Color.White).background(brush = bgBrush)) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(if (isDone) SuccessGreen else Color.Transparent).border(width = 2.dp, color = if (isDone) SuccessGreen else (if (isDark) Color(0xFF5A6270) else Color(0xFFB0BEC5)), shape = CircleShape), contentAlignment = Alignment.Center) {
                    if (isDone) Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = if (isDone) { if (isDark) Color(0xFFD1FAE5) else Color(0xFF065F46) } else MaterialTheme.colorScheme.onSurface))
                        if (timeText != null) Text(text = timeText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black, color = if (isDone) { if (isDark) Color(0xFF34D399) else Color(0xFF059669) } else { if (isDark) Color(0xFFFFD54F) else Color(0xFF065F46) }))
                    }
                    Text(text = description, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), fontSize = 10.5.sp), maxLines = 1)
                }
            }
        }
    }
}

data class MotivationHeaderData(val title: String, val text: String, val icon: String, val badgeColor: Color)

@Composable
fun MotivationHeaderCard(totalDoneItems: Int, isQuranDone: Boolean, isFajrDone: Boolean, isTodaySelected: Boolean, darkTheme: Boolean = true, isArabic: Boolean) {
    val motivation = when {
        totalDoneItems == 8 -> MotivationHeaderData(if (isArabic) "هنيئاً لك التمام والكمال!" else "Congratulations on Perfection!", if (isArabic) "أتممت عباداتك اليومية كاملة." else "You have completed all daily worships.", "👑", Color(0xFFFFD700))
        totalDoneItems >= 5 -> MotivationHeaderData(if (isArabic) "همة عالية وخطى ثابتة" else "High Resolve", if (isArabic) "أنجزت معظم فرائض اليوم." else "You have accomplished most worships.", "🌟", Color(0xFF34D399))
        else -> MotivationHeaderData(if (isArabic) "يوم جديد.. وباب أجر مفتوح" else "A New Day", if (isArabic) "استعن بالله وحافظ على صلواتك." else "Maintain your daily prayers.", "🌿", Color(0xFF10B981))
    }
    Card(modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp)).border(width = 1.dp, color = if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0), shape = RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color(0xFF111827) else Color(0xFFFFFFFF))) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(motivation.badgeColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Text(text = motivation.icon, fontSize = 24.sp) }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = motivation.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = if (darkTheme) Color.White else Color(0xFF0F172A))
                Text(text = motivation.text, style = MaterialTheme.typography.bodySmall, color = if (darkTheme) Color(0xFF94A3B8) else Color(0xFF475569))
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
                    if (dateIdx != -1) {
                        list.add(DailyRecord(
                            date = cursor.getString(dateIdx), fajrDone = cursor.getInt(fIdx) == 1, dhuhrDone = cursor.getInt(dIdx) == 1,
                            asrDone = cursor.getInt(aIdx) == 1, maghribDone = cursor.getInt(mIdx) == 1, ishaDone = cursor.getInt(iIdx) == 1,
                            quranPages = cursor.getInt(qIdx), morningDhikrDone = cursor.getInt(mdIdx) == 1, eveningDhikrDone = cursor.getInt(edIdx) == 1
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
                    Text(if (isArabic) "لا توجد بيانات مسجلة." else "No records found.", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
        "wakeup" -> if (isArabic) "الاستيقاظ" else "Wake up"
        "food" -> if (isArabic) "الطعام والشراب" else "Food"
        "travel" -> if (isArabic) "الركوب والسفر" else "Travel"
        "home" -> if (isArabic) "المنزل" else "Home"
        "mosque" -> if (isArabic) "المسجد" else "Mosque"
        "toilet" -> if (isArabic) "الخلاء" else "Toilet"
        "rain" -> if (isArabic) "المطر" else "Rain"
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
