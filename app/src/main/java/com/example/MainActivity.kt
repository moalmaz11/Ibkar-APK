package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.DailyRecord
import com.example.data.DateHelper
import com.example.ui.WorshipViewModel
import android.content.Intent
import android.net.Uri
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Build
import android.Manifest
import android.location.Geocoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var initialDhikrTypeState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.notification.PrayerNotificationManager.createNotificationChannel(this)
        
        handleIntent(intent)
        
        setContent {
            val context = LocalContext.current
            val themePrefs = remember(context) { context.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE) }
            var useDarkTheme by rememberSaveable { mutableStateOf(themePrefs.getBoolean("dark_theme", true)) }

            MyApplicationTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppContent(
                        darkTheme = useDarkTheme,
                        onToggleTheme = {
                            val newValue = !useDarkTheme
                            useDarkTheme = newValue
                            themePrefs.edit().putBoolean("dark_theme", newValue).apply()
                        },
                        initialDhikrType = initialDhikrTypeState.value,
                        onInitialDhikrHandled = { initialDhikrTypeState.value = null }
                    )
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
        if (type != null) {
            initialDhikrTypeState.value = type
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val intent1 = Intent(this, Class.forName("com.example.widget.CountdownWidgetProvider")).apply {
                action = "com.example.widget.REFRESH_COUNTDOWN"
            }
            sendBroadcast(intent1)
            val intent2 = Intent(this, Class.forName("com.example.widget.PrayerTimesWidgetProvider")).apply {
                action = "com.example.widget.REFRESH_TIMES"
            }
            sendBroadcast(intent2)
        } catch (e: Exception) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    darkTheme: Boolean = isSystemInDarkTheme(),
    onToggleTheme: () -> Unit = {},
    viewModel: WorshipViewModel = viewModel(),
    initialDhikrType: String? = null,
    onInitialDhikrHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val record by viewModel.currentRecord.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val totalPoints by viewModel.totalPoints.collectAsStateWithLifecycle()
    val streak by viewModel.currentStreak.collectAsStateWithLifecycle()
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var activeDhikrTypeForReading by remember { mutableStateOf<String?>(null) }
    
    val celebrationPrefs = remember(context) {
        context.getSharedPreferences("celebration_prefs", android.content.Context.MODE_PRIVATE)
    }
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
            if (todayStr != lastCelebrated) {
                showDaily100Celebration = true
            }
        }
    }

    LaunchedEffect(totalPoints) {
        if (totalPoints >= 100 && !hasDismissedTotalCelebration) {
            val alreadyCelebrated = celebrationPrefs.getBoolean("total_100_celebrated", false)
            if (!alreadyCelebrated) {
                showTotal100Celebration = true
            }
        }
    }

    val notificationSettingsPrefs = remember(context) {
        context.getSharedPreferences("notification_settings", android.content.Context.MODE_PRIVATE)
    }
    var notifyAll by remember {
        mutableStateOf(notificationSettingsPrefs.getBoolean("notify_all", true))
    }
    var notifyPrayers by remember {
        mutableStateOf(notificationSettingsPrefs.getBoolean("notify_prayers", true))
    }
    var notifyMorningDhikr by remember {
        mutableStateOf(notificationSettingsPrefs.getBoolean("notify_morning_dhikr", true))
    }
    var notifyEveningDhikr by remember {
        mutableStateOf(notificationSettingsPrefs.getBoolean("notify_evening_dhikr", true))
    }
    var showNotificationDetailsDialog by remember { mutableStateOf(false) }

    var userLat by remember {
        mutableStateOf(notificationSettingsPrefs.getFloat("user_latitude", com.example.notification.PrayerTimeCalculator.DEFAULT_LATITUDE.toFloat()))
    }
    var userLng by remember {
        mutableStateOf(notificationSettingsPrefs.getFloat("user_longitude", com.example.notification.PrayerTimeCalculator.DEFAULT_LONGITUDE.toFloat()))
    }
    var prayerCalcMethod by remember {
        mutableStateOf(notificationSettingsPrefs.getInt("prayer_calc_method", 0))
    }

    // استخراج اسم المدينة
    var cityNameState by remember { mutableStateOf("جاري التحديد...") }
    LaunchedEffect(userLat, userLng) {
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale("ar"))
                val addresses = geocoder.getFromLocation(userLat.toDouble(), userLng.toDouble(), 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "موقعك الحالي"
                    cityNameState = city
                } else {
                    cityNameState = "موقعك الحالي"
                }
            } catch (e: Exception) {
                cityNameState = "موقعك الحالي"
            }
        }
    }

    val todayTimesRaw = remember(userLat, userLng, prayerCalcMethod) {
        val cal = com.example.notification.PrayerTimeCalculator.getLocalCalendar(userLat.toDouble(), userLng.toDouble())
        com.example.notification.PrayerTimeCalculator.calculatePrayerTimes(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            userLat.toDouble(),
            userLng.toDouble(),
            prayerCalcMethod
        )
    }

    var offsetMinutesVal by remember {
        mutableStateOf(notificationSettingsPrefs.getInt("prayer_offset_minutes", 0))
    }

    val todayTimes = remember(todayTimesRaw) { todayTimesRaw }

    fun getPrayerTimeStr(key: String): String {
        val t = todayTimes[key] ?: return ""
        val h12 = if (t.first % 12 == 0) 12 else t.first % 12
        val amPm = if (t.first >= 12) "م" else "ص"
        return "%d:%02d %s".format(h12, t.second, amPm)
    }

    val todayStr = DateHelper.getTodayDateString(context)
    val displayDate = DateHelper.getArabicDisplayDate(selectedDate)
    val isTodaySelected = selectedDate == todayStr

    var upcomingPrayerInfoState by remember(todayTimes) {
        mutableStateOf<UpcomingPrayerInfo?>(null)
    }

    // ترشيد استهلاك البطارية: تشغيل العدّاد بالثانية فقط عندما يكون اليوم الحالي معروضاً
    LaunchedEffect(todayTimes, userLat, userLng, isTodaySelected) {
        if (isTodaySelected) {
            while (true) {
                upcomingPrayerInfoState = getUpcomingPrayer(todayTimes, userLat.toDouble(), userLng.toDouble())
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                com.example.updateLocationAndPrayerTimes(context, notificationSettingsPrefs) { success, msg, newLat, newLng ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    if (success) {
                        userLat = newLat
                        userLng = newLng
                    }
                }
            } else {
                Toast.makeText(context, "تم رفض إذن الموقع.", Toast.LENGTH_LONG).show()
            }
        }
    )

    LaunchedEffect(initialDhikrType) {
        if (initialDhikrType != null) {
            activeDhikrTypeForReading = initialDhikrType
            onInitialDhikrHandled()
        }
    }

    var hasNotifyPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotifyPermission = isGranted
            if (isGranted) {
                com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
            }
        }
    )

    LaunchedEffect(Unit) {
        val hasCoarseLocation = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (!hasCoarseLocation) {
            kotlinx.coroutines.delay(800L)
            locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            com.example.updateLocationAndPrayerTimes(context, notificationSettingsPrefs) { success, msg, newLat, newLng ->
                if (success) {
                    userLat = newLat
                    userLng = newLng
                }
            }
        }

        kotlinx.coroutines.delay(1200L)
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
    val prayersDoneCount = listOf(
        activeRecord.fajrDone,
        activeRecord.dhuhrDone,
        activeRecord.asrDone,
        activeRecord.maghribDone,
        activeRecord.ishaDone
    ).count { it }
    val isQuranDone = activeRecord.quranPages > 0
    val totalDoneItems = prayersDoneCount + 
            (if (isQuranDone) 1 else 0) + 
            (if (activeRecord.morningDhikrDone) 1 else 0) + 
            (if (activeRecord.eveningDhikrDone) 1 else 0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (darkTheme) {
                        listOf(Color(0xFF0B0F19), Color(0xFF111827))
                    } else {
                        listOf(Color(0xFFF4F6FA), Color(0xFFE8EDF4))
                    }
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 660.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // الشريط العلوي
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "إِبْكَـار",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 24.sp
                            )
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "📍 $cityNameState",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "الإعدادات",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "الإصدار 1.0",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            // بطاقة التحفيز
            item {
                MotivationHeaderCard(
                    totalDoneItems = totalDoneItems,
                    isQuranDone = isQuranDone,
                    isFajrDone = activeRecord.fajrDone,
                    isTodaySelected = isTodaySelected,
                    darkTheme = darkTheme
                )
            }

            // بطاقة الهوية الإيمانية
            item {
                val rankSpec = viewModel.getRankInfo(dailyPoints)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(24.dp))
                        .border(
                            1.dp,
                            if (darkTheme) Color(0xFF1E293B) else Color(0xFFB0CDE8),
                            RoundedCornerShape(24.dp)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = if (darkTheme) {
                                        listOf(Color(0xFF064E3B), Color(0xFF0F172A))
                                    } else {
                                        listOf(Color(0xFFD1FAE5), Color(0xFFFFFFFF))
                                    }
                                )
                            )
                    ) {
                        CrescentMoonIcon(
                            modifier = Modifier
                                .size(110.dp)
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 12.dp, end = 12.dp)
                                .scale(1.3f),
                            color = (if (darkTheme) Color(0xFF10B981) else Color(0xFF065F46)).copy(alpha = 0.08f)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (darkTheme) Color(0xFF10B981) else Color(0xFF065F46))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "المستخدم",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (darkTheme) Color(0xFF022C22) else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .testTag("edit_profile_name_area")
                                            .clickable {
                                                inputName = profile?.userName ?: "عابد لله"
                                                showEditNameDialog = true
                                            }
                                    ) {
                                        Text(
                                            text = profile?.userName ?: "عابد لله",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                color = if (darkTheme) Color.White else Color(0xFF065F46),
                                                fontSize = 22.sp
                                            )
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "تعديل الاسم",
                                            modifier = Modifier.size(16.dp),
                                            tint = (if (darkTheme) Color(0xFF34D399) else Color(0xFF065F46)).copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (darkTheme) Color.White.copy(alpha = 0.12f) else Color(0xFF065F46).copy(alpha = 0.08f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (streak > 0) "التتابع: $streak أيام" else "ابدأ التتابع اليوم",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (darkTheme) Color(0xFF34D399) else Color(0xFF065F46)
                                            )
                                        )
                                        Text(
                                            text = "🔥",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "الرتبة اليومية",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = (if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF065F46)).copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = rankSpec.title,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            color = if (darkTheme) Color.White else Color(0xFF047857)
                                        )
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "نقاط اليوم",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = (if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF065F46)).copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "$dailyPoints",
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                color = if (darkTheme) Color.White else Color(0xFF065F46)
                                            )
                                        )
                                        Text(
                                            text = "/ 100",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = (if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF065F46)).copy(alpha = 0.8f),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }

                            val dayProgress = totalDoneItems.toFloat() / 8f
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "نسبة إتمام عبادات اليوم",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = (if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF065F46)).copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "${(dayProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            color = if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF065F46)
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { dayProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = if (darkTheme) Color(0xFF10B981) else Color(0xFF059669),
                                    trackColor = (if (darkTheme) Color.White else Color(0xFF065F46)).copy(alpha = 0.15f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (totalDoneItems == 8) {
                                        "ما شاء الله! أتممت جميع عبادات اليوم بالكامل 🎉"
                                    } else {
                                        "أتممت $totalDoneItems من 8 عبادات، واصل الطاعة!"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = (if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF065F46)).copy(alpha = 0.9f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // التنقل بالتقويم
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (darkTheme) Color(0xFF1E293B) else Color(0xFFD4DCE5),
                            RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (darkTheme) Color(0xFF111827) else Color(0xFFFFFFFF)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            modifier = Modifier.testTag("prev_day_button"),
                            onClick = { viewModel.selectPreviousDay() }
                        ) {
                            Text(
                                text = "◀",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (darkTheme) Color(0xFF10B981) else Color(0xFF059669),
                                fontSize = 18.sp
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = displayDate,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (darkTheme) Color.White else Color(0xFF111318)
                                ),
                                textAlign = TextAlign.Center
                            )
                            if (!isTodaySelected) {
                                Text(
                                    text = "العودة لليوم",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (darkTheme) Color(0xFF10B981) else Color(0xFF059669),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier
                                        .testTag("today_quick_link")
                                        .clickable { viewModel.selectToday() }
                                        .padding(vertical = 2.dp)
                                )
                            } else {
                                Text(
                                    text = "اليوم",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (darkTheme) Color(0xFF909196) else Color(0xFF5A5E6B),
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        val isNextDisabled = selectedDate >= todayStr
                        val nextButtonAlpha = if (isNextDisabled) 0.3f else 1f
                        IconButton(
                            modifier = Modifier.testTag("next_day_button"),
                            onClick = { viewModel.selectNextDay() },
                            enabled = !isNextDisabled
                        ) {
                            Text(
                                text = "▶",
                                style = MaterialTheme.typography.bodyLarge,
                                color = (if (darkTheme) Color(0xFF10B981) else Color(0xFF059669)).copy(alpha = nextButtonAlpha),
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            // شريط إشعار القفل عند استعراض يوم سابق
            if (!isTodaySelected) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "🔒 سجل الأيام السابقة للعرض فقط حفاظاً على دقة البيانات",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (darkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // قائمة الصلوات
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "الصلوات الخمس المفروضة",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    if (isTodaySelected) {
                        upcomingPrayerInfoState?.let { upcoming ->
                            NextPrayerCountdownCard(upcoming = upcoming, darkTheme = darkTheme)
                        }
                    }

                    PrayerItemRow(
                        name = "الفجر",
                        description = "ركعتان مفروضتان مع سنة الفجر",
                        isDone = activeRecord.fajrDone,
                        tag = "fajr",
                        iconString = "🌅",
                        pointLabel = "+12 نقطة",
                        darkTheme = darkTheme,
                        timeText = getPrayerTimeStr("fajr"),
                        onToggle = { if (isTodaySelected) viewModel.togglePrayer("fajr") }
                    )
                    PrayerItemRow(
                        name = "الظهر",
                        description = "أربع ركعات مفروضة",
                        isDone = activeRecord.dhuhrDone,
                        tag = "dhuhr",
                        iconString = "☀️",
                        pointLabel = "+12 نقطة",
                        darkTheme = darkTheme,
                        timeText = getPrayerTimeStr("dhuhr"),
                        onToggle = { if (isTodaySelected) viewModel.togglePrayer("dhuhr") }
                    )
                    PrayerItemRow(
                        name = "العصر",
                        description = "أربع ركعات مفروضة",
                        isDone = activeRecord.asrDone,
                        tag = "asr",
                        iconString = "🌤️",
                        pointLabel = "+12 نقطة",
                        darkTheme = darkTheme,
                        timeText = getPrayerTimeStr("asr"),
                        onToggle = { if (isTodaySelected) viewModel.togglePrayer("asr") }
                    )
                    PrayerItemRow(
                        name = "المغرب",
                        description = "ثلاث ركعات مفروضة",
                        isDone = activeRecord.maghribDone,
                        tag = "maghrib",
                        iconString = "🌇",
                        pointLabel = "+12 نقطة",
                        darkTheme = darkTheme,
                        timeText = getPrayerTimeStr("maghrib"),
                        onToggle = { if (isTodaySelected) viewModel.togglePrayer("maghrib") }
                    )
                    PrayerItemRow(
                        name = "العشاء",
                        description = "أربع ركعات مفروضة مع الشفع والوتر",
                        isDone = activeRecord.ishaDone,
                        tag = "isha",
                        iconString = "🌙",
                        pointLabel = "+12 نقطة",
                        darkTheme = darkTheme,
                        timeText = getPrayerTimeStr("isha"),
                        onToggle = { if (isTodaySelected) viewModel.togglePrayer("isha") }
                    )

                    AnimatedVisibility(visible = prayersDoneCount == 5) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "مبارك! أتممت جميع الصلوات المفروضة (+20 نقطة مكافأة)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ورد القرآن
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (darkTheme) Color(0xFF1E293B) else Color(0xFFD4DCE5),
                            RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (darkTheme) Color(0xFF111827) else Color(0xFFFFFFFF)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (darkTheme) Color(0xFF065F46).copy(alpha = 0.4f) else Color(0xFFD1FAE5)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "📖", fontSize = 18.sp)
                                }
                                Column {
                                    Text(
                                        text = "ورد القرآن الكريم",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (darkTheme) Color.White else Color(0xFF111318)
                                        )
                                    )
                                    Text(
                                        text = "صفحة واحدة على الأقل يومياً",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (darkTheme) Color(0xFF10B981) else Color(0xFF065F46))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "+1 نقطة / صفحة",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (darkTheme) Color(0xFF022C22) else Color.White
                                    )
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "عدد الصفحات المقروءة:",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                IconButton(
                                    onClick = { if (isTodaySelected) viewModel.setQuranPages(activeRecord.quranPages - 1) },
                                    enabled = isTodaySelected,
                                    modifier = Modifier
                                        .testTag("quran_decrement_btn")
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (darkTheme) Color(0xFF1E293B) else Color(0xFFF4F6FA))
                                ) {
                                    Text(
                                        text = "-",
                                        fontWeight = FontWeight.Bold,
                                        color = (if (darkTheme) Color(0xFF34D399) else Color(0xFF065F46)).copy(alpha = if (isTodaySelected) 1f else 0.35f),
                                        fontSize = 18.sp
                                    )
                                }

                                Text(
                                    text = "${activeRecord.quranPages}",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        color = if (darkTheme) Color(0xFF34D399) else Color(0xFF065F46)
                                    ),
                                    modifier = Modifier
                                        .testTag("quran_page_count_text")
                                        .widthIn(min = 28.dp),
                                    textAlign = TextAlign.Center
                                )

                                IconButton(
                                    onClick = { if (isTodaySelected) viewModel.setQuranPages(activeRecord.quranPages + 1) },
                                    enabled = isTodaySelected,
                                    modifier = Modifier
                                        .testTag("quran_increment_btn")
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (darkTheme) Color(0xFF10B981) else Color(0xFF059669))
                                ) {
                                    Text(
                                        text = "+",
                                        fontWeight = FontWeight.Bold,
                                        color = (if (darkTheme) Color(0xFF022C22) else Color.White).copy(alpha = if (isTodaySelected) 1f else 0.35f),
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }

                        val quranPointsEarned = activeRecord.quranPages.coerceAtMost(10)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (activeRecord.quranPages >= 10) "تم تحصيل الحد الأقصى (10 نقاط)" else "النقاط المكتسبة: $quranPointsEarned من 10",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (activeRecord.quranPages >= 10) SuccessGreen else (if (darkTheme) Color(0xFF34D399) else Color(0xFF065F46)),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            if (activeRecord.quranPages > 0) {
                                Text(
                                    text = "جزاك الله خيراً",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // أذكار الصباح والمساء
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (darkTheme) Color(0xFF1E293B) else Color(0xFFD4DCE5),
                            RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (darkTheme) Color(0xFF111827) else Color(0xFFFFFFFF)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (darkTheme) Color(0xFF065F46).copy(alpha = 0.4f) else Color(0xFFD1FAE5)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "🤲", fontSize = 18.sp)
                                }
                                Column {
                                    Text(
                                        text = "الأذكار اليومية",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (darkTheme) Color.White else Color(0xFF111318)
                                        )
                                    )
                                    Text(
                                        text = "حصن المسلم اليومي",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (darkTheme) Color(0xFF10B981) else Color(0xFF065F46))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "+5 نقاط لكل ذكر",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (darkTheme) Color(0xFF022C22) else Color.White
                                    )
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (activeRecord.morningDhikrDone) {
                                        if (darkTheme) Color(0xFF064E3B) else Color(0xFFD1FAE5)
                                    } else {
                                        if (darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                                    }
                                )
                                .clickable { activeDhikrTypeForReading = "morning" }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "🌅", fontSize = 16.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "أذكار الصباح (انقر للقراءة)",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (darkTheme) Color.White else Color(0xFF1F2937)
                                        )
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "قراءة",
                                        tint = (if (darkTheme) Color(0xFF34D399) else Color(0xFF059669)).copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "+5 نقاط",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (darkTheme) Color(0xFF34D399) else Color(0xFF059669),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Checkbox(
                                    checked = activeRecord.morningDhikrDone,
                                    enabled = isTodaySelected,
                                    onCheckedChange = { if (isTodaySelected) viewModel.toggleMorningDhikr() },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = SuccessGreen,
                                        uncheckedColor = if (darkTheme) Color(0xFF6B7280) else Color(0xFF9CA3AF)
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (activeRecord.eveningDhikrDone) {
                                        if (darkTheme) Color(0xFF064E3B) else Color(0xFFD1FAE5)
                                    } else {
                                        if (darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                                    }
                                )
                                .clickable { activeDhikrTypeForReading = "evening" }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "🌇", fontSize = 16.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "أذكار المساء (انقر للقراءة)",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (darkTheme) Color.White else Color(0xFF1F2937)
                                        )
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "قراءة",
                                        tint = (if (darkTheme) Color(0xFF34D399) else Color(0xFF059669)).copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "+5 نقاط",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (darkTheme) Color(0xFF34D399) else Color(0xFF059669),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Checkbox(
                                    checked = activeRecord.eveningDhikrDone,
                                    enabled = isTodaySelected,
                                    onCheckedChange = { if (isTodaySelected) viewModel.toggleEveningDhikr() },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = SuccessGreen,
                                        uncheckedColor = if (darkTheme) Color(0xFF6B7280) else Color(0xFF9CA3AF)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // المسبحة الإلكترونية التفاعلية
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (darkTheme) Color(0xFF1E293B) else Color(0xFFD4DCE5),
                            RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (darkTheme) Color(0xFF111827) else Color(0xFFFFFFFF)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (darkTheme) Color(0xFF065F46).copy(alpha = 0.4f) else Color(0xFFD1FAE5)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "📿", fontSize = 18.sp)
                                }
                                Column {
                                    Text(
                                        text = "المسبحة الإلكترونية",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (darkTheme) Color.White else Color(0xFF111318)
                                        )
                                    )
                                    Text(
                                        text = "سَبِّحْ بِحَمْدِ رَبِّكَ",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (darkTheme) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF065F46).copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "تسبيح حر",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (darkTheme) Color(0xFF34D399) else Color(0xFF065F46)
                                    )
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        Box(
                            modifier = Modifier
                                .testTag("dhikr_click_button")
                                .size(130.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = if (darkTheme) {
                                            listOf(Color(0xFF065F46), Color(0xFF022C22))
                                        } else {
                                            listOf(Color(0xFFD1FAE5), Color(0xFFFFFFFF))
                                        }
                                    )
                                )
                                .border(
                                    3.dp,
                                    if (darkTheme) Color(0xFF10B981) else Color(0xFF059669),
                                    CircleShape
                                )
                                .clickable { 
                                    if (isTodaySelected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.incrementDhikr() 
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${activeRecord.dhikrCount}",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        color = if (darkTheme) Color.White else Color(0xFF065F46),
                                        fontSize = 34.sp
                                    )
                                )
                                Text(
                                    text = if (isTodaySelected) "اضغط للتسبيح" else "للعرض فقط",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = (if (darkTheme) Color.White else Color(0xFF065F46)).copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isTodaySelected) {
                                Text(
                                    text = "إعادة ضبط العداد ↺",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (darkTheme) Color(0xFF34D399) else Color(0xFF059669),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    modifier = Modifier
                                        .testTag("dhikr_reset_label")
                                        .clickable { 
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.resetDhikr() 
                                        }
                                        .padding(vertical = 4.dp, horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // النوافذ المنبثقة
    if (activeDhikrTypeForReading != null) {
        DhikrReadingFlow(
            type = activeDhikrTypeForReading!!,
            darkTheme = darkTheme,
            onDismiss = { activeDhikrTypeForReading = null },
            onComplete = {
                if (isTodaySelected) {
                    val isDoneCurrently = if (activeDhikrTypeForReading == "morning") activeRecord.morningDhikrDone else activeRecord.eveningDhikrDone
                    if (!isDoneCurrently) {
                        if (activeDhikrTypeForReading == "morning") {
                            viewModel.toggleMorningDhikr()
                        } else if (activeDhikrTypeForReading == "evening") {
                            viewModel.toggleEveningDhikr()
                        }
                    }
                }
                activeDhikrTypeForReading = null
            }
        )
    }

    if (showDaily100Celebration) {
        WorshipCelebrationDialog(
            title = "مبارك! حققت العلامة الكاملة",
            description = "ما شاء الله! أتممت جميع عبادات اليوم وحققت 100 نقطة كاملة. تقبل الله طاعاتك وثبتك عليها.",
            darkTheme = darkTheme,
            onDismiss = {
                celebrationPrefs.edit().putString("daily_100_last_date", record?.date ?: "").apply()
                showDaily100Celebration = false
                hasDismissedDailyCelebrationToday = true
            }
        )
    }

    if (showTotal100Celebration) {
        WorshipCelebrationDialog(
            title = "إنجاز مبارك!",
            description = "تجاوزت حاجز 100 نقطة في مجموع طاعاتك الإجمالية بتطبيق إِبْكَـار. استمر في مسيرتك الإيمانية!",
            darkTheme = darkTheme,
            onDismiss = {
                celebrationPrefs.edit().putBoolean("total_100_celebrated", true).apply()
                showTotal100Celebration = false
                hasDismissedTotalCelebration = true
            }
        )
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = {
                Text(
                    text = "تعديل اسم المستخدم",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "اكتب الاسم الذي تود ظهوره في بطاقة إنجازك الإيماني:",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { if (it.length <= 18) inputName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_name_input_field"),
                        singleLine = true,
                        placeholder = { Text("مثال: عبد الرحمن") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                    Text(
                        text = "الحد الأقصى 18 حرفاً",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        ),
                        textAlign = TextAlign.Left,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("dialog_save_name_btn"),
                    onClick = {
                        if (inputName.isNotBlank()) {
                            viewModel.updateProfileName(inputName)
                        }
                        showEditNameDialog = false
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("dialog_cancel_name_btn"),
                    onClick = { showEditNameDialog = false }
                ) {
                    Text("إلغاء")
                }
            }
        )
    }

    // نافذة الإعدادات
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "إعدادات تطبيق إِبْكَـار",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "خصص إعدادات التنبيه والموقع والمظهر بما يناسبك:",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (darkTheme) Color(0xFF1E293B) else Color(0xFFF7F9FC)
                        ),
                        border = BorderStroke(1.dp, if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleTheme() }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Switch(
                                checked = !darkTheme,
                                onCheckedChange = { onToggleTheme() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.scale(0.85f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "الوضع الفاتح (Light Mode)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        textAlign = TextAlign.Right
                                    )
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (darkTheme) Color(0xFF1E293B) else Color(0xFFF7F9FC)
                        ),
                        border = BorderStroke(1.dp, if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Switch(
                                    checked = hasNotifyPermission && notifyAll,
                                    onCheckedChange = { checked ->
                                        if (!hasNotifyPermission) {
                                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            notifyAll = checked
                                            notificationSettingsPrefs.edit().putBoolean("notify_all", checked).apply()
                                            if (checked) {
                                                com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
                                            }
                                        }
                                    },
                                    modifier = Modifier.scale(0.85f)
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "تفعيل التنبيهات والإشعارات",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        textAlign = TextAlign.Right
                                    )
                                }
                            }
                            if (hasNotifyPermission && notifyAll) {
                                HorizontalDivider(color = if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                                Button(
                                    onClick = { showNotificationDetailsDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "تفاصيل التنبيهات",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "تخصيص تنبيهات كل صلاة وذكر",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (darkTheme) Color(0xFF1E293B) else Color(0xFFF7F9FC)
                        ),
                        border = BorderStroke(1.dp, if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(
                                    onClick = {
                                        locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = "تحديث الموقع",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Text(
                                    text = "موقع حساب المواقيت",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Right
                                )
                            }
                            HorizontalDivider(color = if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$offsetMinutesVal دقيقة",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                    Text(
                                        text = "إزاحة المواقيت (تقديم أو تأخير):",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        textAlign = TextAlign.Right
                                    )
                                }
                                Slider(
                                    value = offsetMinutesVal.toFloat(),
                                    onValueChange = { newValue ->
                                        offsetMinutesVal = newValue.toInt()
                                        notificationSettingsPrefs.edit().putInt("prayer_offset_minutes", newValue.toInt()).apply()
                                    },
                                    onValueChangeFinished = {
                                        com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
                                        try {
                                            val intent1 = Intent(context, Class.forName("com.example.widget.CountdownWidgetProvider")).apply {
                                                action = "com.example.widget.REFRESH_COUNTDOWN"
                                            }
                                            context.sendBroadcast(intent1)
                                            val intent2 = Intent(context, Class.forName("com.example.widget.PrayerTimesWidgetProvider")).apply {
                                                action = "com.example.widget.REFRESH_TIMES"
                                            }
                                            context.sendBroadcast(intent2)
                                        } catch (e: Exception) {}
                                        Toast.makeText(context, "تم ضبط الإزاحة بمقدار $offsetMinutesVal دقائق", Toast.LENGTH_SHORT).show()
                                    },
                                    valueRange = 0f..30f,
                                    steps = 6,
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (darkTheme) Color(0xFF1E293B) else Color(0xFFF7F9FC)
                        ),
                        border = BorderStroke(1.dp, if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "طريقة حساب مواقيت الصلاة",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                            val methods = listOf(
                                "الهيئة العامة المصرية للمساحة",
                                "جامعة أم القرى - مكة المكرمة",
                                "رابطة العالم الإسلامي",
                                "الجمعية الإسلامية لأمريكا الشمالية (ISNA)",
                                "جامعة العلوم الإسلامية بكراتشي",
                                "منطقة الخليج ودبي"
                            )
                            var expanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (darkTheme) Color(0xFF334155) else Color(0xFFE2E8F0),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { expanded = true }
                                        .padding(vertical = 10.dp, horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = methods.getOrElse(prayerCalcMethod) { methods[0] },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    methods.forEachIndexed { index, name ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    textAlign = TextAlign.Right,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            },
                                            onClick = {
                                                prayerCalcMethod = index
                                                notificationSettingsPrefs.edit().putInt("prayer_calc_method", index).apply()
                                                expanded = false
                                                com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
                                                try {
                                                    val intent1 = Intent(context, Class.forName("com.example.widget.CountdownWidgetProvider")).apply {
                                                        action = "com.example.widget.REFRESH_COUNTDOWN"
                                                    }
                                                    context.sendBroadcast(intent1)
                                                    val intent2 = Intent(context, Class.forName("com.example.widget.PrayerTimesWidgetProvider")).apply {
                                                        action = "com.example.widget.REFRESH_TIMES"
                                                    }
                                                    context.sendBroadcast(intent2)
                                                } catch (e: Exception) {}
                                                Toast.makeText(context, "تم حفظ طريقة الحساب بنجاح!", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "تطبيق إِبْكَـار - رفيقك الإيماني",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "صُنع بكل حب مصطفى الماظ",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ibkar.vercel.app"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = if (darkTheme) MaterialTheme.colorScheme.onPrimary else Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "الموقع الرسمي للتطبيق",
                                        color = if (darkTheme) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1877F2))
                                        .clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/ibkar.application"))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "تعذر فتح فيسبوك", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "f",
                                            color = Color(0xFF1877F2),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                                            ),
                                            modifier = Modifier.offset(y = (-1).dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettingsDialog = false }
                ) {
                    Text("تم")
                }
            }
        )
    }

    if (showNotificationDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationDetailsDialog = false },
            title = {
                Text(
                    text = "تخصيص إشعارات العبادات",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "اختر التنبيهات التي ترغب في استقبالها يومياً:",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Switch(
                            checked = notifyPrayers,
                            onCheckedChange = { checked ->
                                notifyPrayers = checked
                                notificationSettingsPrefs.edit().putBoolean("notify_prayers", checked).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SuccessGreen)
                        )
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text(
                                text = "تنبيهات مواقيت الصلاة",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Right
                            )
                            Text(
                                text = "إشعار عند دخول وقت كل صلاة من الصلوات الخمس",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)),
                                textAlign = TextAlign.Right
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Switch(
                            checked = notifyMorningDhikr,
                            onCheckedChange = { checked ->
                                notifyMorningDhikr = checked
                                notificationSettingsPrefs.edit().putBoolean("notify_morning_dhikr", checked).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SuccessGreen)
                        )
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text(
                                text = "تنبيه أذكار الصباح",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Right
                            )
                            Text(
                                text = "تذكير يومي بقراءة أذكار الصباح بعد الشروق",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)),
                                textAlign = TextAlign.Right
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Switch(
                            checked = notifyEveningDhikr,
                            onCheckedChange = { checked ->
                                notifyEveningDhikr = checked
                                notificationSettingsPrefs.edit().putBoolean("notify_evening_dhikr", checked).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SuccessGreen)
                        )
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text(
                                text = "تنبيه أذكار المساء",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Right
                            )
                            Text(
                                text = "تذكير يومي بقراءة أذكار المساء قبل الغروب",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)),
                                textAlign = TextAlign.Right
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showNotificationDetailsDialog = false }
                ) {
                    Text(
                        text = "إغلاق",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        )
    }
}

@Composable
fun CrescentMoonIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val path = Path().apply {
            moveTo(width * 0.75f, height * 0.15f)
            quadraticTo(
                width * 0.05f, height * 0.5f,
                width * 0.75f, height * 0.85f
            )
            quadraticTo(
                width * 0.35f, height * 0.5f,
                width * 0.75f, height * 0.15f
            )
            close()
        }
        drawPath(path = path, color = color)
    }
}

