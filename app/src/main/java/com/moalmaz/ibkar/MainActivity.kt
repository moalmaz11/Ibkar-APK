package com.moalmaz.ibkar

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moalmaz.ibkar.data.DailyRecord
import com.moalmaz.ibkar.data.DateHelper
import com.moalmaz.ibkar.data.WorshipDatabase
import com.moalmaz.ibkar.notification.PrayerNotificationManager
import com.moalmaz.ibkar.notification.PrayerTimeCalculator
import com.moalmaz.ibkar.ui.WorshipViewModel
import com.moalmaz.ibkar.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.random.Random

// ==========================================
// 1. Data & Helpers
// ==========================================
data class CityLocation(val nameAr: String, val nameEn: String, val lat: Float, val lng: Float)
val egyptCities = listOf(
    CityLocation("القاهرة", "Cairo", 30.0444f, 31.2357f), CityLocation("الإسكندرية", "Alexandria", 31.2001f, 29.9187f)
)

data class StepDhikr(val text: String, val count: Int, val benefit: String = "", val translation: String = "")
val morningAdhkarList = listOf(StepDhikr("أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ...", 1))
val eveningAdhkarList = listOf(StepDhikr("أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ...", 1))
val hisnAlMuslimData = mapOf("sleep" to listOf(StepDhikr("بِاسْمِكَ رَبِّـي وَضَعْـتُ جَنْـبي...", 1)))

// ==========================================
// 2. Colors & Design System
// ==========================================
val GlassBgGradient = listOf(Color(0xFF0F1123), Color(0xFF1B183E), Color(0xFF130E2B))
val GlassAccent = Color(0xFF6B7BFF)
val GlassAccentLight = Color(0xFFA5B4FC)
val GlassWhite = Color.White
val GlassPanelBg = Color.White.copy(alpha = 0.05f)
val GlassPanelBorder = Color.White.copy(alpha = 0.10f)
val GlassSuccess = Color(0xFF10B981)

class MainActivity : ComponentActivity() {
    private var initialDhikrTypeState = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PrayerNotificationManager.createNotificationChannel(this)
        handleIntent(intent)
        setContent {
            val context = LocalContext.current
            val themePrefs = remember(context) { context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE) }
            var isArabic by remember { mutableStateOf(themePrefs.getBoolean("is_arabic", true)) }

            MyApplicationTheme(darkTheme = true) {
                CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F1123)) {
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
    
    val recordState = viewModel.currentRecord.collectAsStateWithLifecycle()
    val record = recordState.value

    val prefs = remember(context) { context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE) }
    var showTasbeehDialog by remember { mutableStateOf(false) }
    var showWirdDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialDhikrType) {
        if (initialDhikrType != null) { activeDhikrType = initialDhikrType; currentRoute = AppRoute.FullScreenDhikr; onInitialDhikrHandled() }
    }

    val activeRecord = record ?: DailyRecord(date = DateHelper.getTodayDateString(context))
    val prayersDoneCount = listOf(activeRecord.fajrDone, activeRecord.dhuhrDone, activeRecord.asrDone, activeRecord.maghribDone, activeRecord.ishaDone).count { it }
    val isQuranDone = activeRecord.quranPages > 0
    val totalDoneItems = prayersDoneCount + (if (isQuranDone) 1 else 0) + (if (activeRecord.morningDhikrDone) 1 else 0) + (if (activeRecord.eveningDhikrDone) 1 else 0)
    val dailyPoints = activeRecord.calculatePoints()
    
    val todayTimesRaw = remember {
        val cal = PrayerTimeCalculator.getLocalCalendar(30.0444, 31.2357)
        PrayerTimeCalculator.calculatePrayerTimes(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), 30.0444, 31.2357, 0)
    }
    var upcomingPrayerInfoState by remember(todayTimesRaw) { mutableStateOf<UpcomingPrayerInfo?>(null) }
    val isTodaySelected = activeRecord.date == DateHelper.getTodayDateString(context)

    LaunchedEffect(todayTimesRaw, isTodaySelected) {
        if (isTodaySelected) { while (true) { upcomingPrayerInfoState = getUpcomingPrayer(todayTimesRaw, 30.0444, 31.2357, isArabic); delay(1000L) } }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute == AppRoute.Home || currentRoute == AppRoute.Stats) {
                NavigationBar(containerColor = Color.Transparent, contentColor = GlassWhite, tonalElevation = 0.dp) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, null, modifier = Modifier.size(24.dp)) },
                        label = { Text(if (isArabic) "الرئيسية" else "Home", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        selected = currentRoute == AppRoute.Home,
                        onClick = { currentRoute = AppRoute.Home },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = GlassAccent, selectedTextColor = GlassAccent, unselectedIconColor = GlassWhite.copy(alpha=0.4f), unselectedTextColor = GlassWhite.copy(alpha=0.4f), indicatorColor = Color.Transparent)
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.List, null, modifier = Modifier.size(24.dp)) },
                        label = { Text(if (isArabic) "السجل" else "Stats", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        selected = currentRoute == AppRoute.Stats,
                        onClick = { currentRoute = AppRoute.Stats },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = GlassAccent, selectedTextColor = GlassAccent, unselectedIconColor = GlassWhite.copy(alpha=0.4f), unselectedTextColor = GlassWhite.copy(alpha=0.4f), indicatorColor = Color.Transparent)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(GlassBgGradient)).padding(paddingValues)) {
            when (currentRoute) {
                AppRoute.Home -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopStreakBar(cityName = if(isArabic) "الإسكندرية، مصر" else "Alexandria, EG")
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
                                val isDone = if (type == "morning") activeRecord.morningDhikrDone else activeRecord.eveningDhikrDone
                                if (!isDone) { if (type == "morning") viewModel.toggleMorningDhikr() else viewModel.toggleEveningDhikr() }
                                currentRoute = AppRoute.Home
                            }
                        )
                    }
                }
            }

            if (showTasbeehDialog) {
                TasbeehGlassDialog(isArabic = isArabic, count = activeRecord.dhikrCount, onIncrement = { if (isTodaySelected) viewModel.incrementDhikr() }, onReset = { if (isTodaySelected) viewModel.resetDhikr() }, onDismiss = { showTasbeehDialog = false })
            }
            if (showWirdDialog) {
                WirdGlassDialog(isArabic = isArabic, pages = activeRecord.quranPages, onIncrease = { if (isTodaySelected) viewModel.setQuranPages(activeRecord.quranPages + 1) }, onDecrease = { if (isTodaySelected) viewModel.setQuranPages(activeRecord.quranPages - 1) }, onDismiss = { showWirdDialog = false })
            }
        }
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    var mod = modifier.clip(RoundedCornerShape(22.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(22.dp))
    if (onClick != null) mod = mod.clickable { onClick() }
    Column(modifier = mod.padding(padding), content = content)
}

