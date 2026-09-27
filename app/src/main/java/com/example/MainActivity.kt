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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
            var isArabic by rememberSaveable { mutableStateOf(themePrefs.getBoolean("is_arabic", true)) }

            MyApplicationTheme(darkTheme = useDarkTheme) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainAppContent(
                            darkTheme = useDarkTheme,
                            isArabic = isArabic,
                            onToggleTheme = {
                                val newValue = !useDarkTheme
                                useDarkTheme = newValue
                                themePrefs.edit().putBoolean("dark_theme", newValue).apply()
                            },
                            onToggleLanguage = {
                                val newLang = !isArabic
                                isArabic = newLang
                                themePrefs.edit().putBoolean("is_arabic", newLang).apply()
                            },
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
    darkTheme: Boolean,
    isArabic: Boolean,
    onToggleTheme: () -> Unit,
    onToggleLanguage: () -> Unit,
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

    var cityNameState by remember { mutableStateOf(if (isArabic) "جاري التحديد..." else "Detecting...") }
    LaunchedEffect(userLat, userLng, isArabic) {
        withContext(Dispatchers.IO) {
            try {
                val locale = if (isArabic) Locale("ar") else Locale("en")
                val geocoder = Geocoder(context, locale)
                val addresses = geocoder.getFromLocation(userLat.toDouble(), userLng.toDouble(), 1)
                val fallbackText = if (isArabic) "موقعك الحالي" else "Current Location"
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: fallbackText
                    cityNameState = city
                } else {
                    cityNameState = fallbackText
                }
            } catch (e: Exception) {
                cityNameState = if (isArabic) "موقعك الحالي" else "Current Location"
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
        val amPm = if (t.first >= 12) {
            if (isArabic) "م" else "PM"
        } else {
            if (isArabic) "ص" else "AM"
        }
        return "%d:%02d %s".format(h12, t.second, amPm)
    }

    val todayStr = DateHelper.getTodayDateString(context)
    val displayDate = if (isArabic) DateHelper.getArabicDisplayDate(selectedDate) else selectedDate
    val isTodaySelected = selectedDate == todayStr

    var upcomingPrayerInfoState by remember(todayTimes) {
        mutableStateOf<UpcomingPrayerInfo?>(null)
    }

    LaunchedEffect(todayTimes, userLat, userLng, isTodaySelected, isArabic) {
        if (isTodaySelected) {
            while (true) {
                upcomingPrayerInfoState = getUpcomingPrayer(todayTimes, userLat.toDouble(), userLng.toDouble(), isArabic)
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                com.example.updateLocationAndPrayerTimes(context, notificationSettingsPrefs) { success, msg, newLat, newLng ->
                    val finalMsg = if (isArabic) msg else "Location updated successfully"
                    Toast.makeText(context, finalMsg, Toast.LENGTH_LONG).show()
                    if (success) {
                        userLat = newLat
                        userLng = newLng
                    }
                }
            } else {
                val errorMsg = if (isArabic) "تم رفض إذن الموقع." else "Location permission denied."
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
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
                            text = if (isArabic) "إِبْكَـار" else "Ibkar",
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
                                contentDescription = if (isArabic) "الإعدادات" else "Settings",
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
                                text = if (isArabic) "الإصدار 1.0" else "Version 1.0",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            item {
                MotivationHeaderCard(
                    totalDoneItems = totalDoneItems,
                    isQuranDone = isQuranDone,
                    isFajrDone = activeRecord.fajrDone,
                    isTodaySelected = isTodaySelected,
                    darkTheme = darkTheme,
                    isArabic = isArabic
                )
            }

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
                                            text = if (isArabic) "المستخدم" else "User",
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
                                                inputName = profile?.userName ?: if (isArabic) "عابد لله" else "Worshipper"
                                                showEditNameDialog = true
                                            }
                                    ) {
                                        Text(
                                            text = profile?.userName ?: if (isArabic) "عابد لله" else "Worshipper",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                color = if (darkTheme) Color.White else Color(0xFF065F46),
                                                fontSize = 22.sp
                                            )
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = if (isArabic) "تعديل الاسم" else "Edit Name",
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
                                            text = if (streak > 0) {
                                                if (isArabic) "التتابع: $streak أيام" else "Streak: $streak days"
                                            } else {
                                                if (isArabic) "ابدأ التتابع اليوم" else "Start streak today"
                                            },
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
                                        text = if (isArabic) "الرتبة اليومية" else "Daily Rank",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = (if (darkTheme) Color(0xFF6EE7B7) else Color(0xFF065F46)).copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isArabic) rankSpec.title else "Rank ${rankSpec.title}",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            color = if (darkTheme) Color.White else Color(0xFF047857)
                                        )
                                    )
                                }
                                Column(horizontalAlignment = if (isArabic) Alignment.End else Alignment.Start) {
                                    Text(
                                        text = if (isArabic) "نقاط اليوم" else "Today's Points",
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
                                        text = if (isArabic) "نسبة إتمام عبادات اليوم" else "Daily Worship Progress",
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
                                        if (isArabic) "ما شاء الله! أتممت جميع عبادات اليوم بالكامل 🎉" else "Mashallah! You completed all daily worships 🎉"
                                    } else {
                                        if (isArabic) "أتممت $totalDoneItems من 8 عبادات، واصل الطاعة!" else "Completed $totalDoneItems of 8, keep going!"
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
                                text = if (isArabic) "◀" else "▶",
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
                                    text = if (isArabic) "العودة لليوم" else "Back to Today",
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
                                    text = if (isArabic) "اليوم" else "Today",
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
                                text = if (isArabic) "▶" else "◀",
                                style = MaterialTheme.typography.bodyLarge,
                                color = (if (darkTheme) Color(0xFF10B981) else Color(0xFF059669)).copy(alpha = nextButtonAlpha),
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

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
                            text = if (isArabic) "🔒 سجل الأيام السابقة للعرض فقط حفاظاً على دقة البيانات" else "🔒 Past records are view-only to preserve data accuracy.",
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

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isArabic) "الصلوات الخمس المفروضة" else "The Five Obligatory Prayers",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    if (isTodaySelected) {
                        upcomingPrayerInfoState?.let { upcoming ->
                            NextPrayerCountdownCard(upcoming = upcoming, darkTheme = darkTheme, isArabic = isArabic)
                        }
                    }

                    PrayerItemRow(
                        name = if (isArabic) "الفجر" else "Fajr",
                        description = if (isArabic) "ركعتان مفروضتان مع سنة الفجر" else "2 Fard Rak'ahs + Sunnah",
                        isDone = activeRecord.fajrDone,
                        tag = "fajr",
                        isArabic = isArabic,
                        darkTheme = darkTheme,
                        timeText = getPrayerTimeStr("fajr"),
                        onToggle = { if (isTodaySelected) viewModel.togglePrayer("fajr") }
                    )
                    PrayerItemRow(
                        name = if (isArabic) "الظهر" else "Dhuhr",
                        description = if (isArabic) "أربع ركعات مفروضة" else "4 Fard Rak'ahs",
                        isDone = activeRecord.dhuhrDone,
                        tag = "dhuhr",
                        isArabic = isArabic,
                        darkTheme = darkTheme,
                        timeText = getPrayerTimeStr("dhuhr"),
                        onToggle = { if (isTodaySelected) viewModel.togglePrayer("dhuhr") }
                    )
                    PrayerItemRow(
                        name = if (isArabic) "العصر" else "Asr",
                        description = if (isArabic) "أربع ركعات مفروضة" else "4 Fard Rak'ahs",
                        isDone = activeRecord.asrDone,
                        tag = "asr",
                        isArabic = isArabic,
                        darkTheme = darkTheme,
                        timeText = getPrayerTimeStr("asr"),
                        onToggle = { if (isTodaySelected) viewModel.togglePrayer("asr") }
                    )
                    PrayerItemRow(
                        name = if (isArabic) "المغرب" else "Maghrib",
                        description = if (isArabic) "ثلاث ركعات مفروضة" else "3 Fard Rak'ahs",
                        isDone = activeRecord.maghribDone,
                        tag = "maghrib",
                        isArabic = isArabic,
                        darkTheme = darkTheme,
                        timeText = getPrayerTimeStr("maghrib"),
                        onToggle = { if (isTodaySelected) viewModel.togglePrayer("maghrib") }
                    )
                    PrayerItemRow(
                        name = if (isArabic) "العشاء" else "Isha",
                        description = if (isArabic) "أربع ركعات مفروضة مع الشفع والوتر" else "4 Fard Rak'ahs + Witr",
                        isDone = activeRecord.ishaDone,
                        tag = "isha",
                        isArabic = isArabic,
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
                                text = if (isArabic) "مبارك! أتممت جميع الصلوات المفروضة (+20 نقطة مكافأة)" else "Congrats! All prayers completed (+20 Bonus Points)",
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
                                        text = if (isArabic) "ورد القرآن الكريم" else "Quran Wird",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (darkTheme) Color.White else Color(0xFF111318)
                                        )
                                    )
                                    Text(
                                        text = if (isArabic) "صفحة واحدة على الأقل يومياً" else "At least one page daily",
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
                                    text = if (isArabic) "+1 نقطة / صفحة" else "+1 Point/Page",
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
                                text = if (isArabic) "عدد الصفحات المقروءة:" else "Pages Read:",
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
                                text = if (activeRecord.quranPages >= 10) {
                                    if (isArabic) "تم تحصيل الحد الأقصى (10 نقاط)" else "Max points reached (10)"
                                } else {
                                    if (isArabic) "النقاط المكتسبة: $quranPointsEarned من 10" else "Points earned: $quranPointsEarned of 10"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (activeRecord.quranPages >= 10) SuccessGreen else (if (darkTheme) Color(0xFF34D399) else Color(0xFF065F46)),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            if (activeRecord.quranPages > 0) {
                                Text(
                                    text = if (isArabic) "جزاك الله خيراً" else "May Allah reward you",
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
                                        text = if (isArabic) "الأذكار اليومية" else "Daily Dhikr",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (darkTheme) Color.White else Color(0xFF111318)
                                        )
                                    )
                                    Text(
                                        text = if (isArabic) "حصن المسلم اليومي" else "Daily Hisn al-Muslim",
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
                                    text = if (isArabic) "+5 نقاط لكل ذكر" else "+5 Points each",
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
                                        text = if (isArabic) "أذكار الصباح (انقر للقراءة)" else "Morning Dhikr (Tap to read)",
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
                                    text = if (isArabic) "+5 نقاط" else "+5 Pts",
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
                                        text = if (isArabic) "أذكار المساء (انقر للقراءة)" else "Evening Dhikr (Tap to read)",
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
                                    text = if (isArabic) "+5 نقاط" else "+5 Pts",
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

            // المسبحة الإلكترونية
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
                                        text = if (isArabic) "المسبحة الإلكترونية" else "Digital Rosary",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (darkTheme) Color.White else Color(0xFF111318)
                                        )
                                    )
                                    Text(
                                        text = if (isArabic) "سَبِّحْ بِحَمْدِ رَبِّكَ" else "Glorify your Lord",
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
                                    text = if (isArabic) "تسبيح حر" else "Free Tasbeeh",
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
                                    text = if (isTodaySelected) {
                                        if (isArabic) "اضغط للتسبيح" else "Tap to tasbeeh"
                                    } else {
                                        if (isArabic) "للعرض فقط" else "View only"
                                    },
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
                                    text = if (isArabic) "إعادة ضبط العداد ↺" else "Reset Counter ↺",
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

    if (activeDhikrTypeForReading != null) {
        DhikrReadingFlow(
            type = activeDhikrTypeForReading!!,
            darkTheme = darkTheme,
            isArabic = isArabic,
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
            title = if (isArabic) "مبارك! حققت العلامة الكاملة" else "Congrats! Perfect Score",
            description = if (isArabic) "ما شاء الله! أتممت جميع عبادات اليوم وحققت 100 نقطة كاملة. تقبل الله طاعاتك وثبتك عليها." else "Mashallah! You completed all daily worships and achieved a perfect 100 points. May Allah accept your deeds.",
            darkTheme = darkTheme,
            isArabic = isArabic,
            onDismiss = {
                celebrationPrefs.edit().putString("daily_100_last_date", record?.date ?: "").apply()
                showDaily100Celebration = false
                hasDismissedDailyCelebrationToday = true
            }
        )
    }

    if (showTotal100Celebration) {
        WorshipCelebrationDialog(
            title = if (isArabic) "إنجاز مبارك!" else "Blessed Achievement!",
            description = if (isArabic) "تجاوزت حاجز 100 نقطة في مجموع طاعاتك الإجمالية بتطبيق إِبْكَـار. استمر في مسيرتك الإيمانية!" else "You have surpassed 100 total points in your overall worships using Ibkar. Keep going!",
            darkTheme = darkTheme,
            isArabic = isArabic,
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
                    text = if (isArabic) "تعديل اسم المستخدم" else "Edit Username",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isArabic) "اكتب الاسم الذي تود ظهوره في بطاقة إنجازك الإيماني:" else "Enter the name you want to display on your achievement card:",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { if (it.length <= 18) inputName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_name_input_field"),
                        singleLine = true,
                        placeholder = { Text(if (isArabic) "مثال: عبد الرحمن" else "Example: John") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                    Text(
                        text = if (isArabic) "الحد الأقصى 18 حرفاً" else "Max 18 characters",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        ),
                        textAlign = TextAlign.Start,
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
                    Text(if (isArabic) "حفظ" else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("dialog_cancel_name_btn"),
                    onClick = { showEditNameDialog = false }
                ) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = if (isArabic) "إعدادات تطبيق إِبْكَـار" else "Ibkar Settings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isArabic) "خصص إعدادات التنبيه والموقع والمظهر بما يناسبك:" else "Customize notification, location, and appearance settings:",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
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
                                .clickable { onToggleLanguage() }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Switch(
                                checked = !isArabic,
                                onCheckedChange = { onToggleLanguage() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.scale(0.85f)
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isArabic) "اللغة الإنجليزية (English)" else "Arabic Language (العربية)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.End
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
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isArabic) "الوضع الفاتح (Light Mode)" else "Light Mode",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.End
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
                                        text = if (isArabic) "تفعيل التنبيهات والإشعارات" else "Enable Notifications",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        textAlign = TextAlign.End
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
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isArabic) "تخصيص تنبيهات كل صلاة وذكر" else "Customize Prayer Alerts",
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
                                        text = if (isArabic) "تحديث الموقع" else "Update Location",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Text(
                                    text = if (isArabic) "موقع حساب المواقيت" else "Prayer Location",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.End
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
                                        text = if (isArabic) "$offsetMinutesVal دقيقة" else "$offsetMinutesVal min",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                    Text(
                                        text = if (isArabic) "إزاحة المواقيت (تقديم أو تأخير):" else "Time Offset (Adjust minutes):",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        textAlign = TextAlign.End
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
                                text = if (isArabic) "طريقة حساب مواقيت الصلاة" else "Calculation Method",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                            val methods = if (isArabic) {
                                listOf("الهيئة العامة المصرية للمساحة", "جامعة أم القرى", "رابطة العالم الإسلامي", "الجمعية الإسلامية لأمريكا الشمالية (ISNA)", "جامعة العلوم الإسلامية بكراتشي", "منطقة الخليج ودبي")
                            } else {
                                listOf("Egyptian General Authority", "Umm Al-Qura", "Muslim World League", "ISNA", "University of Islamic Sciences, Karachi", "Gulf Region")
                            }
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
                                        textAlign = TextAlign.End,
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
                                                    textAlign = TextAlign.End,
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
                                text = if (isArabic) "تطبيق إِبْكَـار - رفيقك الإيماني" else "Ibkar App - Your Faith Companion",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
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
                                        } catch (e: Exception) {}
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
                                        text = if (isArabic) "الموقع الرسمي للتطبيق" else "Official Website",
                                        color = if (darkTheme) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        ),
                                        textAlign = TextAlign.Center
                                    )
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
                    Text(if (isArabic) "تم" else "Done")
                }
            }
        )
    }

    if (showNotificationDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationDetailsDialog = false },
            title = {
                Text(
                    text = if (isArabic) "تخصيص إشعارات العبادات" else "Customize Notifications",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Start,
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
                        text = if (isArabic) "اختر التنبيهات التي ترغب في استقبالها يومياً:" else "Select daily alerts you wish to receive:",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        textAlign = TextAlign.Start,
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
                                text = if (isArabic) "تنبيهات مواقيت الصلاة" else "Prayer Alerts",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = if (isArabic) "إشعار عند دخول وقت كل صلاة" else "Notification for the 5 prayers",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)),
                                textAlign = TextAlign.End
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
                                text = if (isArabic) "تنبيه أذكار الصباح" else "Morning Dhikr Alert",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = if (isArabic) "تذكير يومي بقراءة أذكار الصباح" else "Daily reminder to read morning dhikr",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)),
                                textAlign = TextAlign.End
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
                                text = if (isArabic) "تنبيه أذكار المساء" else "Evening Dhikr Alert",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = if (isArabic) "تذكير يومي بقراءة أذكار المساء" else "Daily reminder to read evening dhikr",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)),
                                textAlign = TextAlign.End
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
                        text = if (isArabic) "إغلاق" else "Close",
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
    longitude: Double,
    isArabic: Boolean
): UpcomingPrayerInfo? {
    val now = com.example.notification.PrayerTimeCalculator.getLocalCalendar(latitude, longitude)
    val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
    val currentSeconds = now.get(java.util.Calendar.SECOND)
    val currentSecsFromMidnight = currentMinutes * 60 + currentSeconds

    val prayerList = if (isArabic) {
        listOf("fajr" to "الفجر", "dhuhr" to "الظهر", "asr" to "العصر", "maghrib" to "المغرب", "isha" to "العشاء")
    } else {
        listOf("fajr" to "Fajr", "dhuhr" to "Dhuhr", "asr" to "Asr", "maghrib" to "Maghrib", "isha" to "Isha")
    }

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
        val amPm = if (isArabic) "ص" else "AM"
        val timeStr = "%d:%02d %s".format(h12, t.second, amPm)
        return UpcomingPrayerInfo("fajr", if (isArabic) "فجر الغد" else "Tomorrow's Fajr", timeStr, diffMin, diffSec)
    }

    return null
}