data class UpcomingPrayerInfo(
    val tag: String,
    val name: String,
    val timeStr: String,
    val diffMinutes: Int,
    val diffSeconds: Int
)

fun getUpcomingPrayer(
    todayTimes: Map<String, Pair<Int, Int>>,
    latitude: Double,
    longitude: Double
): UpcomingPrayerInfo? {
    val now = com.example.notification.PrayerTimeCalculator.getLocalCalendar(latitude, longitude)
    val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
    val currentSeconds = now.get(java.util.Calendar.SECOND)
    val currentSecsFromMidnight = currentMinutes * 60 + currentSeconds

    val prayerList = listOf(
        "fajr" to "الفجر",
        "dhuhr" to "الظهر",
        "asr" to "العصر",
        "maghrib" to "المغرب",
        "isha" to "العشاء"
    )

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
                val amPm = if (t.first >= 12) "م" else "ص"
                val timeStr = "%d:%02d %s".format(h12, t.second, amPm)
                return UpcomingPrayerInfo(p.first, p.second, timeStr, diffMin, diffSec)
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
        val amPm = "ص"
        val timeStr = "%d:%02d %s".format(h12, t.second, amPm)
        return UpcomingPrayerInfo("fajr", "فجر الغد", timeStr, diffMin, diffSec)
    }

    return null
}

