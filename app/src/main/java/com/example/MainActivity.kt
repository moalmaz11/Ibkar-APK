package com.moalmaz.ibkar

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moalmaz.ibkar.data.DailyRecord
import com.moalmaz.ibkar.data.DateHelper
import com.moalmaz.ibkar.data.WorshipDatabase
import com.moalmaz.ibkar.ui.WorshipViewModel
import com.moalmaz.ibkar.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

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
            var isArabic by remember { mutableStateOf(themePrefs.getBoolean("is_arabic", true)) }

            MyApplicationTheme(darkTheme = true) {
                CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
                        MainAppNavigation(
                            isArabic = isArabic,
                            onToggleLanguage = { val nv = !isArabic; isArabic = nv; themePrefs.edit().putBoolean("is_arabic", nv).apply() },
                            initialDhikrType = initialDhikrTypeState.value,
                            onInitialDhikrHandled = { initialDhikrTypeState.value = null }
                        )
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }
    private fun handleIntent(intent: Intent?) { val type = intent?.getStringExtra("OPEN_DHIKR"); if (type != null) initialDhikrTypeState.value = type }
}

enum class AppRoute { Home, Stats, FullScreenHisn, FullScreenDhikr }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavigation(isArabic: Boolean, onToggleLanguage: () -> Unit, viewModel: WorshipViewModel = viewModel(), initialDhikrType: String?, onInitialDhikrHandled: () -> Unit) {
    val context = LocalContext.current
    var currentRoute by remember { mutableStateOf(AppRoute.Home) }
    var activeDhikrType by remember { mutableStateOf<String?>(initialDhikrType) }
    var historyData by remember { mutableStateOf<List<DailyRecord>>(emptyList()) }
    val record by viewModel.currentRecord.collectAsStateWithLifecycle()
    val streak by viewModel.currentStreak.collectAsStateWithLifecycle()
    val prefs = remember(context) { context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE) }
    var notifyPrayers by remember { mutableStateOf(prefs.getBoolean("notify_prayers", true)) }
    var notifyMorningDhikr by remember { mutableStateOf(prefs.getBoolean("notify_morning_dhikr", true)) }
    var notifyEveningDhikr by remember { mutableStateOf(prefs.getBoolean("notify_evening_dhikr", true)) }
    var offsetMinutesVal by remember { mutableStateOf(prefs.getInt("prayer_offset_minutes", 0)) }
    val cityName = if (isArabic) prefs.getString("user_city_name_ar", "القاهرة") ?: "القاهرة" else prefs.getString("user_city_name_en", "Cairo") ?: "Cairo"

    var showTasbeehDialog by remember { mutableStateOf(false) }
    var showWirdDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialDhikrType) {
        if (initialDhikrType != null) { activeDhikrType = initialDhikrType; currentRoute = AppRoute.FullScreenDhikr; onInitialDhikrHandled() }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == AppRoute.Stats) {
            withContext(Dispatchers.IO) {
                try {
                    val db = WorshipDatabase.getDatabase(context)
                    val cursor = db.openHelper.readableDatabase.query("SELECT * FROM daily_record ORDER BY date ASC")
                    val list = mutableListOf<DailyRecord>()
                    while (cursor.moveToNext()) {
                        list.add(DailyRecord(
                            date = cursor.getString(cursor.getColumnIndex("date")),
                            fajrDone = cursor.getInt(cursor.getColumnIndex("fajrDone")) == 1,
                            dhuhrDone = cursor.getInt(cursor.getColumnIndex("dhuhrDone")) == 1,
                            asrDone = cursor.getInt(cursor.getColumnIndex("asrDone")) == 1,
                            maghribDone = cursor.getInt(cursor.getColumnIndex("maghribDone")) == 1,
                            ishaDone = cursor.getInt(cursor.getColumnIndex("ishaDone")) == 1,
                            quranPages = cursor.getInt(cursor.getColumnIndex("quranPages")),
                            morningDhikrDone = cursor.getInt(cursor.getColumnIndex("morningDhikrDone")) == 1,
                            eveningDhikrDone = cursor.getInt(cursor.getColumnIndex("eveningDhikrDone")) == 1
                        ))
                    }
                    cursor.close(); historyData = list
                } catch (e: Exception) {}
            }
        }
    }

    val activeRecord = record ?: DailyRecord(date = DateHelper.getTodayDateString(context))
    val prayersDoneCount = listOf(activeRecord.fajrDone, activeRecord.dhuhrDone, activeRecord.asrDone, activeRecord.maghribDone, activeRecord.ishaDone).count { it }
    val isQuranDone = activeRecord.quranPages > 0
    val totalDoneItems = prayersDoneCount + (if (isQuranDone) 1 else 0) + (if (activeRecord.morningDhikrDone) 1 else 0) + (if (activeRecord.eveningDhikrDone) 1 else 0)
    val dailyPoints = activeRecord.calculatePoints()
    
    val todayTimesRaw = remember(prefs.getFloat("user_latitude", 30.0444f), prefs.getFloat("user_longitude", 31.2357f), prefs.getInt("prayer_calc_method", 0)) {
        val cal = com.moalmaz.ibkar.notification.PrayerTimeCalculator.getLocalCalendar(prefs.getFloat("user_latitude", 30.0444f).toDouble(), prefs.getFloat("user_longitude", 31.2357f).toDouble())
        com.moalmaz.ibkar.notification.PrayerTimeCalculator.calculatePrayerTimes(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), prefs.getFloat("user_latitude", 30.0444f).toDouble(), prefs.getFloat("user_longitude", 31.2357f).toDouble(), prefs.getInt("prayer_calc_method", 0))
    }
    var upcomingPrayerInfoState by remember(todayTimesRaw) { mutableStateOf<UpcomingPrayerInfo?>(null) }
    val isTodaySelected = activeRecord.date == DateHelper.getTodayDateString(context)

    LaunchedEffect(todayTimesRaw, isTodaySelected) {
        if (isTodaySelected) { while (true) { upcomingPrayerInfoState = getUpcomingPrayer(todayTimesRaw, prefs.getFloat("user_latitude", 30.0444f).toDouble(), prefs.getFloat("user_longitude", 31.2357f).toDouble(), isArabic); kotlinx.coroutines.delay(1000L) } }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute == AppRoute.Home || currentRoute == AppRoute.Stats) {
                NavigationBar(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f), contentColor = GlassWhite) {
                    NavigationBarItem(
                        icon = { Icon(if (currentRoute == AppRoute.Home) Icons.Filled.Home else Icons.Outlined.Home, null) },
                        label = { Text(if (isArabic) "الرئيسية" else "Home") },
                        selected = currentRoute == AppRoute.Home,
                        onClick = { currentRoute = AppRoute.Home },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = GlassAccent, selectedTextColor = GlassAccent, unselectedIconColor = GlassAccentLight, unselectedTextColor = GlassAccentLight, indicatorColor = GlassPanelBg)
                    )
                    NavigationBarItem(
                        icon = { Icon(if (currentRoute == AppRoute.Stats) Icons.Filled.BarChart else Icons.Outlined.BarChart, null) },
                        label = { Text(if (isArabic) "السجل" else "Stats") },
                        selected = currentRoute == AppRoute.Stats,
                        onClick = { currentRoute = AppRoute.Stats },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = GlassAccent, selectedTextColor = GlassAccent, unselectedIconColor = GlassAccentLight, unselectedTextColor = GlassAccentLight, indicatorColor = GlassPanelBg)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(GlassBgGradient)).padding(paddingValues)) {
            when (currentRoute) {
                AppRoute.Home -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopStreakBar(streak = streak, cityName = cityName, isArabic = isArabic, onSettingsClick = { showSettingsDialog = true })
                        HomeScreen(
                            isArabic = isArabic, record = activeRecord, dailyPoints = dailyPoints, totalDoneItems = totalDoneItems,
                            upcomingPrayer = upcomingPrayerInfoState, todayTimes = todayTimesRaw,
                            onTogglePrayer = { if (isTodaySelected) viewModel.togglePrayer(it) },
                            onOpenDhikr = { type -> activeDhikrType = type; currentRoute = AppRoute.FullScreenDhikr },
                            onOpenWird = { showWirdDialog = true },
                            onOpenTasbeeh = { showTasbeehDialog = true },
                            onOpenHisn = { currentRoute = AppRoute.FullScreenHisn }
                        )
                    }
                }
                AppRoute.Stats -> AdvancedStatsScreen(isArabic = isArabic, history = historyData)
                AppRoute.FullScreenHisn -> FullScreenHisn(isArabic = isArabic, onBack = { currentRoute = AppRoute.Home })
                AppRoute.FullScreenDhikr -> {
                    activeDhikrType?.let { type ->
                        FullScreenDhikrReading(
                            type = type, isArabic = isArabic, onDismiss = { currentRoute = AppRoute.Home },
                            onComplete = {
                                val isDone = if (type == "morning") record.morningDhikrDone else record.eveningDhikrDone
                                if (!isDone) { if (type == "morning") viewModel.toggleMorningDhikr() else viewModel.toggleEveningDhikr() }
                                currentRoute = AppRoute.Home
                            }
                        )
                    }
                }
                else -> {}
            }

            if (showTasbeehDialog) {
                TasbeehGlassDialog(isArabic = isArabic, count = activeRecord.dhikrCount, onIncrement = { if (isTodaySelected) viewModel.incrementDhikr() }, onReset = { if (isTodaySelected) viewModel.resetDhikr() }, onDismiss = { showTasbeehDialog = false })
            }
            if (showWirdDialog) {
                WirdGlassDialog(isArabic = isArabic, pages = activeRecord.quranPages, onIncrease = { if (isTodaySelected) viewModel.setQuranPages(activeRecord.quranPages + 1) }, onDecrease = { if (isTodaySelected) viewModel.setQuranPages(activeRecord.quranPages - 1) }, onDismiss = { showWirdDialog = false })
            }
            if (showSettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showSettingsDialog = false }, containerColor = Color(0xFF1E1B4B),
                    title = { Text(if (isArabic) "الإعدادات" else "Settings", color = GlassWhite, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if(isArabic) "Change to English" else "تغيير للغة العربية", color = GlassWhite)
                                Switch(checked = !isArabic, onCheckedChange = { onToggleLanguage() }, colors = SwitchDefaults.colors(checkedThumbColor = GlassAccent, checkedTrackColor = GlassAccent.copy(alpha=0.5f)))
                            }
                            HorizontalDivider(color = GlassPanelBorder)
                            Text(if(isArabic) "تخصيص الإشعارات" else "Notifications", color = GlassAccentLight, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if(isArabic) "تنبيهات الصلوات" else "Prayer Alerts", color = GlassWhite)
                                Switch(checked = notifyPrayers, onCheckedChange = { notifyPrayers = it; prefs.edit().putBoolean("notify_prayers", it).apply(); com.moalmaz.ibkar.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, colors = SwitchDefaults.colors(checkedThumbColor = GlassSuccess, checkedTrackColor = GlassSuccess.copy(alpha=0.5f)))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if(isArabic) "أذكار الصباح" else "Morning Dhikr", color = GlassWhite)
                                Switch(checked = notifyMorningDhikr, onCheckedChange = { notifyMorningDhikr = it; prefs.edit().putBoolean("notify_morning_dhikr", it).apply(); com.moalmaz.ibkar.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, colors = SwitchDefaults.colors(checkedThumbColor = GlassSuccess, checkedTrackColor = GlassSuccess.copy(alpha=0.5f)))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if(isArabic) "أذكار المساء" else "Evening Dhikr", color = GlassWhite)
                                Switch(checked = notifyEveningDhikr, onCheckedChange = { notifyEveningDhikr = it; prefs.edit().putBoolean("notify_evening_dhikr", it).apply(); com.moalmaz.ibkar.notification.PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, colors = SwitchDefaults.colors(checkedThumbColor = GlassSuccess, checkedTrackColor = GlassSuccess.copy(alpha=0.5f)))
                            }
                            HorizontalDivider(color = GlassPanelBorder)
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (isArabic) "إِبْكَـار - صُنع بكل حب بواسطة مصطفى الماظ" else "Ibkar - Made with love by Mostafa Almaz", style = MaterialTheme.typography.labelSmall.copy(color = GlassAccentLight, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ibkar.vercel.app"))) } catch (e: Exception) {} }, colors = ButtonDefaults.buttonColors(containerColor = GlassAccent.copy(alpha = 0.2f))) { Text(if (isArabic) "الموقع" else "Web", color = GlassAccent) }
                                    Button(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/ibkar.application"))) } catch (e: Exception) {} }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2).copy(alpha = 0.2f))) { Text("Facebook", color = Color(0xFF8A93FC)) }
                                }
                            }
                        }
                    },
                    confirmButton = { Button(onClick = { showSettingsDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = GlassAccent)) { Text(if (isArabic) "تم" else "Done", color = GlassWhite) } }
                )
            }
        }
    }
}