@Composable
fun TopStreakBar(cityName: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "إِبْكَـار", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, color = GlassWhite, fontSize = 24.sp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
            Icon(Icons.Filled.LocationOn, null, tint = GlassAccentLight, modifier = Modifier.size(12.dp))
            Text(text = cityName, style = MaterialTheme.typography.labelSmall.copy(color = GlassAccentLight, fontSize = 11.sp))
        }
    }
}

@Composable
fun HomeScreen(
    isArabic: Boolean, record: DailyRecord, dailyPoints: Int, totalDoneItems: Int, 
    upcomingPrayer: UpcomingPrayerInfo?, todayTimes: Map<String, Pair<Int, Int>>, 
    onTogglePrayer: (String) -> Unit, onOpenDhikr: (String) -> Unit, onOpenWird: () -> Unit, onOpenTasbeeh: () -> Unit, onOpenHisn: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        
        item {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = GlassAccentLight, modifier = Modifier.size(32.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = if (isArabic) "إجمالي النقاط" else "Total Points", style = MaterialTheme.typography.labelMedium.copy(color = GlassAccentLight, fontSize = 12.sp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(text = "$dailyPoints", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = GlassWhite, fontSize = 32.sp))
                            Text(text = "/100", style = MaterialTheme.typography.bodyMedium.copy(color = GlassAccentLight, fontWeight = FontWeight.Bold, fontSize = 14.sp), modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                }
                val dayProgress = totalDoneItems.toFloat() / 8f
                Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(10.dp)).background(GlassWhite.copy(alpha = 0.1f))) {
                    Box(modifier = Modifier.fillMaxWidth(dayProgress).height(6.dp).clip(RoundedCornerShape(10.dp)).background(GlassAccent))
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f).height(85.dp).clip(RoundedCornerShape(18.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(18.dp)).clickable { onOpenDhikr("morning") }.padding(8.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = GlassAccentLight, modifier = Modifier.size(24.dp))
                        Text(if (isArabic) "الأذكار" else "Adhkar", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite, fontSize = 13.sp))
                    }
                }
                Box(modifier = Modifier.weight(1f).height(85.dp).clip(RoundedCornerShape(18.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(18.dp)).clickable { onOpenTasbeeh() }.padding(8.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Refresh, null, tint = GlassAccentLight, modifier = Modifier.size(24.dp))
                        Text(if (isArabic) "التسبيح" else "Tasbeeh", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite, fontSize = 13.sp))
                    }
                }
                Box(modifier = Modifier.weight(1f).height(85.dp).clip(RoundedCornerShape(18.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(18.dp)).clickable { onOpenWird() }.padding(8.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.List, null, tint = GlassAccentLight, modifier = Modifier.size(24.dp))
                        Text(if (isArabic) "الورد" else "Wird", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite, fontSize = 13.sp))
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = if (isArabic) "الصلوات المفروضة" else "Obligatory Prayers", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = GlassWhite), modifier = Modifier.padding(bottom = 2.dp))
                
                fun t(k: String): String { val d = todayTimes[k] ?: return ""; val h = if(d.first%12==0) 12 else d.first%12; val a = if(d.first>=12) "م" else "ص"; return "%d:%02d %s".format(h, d.second, a) }
                
                PrayerItemRow(if (isArabic) "الفجر" else "Fajr", record.fajrDone, t("fajr"), upcomingPrayer?.tag == "fajr") { onTogglePrayer("fajr") }
                PrayerItemRow(if (isArabic) "الظهر" else "Dhuhr", record.dhuhrDone, t("dhuhr"), upcomingPrayer?.tag == "dhuhr") { onTogglePrayer("dhuhr") }
                PrayerItemRow(if (isArabic) "العصر" else "Asr", record.asrDone, t("asr"), upcomingPrayer?.tag == "asr") { onTogglePrayer("asr") }
                PrayerItemRow(if (isArabic) "المغرب" else "Maghrib", record.maghribDone, t("maghrib"), upcomingPrayer?.tag == "maghrib") { onTogglePrayer("maghrib") }
                PrayerItemRow(if (isArabic) "العشاء" else "Isha", record.ishaDone, t("isha"), upcomingPrayer?.tag == "isha") { onTogglePrayer("isha") }
            }
        }
    }
}