@Composable
fun NextPrayerCountdownCard(
    upcoming: UpcomingPrayerInfo,
    darkTheme: Boolean
) {
    val isDark = darkTheme
    val skyGradient = getPrayerSkyGradient(upcoming.tag, isDark)
    
    val h = upcoming.diffMinutes / 60
    val m = upcoming.diffMinutes % 60
    val s = upcoming.diffSeconds
    val countdownFormatted = if (h > 0) {
        "%02d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
    val countdownLabel = if (h > 0) {
        "ساعة ودقيقة وثانية"
    } else {
        "دقيقة وثانية"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(colors = skyGradient)
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = "الوقت المتبقي للأذان:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = countdownFormatted,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 24.sp,
                            color = if (isDark) Color(0xFFFFD54F) else Color(0xFF065F46)
                        )
                    )
                    Text(
                        text = countdownLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.45f),
                            fontSize = 9.sp
                        )
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "الصلاة القادمة",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "صلاة ${upcoming.name}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = if (isDark) Color.White else Color(0xFF111318)
                        )
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background((if (isDark) Color.White else Color.Black).copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "الأذان: ${upcoming.timeStr}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else Color.Black
                            )
                        )
                    }
                }
            }
        }
    }
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
fun PrayerCustomIcon(tag: String, isDone: Boolean, modifier: Modifier = Modifier) {
    val emoji = when (tag) {
        "fajr" -> "🌅"
        "dhuhr" -> "☀️"
        "asr" -> "🌤️"
        "maghrib" -> "🌇"
        "isha" -> "🌙"
        else -> "🕌"
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 20.sp)
    }
}