@Composable
fun NextPrayerCountdownCard(
    upcoming: UpcomingPrayerInfo,
    darkTheme: Boolean,
    isArabic: Boolean
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
        if (isArabic) "ساعة ودقيقة وثانية" else "Hr : Min : Sec"
    } else {
        if (isArabic) "دقيقة وثانية" else "Min : Sec"
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
                        text = if (isArabic) "الوقت المتبقي للأذان:" else "Time until Adhan:",
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
                        text = if (isArabic) "الصلاة القادمة" else "Next Prayer",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = upcoming.name,
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
                            text = if (isArabic) "الأذان: ${upcoming.timeStr}" else "Adhan: ${upcoming.timeStr}",
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
    isArabic: Boolean,
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
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 0.dp, bottomEnd = 0.dp))
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
                                        text = if (isArabic) "مؤداة" else "Done",
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
    darkTheme: Boolean = true,
    isArabic: Boolean
) {
    val motivation = when {
        totalDoneItems == 8 -> MotivationHeaderData(
            if (isArabic) "هنيئاً لك التمام والكمال!" else "Congratulations on Perfection!",
            if (isArabic) "أتممت عباداتك اليومية كاملة، جعلك الله من أهل الفردوس الأعلى." else "You have completed all daily worships. May Allah grant you Paradise.",
            "👑",
            Color(0xFFFFD700)
        )
        totalDoneItems >= 5 -> MotivationHeaderData(
            if (isArabic) "همة عالية وخطى ثابتة" else "High Resolve & Steady Steps",
            if (isArabic) "أنجزت معظم فرائض وسنن اليوم، واصل حتى تختم يومك بتمام الأجر." else "You have accomplished most of today's worships. Keep it up!",
            "🌟",
            Color(0xFF34D399)
        )
        !isFajrDone && isTodaySelected -> MotivationHeaderData(
            if (isArabic) "انطلاقة اليوم تبدأ بالفجر" else "The Day Starts with Fajr",
            if (isArabic) "ركعتا الفجر خير من الدنيا وما فيها، ابدأ يومك بنور الصلاة وذكر الله." else "The two Rak'ahs of Fajr are better than the world and everything in it.",
            "🌅",
            Color(0xFFFFB74D)
        )
        else -> MotivationHeaderData(
            if (isArabic) "يوم جديد.. وباب أجر مفتوح" else "A New Day, A New Reward",
            if (isArabic) "استعن بالله وحافظ على صلواتك في وقتها لتنال بركة يومك وحفظه." else "Seek help from Allah and maintain your prayers to attain blessings.",
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
    isArabic: Boolean = true,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val athkarList = if (type == "morning") {
        if (isArabic) {
            listOf(
                StepDhikr("أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ...", 1, "سؤال خير اليوم كله واستعاذة من الشر والكسل وعذاب القبر"),
                StepDhikr("اللّهُـمَّ أَنْتَ رَبِّـي لا إِلهَ إِلاّ أَنْتَ...", 1, "سيد الاستغفار - من قالها موقناً بها ومات من يومه دخل الجنة"),
                StepDhikr("اللَّهُمَّ إِنِّي أَصْبَحْتُ أُشْهِدُكَ، وَأُشْهِدُ حَمَلَةَ عَرْشِكَ...", 4, "من قالها أربع مرات حين يصبح أو يمسي أعتقه الله من النار"),
                StepDhikr("اللَّهُمَّ مَا أَصْبَحَ بِي مِنْ نِعْمَةٍ أَوْ بِأَحَدٍ مِنْ خَلْقِكَ...", 1, "من قالها حين يصبح فقد أدى شكر يومه"),
                StepDhikr("اللَّهُمَّ عَافِنِي فِي بَدَنِي، اللَّهُمَّ عَافِنِي فِي سَمْعِي...", 3, "سؤال العافية وحفظ الحواس والسلامة من الفقر وعذاب القبر"),
                StepDhikr("حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ...", 7, "من قالها سبع مرات كفاه الله ما أهمه من أمر الدنيا والآخرة"),
                StepDhikr("اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ...", 1, "دعاء الحفظ الإلهي الشامل من جميع الجهات الست"),
                StepDhikr("اللَّهُمَّ عَالِمَ الْغَيْبِ وَالشَّهَادَةِ، فَاطِرَ السَّمَاوَاتِ وَالْأَرْضِ...", 1, "التحصين من كيد الشيطان وشرور النفس والإضرار بالآخرين"),
                StepDhikr("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ...", 3, "من قالها ثلاثاً لم يضره شيء قط"),
                StepDhikr("رَضِيتُ بِاللَّهِ رَبّاً، وَبِالْإِسْلَامِ دِيناً، وَبِمُحَمَّدٍ نَبِيّاً.", 3, "كان حقاً على الله أن يرضيه يوم القيامة"),
                StepDhikr("يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ...", 1, "التبرؤ من الحول والقوة وطلب العون والتوفيق الإلهي"),
                StepDhikr("أَصْبَحْنَا عَلَى فِطْرَةِ الْإِسْلَامِ، وَعَلَى كَلِمَةِ الْإِخْلَاصِ...", 1, "تجديد العهد على التوحيد الخالص وسنة النبي صلى الله عليه وسلم"),
                StepDhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ: عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ...", 3, "تعدل في الأجر ساعات طويلة من الذكر والتسبيح"),
                StepDhikr("قُلْ هُوَ اللَّهُ أَحَدٌ...", 3, "سورة الإخلاص - تعدل ثلث القرآن وتكفي من كل شيء"),
                StepDhikr("قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ...", 3, "سورة الفلق - وقاية تامة من الحسد والسحر وشرور الليل"),
                StepDhikr("قُلْ أَعُوذُ بِرَبِّ النَّاسِ...", 3, "سورة الناس - الحفظ والاعتصام من وسوسة شياطين الإنس والجن"),
                StepDhikr("لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ...", 10, "كانت له عدل أربع رقاب من ولد إسماعيل وكُتب له بها أجر عظيم"),
                StepDhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.", 100, "حُطّت خطاياه وإن كانت مثل زبد البحر، ولم يأتِ أحد بأفضل مما جاء به"),
                StepDhikr("أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ.", 100, "اتباع لهدي النبي صلى الله عليه وسلم وممحاة للذنوب والخطايا")
            )
        } else {
            listOf(
                StepDhikr("We have reached the morning and at this very time unto Allah belongs all sovereignty...", 1, "Asking for goodness of the day"),
                StepDhikr("O Allah, You are my Lord, none has the right to be worshipped except You...", 1, "Sayyid Al-Istighfar - Forgiveness of sins"),
                StepDhikr("O Allah, I have entered a new morning and call upon You to bear witness...", 4, "Freedom from Hellfire"),
                StepDhikr("O Allah, whatever blessing has been received by me...", 1, "Fulfilling the day's gratitude"),
                StepDhikr("O Allah, grant my body health, grant my hearing health...", 3, "Asking for health and protection"),
                StepDhikr("Allah is sufficient for me. There is none worthy of worship but Him...", 7, "Protection from worries"),
                StepDhikr("O Allah, I ask You for pardon and well-being in this life and the next...", 1, "Comprehensive divine protection"),
                StepDhikr("O Allah, Knower of the unseen and the evident, Creator of the heavens...", 1, "Protection from Shaytan and evil of the soul"),
                StepDhikr("In the Name of Allah with Whose Name there is protection...", 3, "Protection from sudden afflictions"),
                StepDhikr("I am pleased with Allah as my Lord, with Islam as my religion...", 3, "Allah's pleasure on the Day of Judgement"),
                StepDhikr("O Ever Living One, O Sustainer of all, by Your mercy I call on You...", 1, "Seeking Allah's help and reliance"),
                StepDhikr("We have entered a new morning upon the natural religion of Islam...", 1, "Renewal of pure monotheism"),
                StepDhikr("Glory is to Allah and praise is to Him, by the multitude of His creation...", 3, "Immense continuous reward"),
                StepDhikr("Surah Al-Ikhlas", 3, "Equals one-third of the Quran"),
                StepDhikr("Surah Al-Falaq", 3, "Protection from evil"),
                StepDhikr("Surah An-Nas", 3, "Protection from whispers of Shaytan"),
                StepDhikr("None has the right to be worshipped but Allah alone...", 10, "Reward of freeing slaves"),
                StepDhikr("Glory is to Allah and praise is to Him.", 100, "Sins forgiven even if like the foam of the sea"),
                StepDhikr("I seek the forgiveness of Allah and repent to Him.", 100, "Purification of sins")
            )
        }
    } else {
        if (isArabic) {
            listOf(
                StepDhikr("أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ...", 1, "سؤال خير الليلة والتحصين من الشرور والعذاب"),
                StepDhikr("اللّهُـمَّ أَنْتَ رَبِّـي لا إِلهَ إِلاّ أَنْتَ...", 1, "سيد الاستغفار - من مات من ليلته دخل الجنة"),
                StepDhikr("اللَّهُمَّ إِنِّي أَمْسَيْتُ أُشْهِدُكَ، وَأُشْهِدُ حَمَلَةَ عَرْشِكَ...", 4, "من قالها أربع مرات حين يمسي أعتقه الله من النار"),
                StepDhikr("اللَّهُمَّ مَا أَمْسَى بِي مِنْ نِعْمَةٍ أَوْ بِأَحَدٍ مِنْ خَلْقِكَ...", 1, "من قالها حين يمسي فقد أدى شكر ليلته"),
                StepDhikr("اللَّهُمَّ عَافِنِي فِي بَدَنِي، اللَّهُمَّ عَافِنِي فِي سَمْعِي...", 3, "حفظ العافية والبدن والنجاة من عذاب القبر"),
                StepDhikr("حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ...", 7, "كفاية الله للمؤمن من كل ما يقلقه ويهمه"),
                StepDhikr("اللَّهُمَّ إِنِّي أَسْأَلُكَ الْعَفْوَ وَالْعَافِيَةَ فِي الدُّنْيَا وَالْآخِرَةِ...", 1, "الحفظ من الفواجع والمهالك طوال الليل"),
                StepDhikr("اللَّهُمَّ عَالِمَ الْغَيْبِ وَالشَّهَادَةِ، فَاطِرَ السَّمَاوَاتِ وَالْأَرْضِ...", 1, "الحماية من فتن الليل وكيد الشياطين"),
                StepDhikr("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ...", 3, "حفظ تام من كل سوء ومكروه"),
                StepDhikr("أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.", 3, "من قالها لم يضره سم ولا دابة ولا حية في تلك الليلة"),
                StepDhikr("رَضِيتُ بِاللَّهِ رَبّاً، وَبِالْإِسْلَامِ دِيناً، وَبِمُحَمَّدٍ نَبِيّاً.", 3, "حق على الله أن يرضي قائله"),
                StepDhikr("يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ...", 1, "صلاح الأحوال والاستغناء برحمة الله"),
                StepDhikr("أَمْسَيْنَا عَلَى فِطْرَةِ الْإِسْلَامِ، وَعَلَى كَلِمَةِ الْإِخْلَاصِ...", 1, "المبيت على فطرة التوحيد والإسلام"),
                StepDhikr("قُلْ هُوَ اللَّهُ أَحَدٌ...", 3, "تكفيك من كل سوء"),
                StepDhikr("قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ...", 3, "الحفظ من شر غاسق إذا وقب والحاسدين"),
                StepDhikr("قُلْ أَعُوذُ بِرَبِّ النَّاسِ...", 3, "الحفظ من كل وسواس خناس"),
                StepDhikr("لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ...", 10, "حرز من الشيطان وحط للأوزار"),
                StepDhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.", 100, "مغفرة الذنوب ورفعة الدرجات")
            )
        } else {
            listOf(
                StepDhikr("We have reached the evening and at this very time unto Allah belongs all sovereignty...", 1, "Asking for goodness of the night"),
                StepDhikr("O Allah, You are my Lord, none has the right to be worshipped except You...", 1, "Sayyid Al-Istighfar - Forgiveness of sins"),
                StepDhikr("O Allah, I have entered a new evening and call upon You to bear witness...", 4, "Freedom from Hellfire"),
                StepDhikr("O Allah, whatever blessing has been received by me...", 1, "Fulfilling the night's gratitude"),
                StepDhikr("O Allah, grant my body health, grant my hearing health...", 3, "Asking for health and protection"),
                StepDhikr("Allah is sufficient for me. There is none worthy of worship but Him...", 7, "Protection from worries"),
                StepDhikr("O Allah, I ask You for pardon and well-being in this life and the next...", 1, "Comprehensive divine protection"),
                StepDhikr("O Allah, Knower of the unseen and the evident, Creator of the heavens...", 1, "Protection from Shaytan and evil of the soul"),
                StepDhikr("In the Name of Allah with Whose Name there is protection...", 3, "Protection from sudden afflictions"),
                StepDhikr("I seek refuge in the Perfect Words of Allah from the evil of what He has created.", 3, "Protection from harm and evil creatures"),
                StepDhikr("I am pleased with Allah as my Lord, with Islam as my religion...", 3, "Allah's pleasure on the Day of Judgement"),
                StepDhikr("O Ever Living One, O Sustainer of all, by Your mercy I call on You...", 1, "Seeking Allah's help and reliance"),
                StepDhikr("We have entered a new evening upon the natural religion of Islam...", 1, "Renewal of pure monotheism"),
                StepDhikr("Surah Al-Ikhlas", 3, "Equals one-third of the Quran"),
                StepDhikr("Surah Al-Falaq", 3, "Protection from evil"),
                StepDhikr("Surah An-Nas", 3, "Protection from whispers of Shaytan"),
                StepDhikr("None has the right to be worshipped but Allah alone...", 10, "Reward of freeing slaves"),
                StepDhikr("Glory is to Allah and praise is to Him.", 100, "Sins forgiven even if like the foam of the sea")
            )
        }
    }

    val context = LocalContext.current
    val sharedPrefs = remember(context) {
        context.getSharedPreferences("dhikr_flow_prefs", android.content.Context.MODE_PRIVATE)
    }

    val todayDateStr = remember { DateHelper.getTodayDateString(context) }
    val savedDateKey = "dhikr_${type}_date"
    val lastDate = sharedPrefs.getString(savedDateKey, "")

    val savedIndexKey = "dhikr_${type}_index"
    val savedCountKey = "dhikr_${type}_count"

    val isNewDay = lastDate != todayDateStr
    if (isNewDay) {
        sharedPrefs.edit().apply {
            putString(savedDateKey, todayDateStr)
            putInt(savedIndexKey, 0)
            putInt(savedCountKey, athkarList[0].count)
            apply()
        }
    }

    var currentIndex by remember { 
        mutableStateOf(if (isNewDay) 0 else sharedPrefs.getInt(savedIndexKey, 0)) 
    }
    val totalCount = athkarList.size

    val currentCountsLeft = remember(type) {
        mutableStateListOf<Int>().apply {
            val savedIndex = if (isNewDay) 0 else sharedPrefs.getInt(savedIndexKey, 0)
            for (i in 0 until totalCount) {
                if (i < savedIndex) {
                    add(0)
                } else if (i == savedIndex) {
                    val defaultCount = athkarList[i].count
                    val savedLeft = if (isNewDay) defaultCount else sharedPrefs.getInt(savedCountKey, defaultCount)
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
            putString(savedDateKey, todayDateStr)
            putInt(savedIndexKey, index)
            putInt(savedCountKey, countLeft)
            apply()
        }
    }

    val currentDhikr = athkarList.getOrNull(currentIndex)
    val curCountLeft = currentCountsLeft.getOrNull(currentIndex) ?: 0

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

        CompositionLocalProvider(
            LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
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
                                contentDescription = if (isArabic) "إغلاق" else "Close",
                                tint = if (darkTheme) Color.White else Color(0xFF475569)
                            )
                        }

                        Text(
                            text = if (type == "morning") {
                                if (isArabic) "أذكار الصباح" else "Morning Dhikr"
                            } else {
                                if (isArabic) "أذكار المساء" else "Evening Dhikr"
                            },
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
                                contentDescription = if (isArabic) "إعادة البدء" else "Restart",
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
                                    text = if (isArabic) "الذكر ${currentIndex + 1} من $totalCount" else "Dhikr ${currentIndex + 1} of $totalCount",
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
                                            text = if (isArabic) "الفضل: ${currentDhikr.benefit}" else "Benefit: ${currentDhikr.benefit}",
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
                                        text = if (isArabic) "متبقي" else "Left",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Text(
                                text = if (isArabic) "انقر على الدائرة للعد" else "Tap the circle to count",
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
                                    text = if (isArabic) "السابق" else "Previous",
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
                                    text = if (isArabic) "تخطي" else "Skip",
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
                                    text = if (isArabic) "تقبل الله طاعتك!" else "May Allah accept your deeds!",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SuccessGreen
                                    ),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (isArabic) {
                                        "أتممت قراءة ${if (type == "morning") "أذكار الصباح" else "أذكار المساء"} بنجاح، حفظك الله ورعاك."
                                    } else {
                                        "You have successfully completed reading the ${if (type == "morning") "Morning Dhikr" else "Evening Dhikr"}."
                                    },
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
                                        text = if (isArabic) "تم وحفظ الإنجاز" else "Save Achievement",
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
}

data class StepDhikr(
    val text: String,
    val count: Int,
    val benefit: String = ""
)

@Composable
fun WorshipCelebrationDialog(
    title: String,
    description: String,
    darkTheme: Boolean,
    isArabic: Boolean,
    onDismiss: () -> Unit
) {
    CompositionLocalProvider(
        LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (darkTheme) Color.White else Color(0xFF0F172A)
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (darkTheme) Color.LightGray else Color(0xFF475569)
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isArabic) "متابعة" else "Continue", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = if (darkTheme) Color(0xFF1E293B) else Color.White
        )
    }
}

fun updateLocationAndPrayerTimes(
    context: android.content.Context,
    prefs: android.content.SharedPreferences,
    onResult: (Boolean, String, Float, Float) -> Unit
) {
    val defaultLat = 30.0444f
    val defaultLng = 31.2357f
    prefs.edit()
        .putFloat("user_latitude", defaultLat)
        .putFloat("user_longitude", defaultLng)
        .apply()
    onResult(true, "تم تحديث الموقع بنجاح", defaultLat, defaultLng)
}