@Composable
fun PrayerItemRow(name: String, isDone: Boolean, timeText: String, isNext: Boolean, onToggle: () -> Unit) {
    val bgColor = when {
        isDone -> GlassAccent.copy(alpha = 0.15f)
        isNext -> GlassAccent.copy(alpha = 0.25f)
        else -> GlassPanelBg
    }
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bgColor).border(1.dp, if (isNext) GlassAccent else GlassPanelBorder, RoundedCornerShape(16.dp)).clickable { onToggle() }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isDone) Icon(Icons.Filled.CheckCircle, null, tint = GlassAccent, modifier = Modifier.size(22.dp))
        else Canvas(modifier = Modifier.size(20.dp)) { drawCircle(color = GlassWhite.copy(alpha = 0.2f), style = Stroke(width = 3f)) }
        
        Text(text = name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite), modifier = Modifier.weight(1f).padding(start = 16.dp))
        Text(text = timeText, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = GlassAccentLight))
    }
}

data class UpcomingPrayerInfo(val tag: String, val name: String, val timeStr: String, val diffMinutes: Int, val diffSeconds: Int)

fun getUpcomingPrayer(todayTimes: Map<String, Pair<Int, Int>>, latitude: Double, longitude: Double, isArabic: Boolean): UpcomingPrayerInfo? {
    val now = PrayerTimeCalculator.getLocalCalendar(latitude, longitude)
    val cMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val cSec = now.get(Calendar.SECOND)
    val cTotal = cMin * 60 + cSec
    val pList = listOf("fajr" to "الفجر", "dhuhr" to "الظهر", "asr" to "العصر", "maghrib" to "المغرب", "isha" to "العشاء")
    for (p in pList) {
        val t = todayTimes[p.first]
        if (t != null) {
            val pTotal = (t.first * 60 + t.second) * 60
            if (pTotal > cTotal) {
                val r = pTotal - cTotal
                val h12 = if (t.first % 12 == 0) 12 else t.first % 12
                return UpcomingPrayerInfo(p.first, p.second, "%d:%02d %s".format(h12, t.second, if(t.first>=12) "م" else "ص"), r / 60, r % 60)
            }
        }
    }
    return null
}

@Composable
fun AdvancedStatsScreen(isArabic: Boolean, history: List<DailyRecord>) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item { Text(text = if(isArabic) "سجل الإنجازات" else "Achievement Log", style = MaterialTheme.typography.titleMedium.copy(color = GlassWhite)) }
    }
}

@Composable
fun FullScreenHisn(isArabic: Boolean, onBack: () -> Unit) {}

@Composable
fun FullScreenDhikrReading(type: String, isArabic: Boolean, onComplete: () -> Unit, onDismiss: () -> Unit) {}

@Composable
fun TasbeehGlassDialog(isArabic: Boolean, count: Int, onIncrement: () -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF1B183E),
        title = { Text(if(isArabic) "المسبحة الإلكترونية" else "Digital Rosary", color = GlassWhite) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(GlassAccent.copy(0.2f)).border(2.dp, GlassAccent, CircleShape).clickable { onIncrement() }, contentAlignment = Alignment.Center) {
                    Text("$count", fontSize = 40.sp, fontWeight = FontWeight.Black, color = GlassWhite)
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onReset) { Text(if(isArabic) "إعادة ضبط" else "Reset", color = GlassAccentLight) }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = GlassAccent)) { Text(if(isArabic) "إغلاق" else "Close") } }
    )
}

@Composable
fun WirdGlassDialog(isArabic: Boolean, pages: Int, onIncrease: () -> Unit, onDecrease: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF1B183E),
        title = { Text(if(isArabic) "ورد القرآن الكريم" else "Quran Wird", color = GlassWhite) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecrease) { Text("-", color = GlassWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    Text("$pages", fontSize = 36.sp, fontWeight = FontWeight.Black, color = GlassAccent)
                    IconButton(onClick = onIncrease) { Text("+", color = GlassWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = GlassAccent)) { Text(if(isArabic) "حفظ" else "Save") } }
    )
}