@Composable
fun PrayerItemRow(
    name: String,
    description: String,
    isDone: Boolean,
    tag: String,
    iconString: String,
    pointLabel: String,
    darkTheme: Boolean,
    timeText: String? = null,
    onToggle: () -> Unit
) {
    val isDark = darkTheme
    val skyGradient = remember(tag, isDark) { getPrayerSkyGradient(tag, isDark) }
    val bgBrush = remember(skyGradient, isDone, isDark) {
        val baseColors = if (isDone) {
            if (isDark) {
                listOf(Color(0xFF064E3B), Color(0xFF022C22))
            } else {
                listOf(Color(0xFFD1FAE5), Color(0xFFA7F3D0))
            }
        } else {
            if (isDark) {
                listOf(
                    skyGradient[0].copy(alpha = 0.18f),
                    skyGradient[1].copy(alpha = 0.08f)
                )
            } else {
                listOf(
                    skyGradient[0].copy(alpha = 0.65f),
                    skyGradient[1].copy(alpha = 0.35f)
                )
            }
        }
        Brush.horizontalGradient(colors = baseColors)
    }

    val borderStrokeColor by animateColorAsState(
        targetValue = if (isDone) {
            Color(0xFF10B981).copy(alpha = 0.6f)
        } else {
            if (isDark) Color(0xFF1E293B) else Color(0xFFECEFF1)
        },
        animationSpec = spring(),
        label = "prayer_card_border"
    )

    val rightAccentBarColor = if (isDone) {
        SuccessGreen
    } else {
        if (isDark) Color(0xFF10B981) else Color(0xFFCFD8DC)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("prayer_card_$tag")
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, borderStrokeColor, RoundedCornerShape(14.dp))
            .clickable { onToggle() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 1.dp else 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDark) Color.Transparent else Color.White)
                .background(brush = bgBrush)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(54.dp)
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 14.dp, bottomEnd = 14.dp))
                    .background(rightAccentBarColor)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .testTag("prayer_check_$tag")
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isDone) SuccessGreen else Color.Transparent)
                        .border(
                            width = 2.dp,
                            color = if (isDone) SuccessGreen else (if (isDark) Color(0xFF5A6270) else Color(0xFFB0BEC5)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "تمت الصلاة",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDone) {
                                        if (isDark) Color(0xFFD1FAE5) else Color(0xFF065F46)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            )
                            if (isDone) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(
                                            if (isDark) Color(0xFF065F46).copy(alpha = 0.3f)
                                            else Color(0xFFD1FAE5)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "مؤداة",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }

                        if (timeText != null) {
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = if (isDone) {
                                        if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                                    } else {
                                        if (isDark) Color(0xFFFFD54F) else Color(0xFF065F46)
                                    }
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        ),
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            width = 1.2.dp,
                            color = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    PrayerCustomIcon(
                        tag = tag,
                        isDone = isDone,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

data class MotivationHeaderData(
    val title: String,
    val text: String,
    val icon: String,
    val badgeColor: Color
)

@Composable
fun MotivationHeaderCard(
    totalDoneItems: Int,
    isQuranDone: Boolean,
    isFajrDone: Boolean,
    isTodaySelected: Boolean,
    darkTheme: Boolean = true
) {
    val motivation = when {
        totalDoneItems == 8 -> MotivationHeaderData(
            "هنيئاً لك التمام والكمال!",
            "أتممت عباداتك اليومية كاملة، جعلك الله من أهل الفردوس الأعلى.",
            "👑",
            Color(0xFFFFD700)
        )
        totalDoneItems >= 5 -> MotivationHeaderData(
            "همة عالية وخطى ثابتة",
            "أنجزت معظم فرائض وسنن اليوم، واصل حتى تختم يومك بتمام الأجر.",
            "🌟",
            Color(0xFF34D399)
        )
        !isFajrDone && isTodaySelected -> MotivationHeaderData(
            "انطلاقة اليوم تبدأ بالفجر",
            "ركعتا الفجر خير من الدنيا وما فيها، ابدأ يومك بنور الصلاة وذكر الله.",
            "🌅",
            Color(0xFFFFB74D)
        )
        else -> MotivationHeaderData(
            "يوم جديد.. وباب أجر مفتوح",
            "استعن بالله وحافظ على صلواتك في وقتها لتنال بركة يومك وحفظه.",
            "🌿",
            Color(0xFF10B981)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (darkTheme) Color(0xFF111827) else Color(0xFFFFFFFF)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(motivation.badgeColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = motivation.icon, fontSize = 24.sp)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = motivation.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (darkTheme) Color.White else Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = motivation.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (darkTheme) Color(0xFF94A3B8) else Color(0xFF475569)
                )
            }
        }
    }
}

@Composable
fun DhikrReadingFlow(
    type: String,
    darkTheme: Boolean = true,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val athkarList = if (type == "morning") {
        listOf(
            StepDhikr(
                text = "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لا إِلَهَ إِلا اللَّهُ وَحْدَهُ لا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.",
                count = 1,
                benefit = "سؤال خير اليوم والتعوذ من شره ومن عذاب القبر"
            ),
            StepDhikr(
                text = "اللّهُـمَّ أَنْتَ رَبِّـي لا إِلهَ إِلاّ أَنْتَ، خَلَقْتَنـي وَأَنا عَبْـدُك، وَأَنا عَلـى عَهْـدِكَ وَوَعْـدِكَ ما اسْتَـطَعْت، أَعـوذُ بِكَ مِنْ شَـرِّ ما صَنَـعْت، أَبـوءُ لَـكَ بِنِعْـمَتِـكَ عَلَـيَّ وَأَبـوءُ بِذَنْـبي فَاغْفِـرْ لي فَإِنَّـهُ لا يَغْفِـرُ الذُّنـوبَ إِلاّ أَنْتَ.",
                count = 1,
                benefit = "سيد الاستغفار - من قالها موقناً بها ومات دخل الجنة"
            ),
            StepDhikr(
                text = "قُلْ هُوَ اللَّهُ أَحَدٌ، اللَّهُ الصَّمَدُ، لَمْ يَلِدْ وَلَمْ يُولَدْ، وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ.",
                count = 3,
                benefit = "تكفيك من كل شيء"
            ),
            StepDhikr(
                text = "قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ، مِن شَرِّ مَا خَلَقَ، وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ، وَمِن شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ، وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ.",
                count = 3,
                benefit = "الحفظ والتحصين من الشرور والحسد"
            ),
            StepDhikr(
                text = "قُلْ أَعُوذُ بِرَبِّ النَّاسِ، مَلِكِ النَّاسِ، إِلَهِ النَّاسِ، مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ، الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ، مِنَ الْجِنَّةِ وَالنَّاسِ.",
                count = 3,
                benefit = "التحصين من وساوس الشياطين"
            ),
            StepDhikr(
                text = "بِسـمِ اللهِ الذي لا يَضُـرُّ مَعَ اسمِـهِ شَيءٌ في الأرْضِ وَلا في السّمـاءِ وَهـوَ السّمـيعُ العَلـيم.",
                count = 3,
                benefit = "لم يضره شيء في ذلك اليوم"
            ),
            StepDhikr(
                text = "رَضيـتُ بِاللهِ رَبَّـاً وَبِالإسْلامِ ديـناً وَبِمُحَـمَّدٍ صلى الله عليه وسلم نَبِيّـاً.",
                count = 3,
                benefit = "كان حقاً على الله أن يرضيه يوم القيامة"
            ),
            StepDhikr(
                text = "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ، أَصْلِحْ لِي شَأْنِي كُلَّهُ، وَلَا تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ.",
                count = 1,
                benefit = "طلب المعونة والتسديد في شؤون الحياة كلها"
            ),
            StepDhikr(
                text = "سُبْحـانَ اللهِ وَبِحَمْـدِهِ عَدَدَ خَلْـقِه، وَرِضـا نَفْسِـه، وَزِنَـةَ عَـرْشِـه، وَمِـدادَ كَلِمـاتِـه.",
                count = 3,
                benefit = "أجر عظيم يزن عبادة ساعات طويلة"
            ),
            StepDhikr(
                text = "سُبْحـانَ اللهِ وَبِحَمْـدِهِ.",
                count = 100,
                benefit = "حُطّت خطاياه وإن كانت مثل زبد البحر"
            )
        )
    } else {
        listOf(
            StepDhikr(
                text = "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لا إِلَهَ إِلا اللَّهُ وَحْدَهُ لا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.",
                count = 1,
                benefit = "سؤال خير الليلة والتعوذ من شرها"
            ),
            StepDhikr(
                text = "اللّهُـمَّ أَنْتَ رَبِّـي لا إِلهَ إِلاّ أَنْتَ، خَلَقْتَنـي وَأَنا عَبْـدُك، وَأَنا عَلـى عَهْـدِكَ وَوَعْـدِكَ ما اسْتَـطَعْت، أَعـوذُ بِكَ مِنْ شَـرِّ ما صَنَـعْت، أَبـوءُ لَـكَ بِنِعْـمَتِـكَ عَلَـيَّ وَأَبـوءُ بِذَنْـبي فَاغْفِـرْ لي فَإِنَّـهُ لا يَغْفِـرُ الذُّنـوبَ إِلاّ أَنْتَ.",
                count = 1,
                benefit = "سيد الاستغفار - من قالها ومات من ليلته دخل الجنة"
            ),
            StepDhikr(
                text = "قُلْ هُوَ اللَّهُ أَحَدٌ، اللَّهُ الصَّمَدُ، لَمْ يَلِدْ وَلَمْ يُولَدْ، وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ.",
                count = 3,
                benefit = "تكفيك من كل شيء"
            ),
            StepDhikr(
                text = "قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ، مِن شَرِّ مَا خَلَقَ، وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ، وَمِن شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ، وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ.",
                count = 3,
                benefit = "الحفظ والتحصين من الشرور والحسد"
            ),
            StepDhikr(
                text = "قُلْ أَعُوذُ بِرَبِّ النَّاسِ، مَلِكِ النَّاسِ، إِلَهِ النَّاسِ، مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ، الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ، مِنَ الْجِنَّةِ وَالنَّاسِ.",
                count = 3,
                benefit = "التحصين من وساوس الشياطين"
            ),
            StepDhikr(
                text = "أَعُوذُ بِكَلِمَاتِ اللهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.",
                count = 3,
                benefit = "لم يضره شيء في تلك الليلة"
            ),
            StepDhikr(
                text = "بِسـمِ اللهِ الذي لا يَضُـرُّ مَعَ اسمِـهِ شَيءٌ في الأرْضِ وَلا في السّمـاءِ وَهـوَ السّمـيعُ العَلـيم.",
                count = 3,
                benefit = "حماية تامة من فواجع الأقدار"
            ),
            StepDhikr(
                text = "رَضيـتُ بِاللهِ رَبَّـاً وَبِالإسْلامِ ديـناً وَبِمُحَـمَّدٍ صلى الله عليه وسلم نَبِيّـاً.",
                count = 3,
                benefit = "كان حقاً على الله أن يرضيه يوم القيامة"
            ),
            StepDhikr(
                text = "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ، أَصْلِحْ لِي شَأْنِي كُلَّهُ، وَلَا تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ.",
                count = 1,
                benefit = "طلب التسديد والتوكل على الله"
            ),
            StepDhikr(
                text = "سُبْحـانَ اللهِ وَبِحَمْـدِهِ.",
                count = 100,
                benefit = "غفران الذنوب ورفعة الدرجات"
            )
        )
    }

    val context = LocalContext.current
    val sharedPrefs = remember(context) {
        context.getSharedPreferences("dhikr_flow_prefs", android.content.Context.MODE_PRIVATE)
    }
    val savedIndexKey = "dhikr_${type}_index"
    val savedCountKey = "dhikr_${type}_count"
    var currentIndex by remember { 
        mutableStateOf(sharedPrefs.getInt(savedIndexKey, 0)) 
    }
    val totalCount = athkarList.size

    val currentCountsLeft = remember(type) {
        mutableStateListOf<Int>().apply {
            val savedIndex = sharedPrefs.getInt(savedIndexKey, 0)
            for (i in 0 until totalCount) {
                if (i < savedIndex) {
                    add(0)
                } else if (i == savedIndex) {
                    val defaultCount = athkarList[i].count
                    val savedLeft = sharedPrefs.getInt(savedCountKey, defaultCount)
                    if (savedLeft in 1..defaultCount) {
                        add(savedLeft)
                    } else {
                        add(defaultCount)
                    }
                } else {
                    add(athkarList[i].count)
                }
            }
        }
    }

    fun saveProgress(index: Int, countLeft: Int) {
        sharedPrefs.edit().apply {
            putInt(savedIndexKey, index)
            putInt(savedCountKey, countLeft)
            apply()
        }
    }

    val currentDhikr = athkarList.getOrNull(currentIndex)
    val curCountLeft = currentCountsLeft.getOrNull(currentIndex) ?: 0
    val maxCount = currentDhikr?.count ?: 1

    var isFinished by remember { mutableStateOf(currentIndex >= totalCount) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val backgroundColor = if (darkTheme) Color(0xFF0B0F19) else Color(0xFFF8FAFC)
        val cardColor = if (darkTheme) Color(0xFF111827) else Color(0xFFFFFFFF)
        val textColor = if (darkTheme) Color.White else Color(0xFF0F172A)
        val brandColor = if (type == "morning") Color(0xFF10B981) else Color(0xFF3B82F6)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 660.dp)
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onDismiss() },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = if (darkTheme) Color.White else Color(0xFF475569)
                        )
                    }

                    Text(
                        text = if (type == "morning") "أذكار الصباح" else "أذكار المساء",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = brandColor
                        ),
                        textAlign = TextAlign.Center
                    )

                    IconButton(
                        onClick = {
                            currentIndex = 0
                            isFinished = false
                            saveProgress(0, athkarList[0].count)
                            currentCountsLeft.clear()
                            currentCountsLeft.addAll(athkarList.map { it.count })
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "إعادة البدء",
                            tint = if (darkTheme) Color.White else Color(0xFF475569)
                        )
                    }
                }

                if (!isFinished && currentDhikr != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الذكر ${currentIndex + 1} من $totalCount",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (darkTheme) Color.LightGray else Color(0xFF64748B)
                                )
                            )
                            val percent = (((currentIndex + 1).toFloat() / totalCount) * 100).toInt()
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = brandColor
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (i in 0 until totalCount) {
                                val segmentColor = when {
                                    i < currentIndex -> SuccessGreen
                                    i == currentIndex -> brandColor
                                    else -> if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(segmentColor)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(cardColor)
                            .border(
                                1.dp,
                                if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                                RoundedCornerShape(24.dp)
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentDhikr.text,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        lineHeight = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp,
                                        color = textColor
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (currentDhikr.benefit.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (darkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "الفضل: ${currentDhikr.benefit}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (darkTheme) Color(0xFF94A3B8) else Color(0xFF475569),
                                            lineHeight = 16.sp
                                        ),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(115.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            brandColor,
                                            brandColor.copy(alpha = 0.7f)
                                        )
                                    )
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (curCountLeft > 1) {
                                        val newCount = curCountLeft - 1
                                        currentCountsLeft[currentIndex] = newCount
                                        saveProgress(currentIndex, newCount)
                                    } else {
                                        currentCountsLeft[currentIndex] = 0
                                        if (currentIndex < totalCount - 1) {
                                            val nextIndex = currentIndex + 1
                                            currentIndex = nextIndex
                                            val nextDefaultCount = athkarList[nextIndex].count
                                            saveProgress(nextIndex, nextDefaultCount)
                                        } else {
                                            isFinished = true
                                            saveProgress(0, athkarList[0].count)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "$curCountLeft",
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "متبقي",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Text(
                            text = "انقر على الدائرة للعد",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (darkTheme) Color.Gray else Color(0xFF64748B)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (currentIndex > 0) {
                                    val prevIndex = currentIndex - 1
                                    currentIndex = prevIndex
                                    val prevDefaultCount = athkarList[prevIndex].count
                                    currentCountsLeft[prevIndex] = prevDefaultCount
                                    saveProgress(prevIndex, prevDefaultCount)
                                }
                            },
                            enabled = currentIndex > 0
                        ) {
                            Text(
                                text = "السابق",
                                fontWeight = FontWeight.Bold,
                                color = if (currentIndex > 0) brandColor else Color.Gray
                            )
                        }

                        TextButton(
                            onClick = {
                                currentCountsLeft[currentIndex] = 0
                                if (currentIndex < totalCount - 1) {
                                    val nextIndex = currentIndex + 1
                                    currentIndex = nextIndex
                                    val nextDefaultCount = athkarList[nextIndex].count
                                    saveProgress(nextIndex, nextDefaultCount)
                                } else {
                                    isFinished = true
                                    saveProgress(0, athkarList[0].count)
                                }
                            }
                        ) {
                            Text(
                                text = "تخطي",
                                fontWeight = FontWeight.Bold,
                                color = brandColor
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(SuccessGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "✨", fontSize = 32.sp)
                            }
                            Text(
                                text = "تقبل الله طاعتك!",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SuccessGreen
                                ),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "أتممت قراءة ${if (type == "morning") "أذكار الصباح" else "أذكار المساء"} بنجاح، حفظك الله ورعاك.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (darkTheme) Color.LightGray else Color(0xFF475569),
                                    lineHeight = 22.sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    saveProgress(0, athkarList[0].count)
                                    onComplete()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Text(
                                    text = "تم وحفظ الإنجاز",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class StepDhikr(
    val text: String,
    val count: Int,
    val benefit: String
)

fun updateLocationAndPrayerTimes(
    context: android.content.Context,
    prefs: android.content.SharedPreferences,
    onComplete: (Boolean, String, Float, Float) -> Unit
) {
    val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
    if (locationManager == null) {
        onComplete(false, "تعذر الوصول لخدمة الموقع.", 30.0444f, 31.2357f)
        return
    }

    val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
    val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

    if (!isGpsEnabled && !isNetworkEnabled) {
        onComplete(false, "يرجى تفعيل خدمة تحديد الموقع (GPS) من إعدادات الهاتف.", 30.0444f, 31.2357f)
        return
    }

    val provider = if (isNetworkEnabled) {
        android.location.LocationManager.NETWORK_PROVIDER
    } else {
        android.location.LocationManager.GPS_PROVIDER
    }

    try {
        val lastKnownLocation = locationManager.getLastKnownLocation(provider)
        if (lastKnownLocation != null) {
            val lat = lastKnownLocation.latitude.toFloat()
            val lng = lastKnownLocation.longitude.toFloat()
            
            prefs.edit().apply {
                putFloat("user_latitude", lat)
                putFloat("user_longitude", lng)
                apply()
            }
            
            try {
                val intent1 = Intent(context, Class.forName("com.example.widget.CountdownWidgetProvider")).apply {
                    action = "com.example.widget.REFRESH_COUNTDOWN"
                }
                context.sendBroadcast(intent1)
                val intent2 = Intent(context, Class.forName("com.example.widget.PrayerTimesWidgetProvider")).apply {
                    action = "com.example.widget.REFRESH_TIMES"
                }
                context.sendBroadcast(intent2)
            } catch (e: Exception) {}

            com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
            onComplete(true, "تم تحديث الموقع ومواقيت الصلاة بنجاح", lat, lng)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
                locationManager.getCurrentLocation(
                    provider,
                    null,
                    executor
                ) { location ->
                    if (location != null) {
                        val lat = location.latitude.toFloat()
                        val lng = location.longitude.toFloat()
                        prefs.edit().apply {
                            putFloat("user_latitude", lat)
                            putFloat("user_longitude", lng)
                            apply()
                        }
                        try {
                            val intent1 = Intent(context, Class.forName("com.example.widget.CountdownWidgetProvider")).apply {
                                action = "com.example.widget.REFRESH_COUNTDOWN"
                            }
                            context.sendBroadcast(intent1)
                            val intent2 = Intent(context, Class.forName("com.example.widget.PrayerTimesWidgetProvider")).apply {
                                action = "com.example.widget.REFRESH_TIMES"
                            }
                            context.sendBroadcast(intent2)
                        } catch (e: Exception) {}
                        com.example.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context)
                        onComplete(true, "تم تحديث الموقع ومواقيت الصلاة بنجاح", lat, lng)
                    } else {
                        onComplete(false, "تعذر تحديد الإحداثيات الحالية بدقة.", 30.0444f, 31.2357f)
                    }
                }
            } else {
                onComplete(false, "تعذر تحديد الإحداثيات الحالية بدقة.", 30.0444f, 31.2357f)
            }
        }
    } catch (e: SecurityException) {
        onComplete(false, "لم يتم منح إذن الوصول إلى الموقع.", 30.0444f, 31.2357f)
    } catch (e: Exception) {
        onComplete(false, "حدث خطأ: ${e.localizedMessage}", 30.0444f, 31.2357f)
    }
}

@Composable
fun CelebrationEffects(modifier: Modifier = Modifier) {
    val confettiList = remember {
        List(40) {
            val randomX = (1..1000).random().toFloat() / 1000f
            val randomY = - (1..1500).random().toFloat() / 1000f
            val speed = 0.0035f + (1..60).random().toFloat() / 10000f
            val size = 8f + (1..14).random().toFloat()
            val colors = listOf(
                Color(0xFFFFD700),
                Color(0xFFFF4500),
                Color(0xFF10B981),
                Color(0xFF3B82F6),
                Color(0xFFFF69B4),
                Color(0xFF9370DB),
                Color(0xFF34D399)
            )
            ConfettiState(
                x = randomX,
                y = randomY,
                speed = speed,
                size = size,
                color = colors.random(),
                angle = (1..360).random().toFloat(),
                spin = -4f + (1..8).random().toFloat(),
                type = (0..3).random(),
                drift = -0.001f + (1..20).random().toFloat() / 10000f
            )
        }
    }

    val balloonList = remember {
        List(12) {
            val randomX = 0.05f + (1..900).random().toFloat() / 1000f
            val randomY = 1.05f + (1..1000).random().toFloat() / 1000f
            val speed = 0.0025f + (1..40).random().toFloat() / 10000f
            val size = 40f + (1..20).random().toFloat()
            val colors = listOf(
                Color(0xFFFF5C5C),
                Color(0xFF3CA9FF),
                Color(0xFFFFCA28),
                Color(0xFF10B981),
                Color(0xFFEC407A)
            )
            BalloonState(
                x = randomX,
                y = randomY,
                speed = speed,
                size = size,
                color = colors.random(),
                waveOffset = (1..100).random().toFloat(),
                waveAmplitude = 0.012f + (1..10).random().toFloat() / 1000f
            )
        }
    }

    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            confettiList.forEach { p ->
                p.y += p.speed
                p.x = (p.x + p.drift).coerceIn(0f, 1f)
                p.angle += p.spin
                if (p.y > 1f) {
                    p.y = -0.05f
                }
            }
            balloonList.forEach { b ->
                b.y -= b.speed
                if (b.y < -0.2f) {
                    b.y = 1.1f
                    b.x = 0.05f + (1..900).random().toFloat() / 1000f
                }
            }
            tick++
        }
    }

    Canvas(modifier = modifier) {
        val _tick = tick
        val width = size.width
        val height = size.height

        confettiList.forEach { p ->
            val px = p.x * width
            val py = p.y * height
            val pSize = p.size
            if (p.y in 0f..1f) {
                drawContext.canvas.save()
                drawContext.canvas.translate(px, py)
                drawContext.canvas.rotate(p.angle)
                when (p.type) {
                    0 -> drawCircle(color = p.color, radius = pSize / 2)
                    1 -> drawRect(color = p.color, size = androidx.compose.ui.geometry.Size(pSize, pSize / 2))
                    2 -> {
                        val triPath = Path().apply {
                            moveTo(0f, -pSize / 2)
                            lineTo(pSize / 2, pSize / 2)
                            lineTo(-pSize / 2, pSize / 2)
                            close()
                        }
                        drawPath(path = triPath, color = p.color)
                    }
                    else -> drawRect(color = p.color, size = androidx.compose.ui.geometry.Size(pSize * 1.4f, 3f))
                }
                drawContext.canvas.restore()
            }
        }

        balloonList.forEach { b ->
            val bx = (b.x + kotlin.math.sin(b.y * 7f + b.waveOffset) * b.waveAmplitude) * width
            val by = b.y * height
            val bw = b.size
            val bh = b.size * 1.25f
            if (b.y in -0.15f..1.1f) {
                val stringPath = Path().apply {
                    moveTo(bx, by + bh)
                    cubicTo(
                        bx - 10f, by + bh + bh * 0.4f,
                        bx + 10f, by + bh + bh * 0.8f,
                        bx, by + bh + bh * 1.2f
                    )
                }
                drawPath(
                    path = stringPath,
                    color = Color.LightGray.copy(alpha = 0.35f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )

                val knotPath = Path().apply {
                    moveTo(bx, by + bh)
                    lineTo(bx - 4.dp.toPx(), by + bh + 6.dp.toPx())
                    lineTo(bx + 4.dp.toPx(), by + bh + 6.dp.toPx())
                    close()
                }
                drawPath(path = knotPath, color = b.color)

                drawOval(
                    color = b.color,
                    topLeft = androidx.compose.ui.geometry.Offset(bx - bw / 2f, by),
                    size = androidx.compose.ui.geometry.Size(bw, bh)
                )

                drawOval(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = androidx.compose.ui.geometry.Offset(bx - bw * 0.28f, by + bh * 0.12f),
                    size = androidx.compose.ui.geometry.Size(bw * 0.22f, bh * 0.22f)
                )
            }
        }
    }
}

class ConfettiState(
    var x: Float,
    var y: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    var angle: Float,
    val spin: Float,
    val type: Int,
    val drift: Float
)

class BalloonState(
    var x: Float,
    var y: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val waveOffset: Float,
    val waveAmplitude: Float
)

@Composable
fun WorshipCelebrationDialog(
    title: String,
    description: String,
    darkTheme: Boolean = true,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        var isAnimateOpen by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            isAnimateOpen = true
        }
        val scale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isAnimateOpen) 1f else 0.82f,
            animationSpec = spring(
                dampingRatio = 0.72f,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            ),
            label = "scale_anim"
        )
        val alpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isAnimateOpen) 1f else 0f,
            animationSpec = tween(durationMillis = 350),
            label = "alpha_anim"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            CelebrationEffects(modifier = Modifier.fillMaxSize())

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .widthIn(max = 400.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        alpha = alpha
                    )
                    .shadow(
                        12.dp,
                        RoundedCornerShape(24.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.01f)
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1.0f,
                            targetValue = 1.12f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse_scale"
                        )
                        
                        Box(
                            modifier = Modifier.scale(pulseScale),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🏆",
                                style = androidx.compose.ui.text.TextStyle(fontSize = 32.sp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                            lineHeight = 22.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "الحمد لله",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}
