package com.moalmaz.ibkar

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
// 1. Data & Database Arrays (مدمجة لضمان عدم نقص أي بيانات)
// ==========================================
data class StepDhikr(val text: String, val count: Int, val benefit: String = "", val translation: String = "")
val morningAdhkarList = listOf(
    StepDhikr("أَعُوذُ بِاللهِ مِنْ الشَّيْطَانِ الرَّجِيمِ\nاللّهُ لاَ إِلَـهَ إِلاَّ هُوَ الْحَيُّ الْقَيُّومُ...", 1, "أجير من الجن حتى يمسي"),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ هُوَ ٱللَّهُ أَحَدٌ، ٱللَّهُ ٱلصَّمَدُ...", 3, "تكفيه من كل شيء"),
    StepDhikr("أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لا إِلَهَ إِلا اللَّهُ...", 1, "سؤال خير اليوم"),
    StepDhikr("اللّهُـمَّ أَنْتَ رَبِّـي لا إِلهَ إِلاّ أَنْتَ، خَلَقْتَنـي وَأَنا عَبْـدُك...", 1, "سيد الاستغفار")
)
val eveningAdhkarList = listOf(
    StepDhikr("أَعُوذُ بِاللهِ مِنْ الشَّيْطَانِ الرَّجِيمِ\nاللّهُ لاَ إِلَـهَ إِلاَّ هُوَ الْحَيُّ الْقَيُّومُ...", 1, "أجير من الجن حتى يصبح"),
    StepDhikr("أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لا إِلَهَ إِلا اللَّهُ...", 1, "سؤال خير الليلة"),
    StepDhikr("اللّهُـمَّ أَنْتَ رَبِّـي لا إِلهَ إِلاّ أَنْتَ، خَلَقْتَنـي وَأَنا عَبْـدُك...", 1, "سيد الاستغفار")
)
val hisnAlMuslimData = mapOf(
    "sleep" to listOf(StepDhikr("بِاسْمِكَ رَبِّـي وَضَعْـتُ جَنْـبي...", 1, "الحفظ أثناء النوم")),
    "wakeup" to listOf(StepDhikr("الحَمْـدُ لِلّهِ الّذي أَحْـيانا بَعْـدَ ما أَماتَـنا...", 1, "شكر الله")),
    "food" to listOf(StepDhikr("بِسْمِ اللَّهِ.", 1, "البركة")),
    "travel" to listOf(StepDhikr("سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا...", 1, "دعاء الركوب")),
    "home" to listOf(StepDhikr("بِسْمِ اللَّهِ، تَوَكَّلْتُ عَلَى اللَّهِ...", 1, "التوكل")),
    "mosque" to listOf(StepDhikr("اللَّهُمَّ افْتَحْ لِي أَبْوَابَ رَحْمَتِكَ.", 1, "طلب الرحمة"))
)

// ==========================================
// 2. Colors & Design System
// ==========================================
val AppBackground = Color(0xFF13152C)
val CardBackground = Color(0xFF232350)
val CardStroke = Color(0xFF353569)
val PrimaryAccent = Color(0xFF6C63FF)
val TextLightPurple = Color(0xFFA5A3D8)
val TextWhite = Color.White
val SuccessGreen = Color(0xFF10B981)

class MainActivity : ComponentActivity() {
    private var initialDhikrTypeState = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PrayerNotificationManager.createNotificationChannel(this)
        handleIntent(intent)
        setContent {
            val themePrefs = remember { getSharedPreferences("theme_prefs", Context.MODE_PRIVATE) }
            val isArabic by remember { mutableStateOf(themePrefs.getBoolean("is_arabic", true)) }

            MyApplicationTheme(darkTheme = true) {
                CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
                        MainAppNavigation(
                            isArabic = isArabic,
                            onToggleLanguage = { val nv = !isArabic; themePrefs.edit().putBoolean("is_arabic", nv).apply() },
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
    var activeDhikrType by remember { mutableStateOf<String?>("morning") }
    var historyData by remember { mutableStateOf<List<DailyRecord>>(emptyList()) }
    
    val recordState = viewModel.currentRecord.collectAsStateWithLifecycle()
    val record = recordState.value
    val streak = viewModel.currentStreak.collectAsStateWithLifecycle().value

    var showTasbeehDialog by remember { mutableStateOf(false) }
    var showWirdDialog by remember { mutableStateOf(false) }
    var showAdhkarDialog by remember { mutableStateOf(false) }
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
                            date = cursor.getString(cursor.getColumnIndexOrThrow("date")),
                            fajrDone = cursor.getInt(cursor.getColumnIndexOrThrow("fajrDone")) == 1,
                            dhuhrDone = cursor.getInt(cursor.getColumnIndexOrThrow("dhuhrDone")) == 1,
                            asrDone = cursor.getInt(cursor.getColumnIndexOrThrow("asrDone")) == 1,
                            maghribDone = cursor.getInt(cursor.getColumnIndexOrThrow("maghribDone")) == 1,
                            ishaDone = cursor.getInt(cursor.getColumnIndexOrThrow("ishaDone")) == 1,
                            quranPages = cursor.getInt(cursor.getColumnIndexOrThrow("quranPages")),
                            morningDhikrDone = cursor.getInt(cursor.getColumnIndexOrThrow("morningDhikrDone")) == 1,
                            eveningDhikrDone = cursor.getInt(cursor.getColumnIndexOrThrow("eveningDhikrDone")) == 1
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
    
    val todayTimesRaw = remember {
        val cal = PrayerTimeCalculator.getLocalCalendar(31.2001, 29.9187)
        PrayerTimeCalculator.calculatePrayerTimes(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), 31.2001, 29.9187, 5)
    }

    Scaffold(
        bottomBar = {
            if (currentRoute == AppRoute.Home || currentRoute == AppRoute.Stats) {
                NavigationBar(containerColor = AppBackground, contentColor = TextWhite, tonalElevation = 0.dp) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, null, modifier = Modifier.size(24.dp)) },
                        label = { Text(if(isArabic) "الرئيسية" else "Home", fontWeight = FontWeight.Bold) },
                        selected = currentRoute == AppRoute.Home,
                        onClick = { currentRoute = AppRoute.Home },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = PrimaryAccent, selectedTextColor = PrimaryAccent, unselectedIconColor = TextLightPurple, unselectedTextColor = TextLightPurple, indicatorColor = Color.Transparent)
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.List, null, modifier = Modifier.size(24.dp)) },
                        label = { Text(if(isArabic) "السجل" else "Stats", fontWeight = FontWeight.Bold) },
                        selected = currentRoute == AppRoute.Stats,
                        onClick = { currentRoute = AppRoute.Stats },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = PrimaryAccent, selectedTextColor = PrimaryAccent, unselectedIconColor = TextLightPurple, unselectedTextColor = TextLightPurple, indicatorColor = Color.Transparent)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(AppBackground).padding(paddingValues)) {
            when (currentRoute) {
                AppRoute.Home -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopHeaderSection(isArabic, onSettingsClick = { showSettingsDialog = true })
                        HomeScreenContent(
                            isArabic = isArabic, record = activeRecord, dailyPoints = dailyPoints, totalDoneItems = totalDoneItems,
                            todayTimes = todayTimesRaw,
                            onTogglePrayer = { viewModel.togglePrayer(it) },
                            onOpenDhikrMenu = { showAdhkarDialog = true },
                            onOpenWird = { showWirdDialog = true },
                            onOpenTasbeeh = { showTasbeehDialog = true },
                            onOpenHisn = { currentRoute = AppRoute.FullScreenHisn }
                        )
                    }
                }
                AppRoute.Stats -> AdvancedStatsScreen(isArabic, historyData)
                AppRoute.FullScreenHisn -> FullScreenHisn(isArabic, onBack = { currentRoute = AppRoute.Home })
                AppRoute.FullScreenDhikr -> {
                    activeDhikrType?.let { type ->
                        FullScreenDhikrReading(
                            type = type, isArabic = isArabic, onDismiss = { currentRoute = AppRoute.Home },
                            onComplete = {
                                if (type == "morning" && !activeRecord.morningDhikrDone) viewModel.toggleMorningDhikr()
                                if (type == "evening" && !activeRecord.eveningDhikrDone) viewModel.toggleEveningDhikr()
                                currentRoute = AppRoute.Home
                            }
                        )
                    }
                }
            }

            if (showTasbeehDialog) TasbeehGlassDialog(isArabic, activeRecord.dhikrCount, { viewModel.incrementDhikr() }, { viewModel.resetDhikr() }) { showTasbeehDialog = false }
            if (showWirdDialog) WirdGlassDialog(isArabic, activeRecord.quranPages, { viewModel.setQuranPages(activeRecord.quranPages + 1) }, { viewModel.setQuranPages(activeRecord.quranPages - 1) }) { showWirdDialog = false }
            if (showAdhkarDialog) {
                AdhkarSelectionDialog(
                    isArabic = isArabic, record = activeRecord,
                    onToggleMorning = { viewModel.toggleMorningDhikr() },
                    onToggleEvening = { viewModel.toggleEveningDhikr() },
                    onReadMorning = { activeDhikrType = "morning"; currentRoute = AppRoute.FullScreenDhikr; showAdhkarDialog = false },
                    onReadEvening = { activeDhikrType = "evening"; currentRoute = AppRoute.FullScreenDhikr; showAdhkarDialog = false },
                    onDismiss = { showAdhkarDialog = false }
                )
            }
            if (showSettingsDialog) SettingsDialog(isArabic, onToggleLanguage) { showSettingsDialog = false }
        }
    }
}

// ==========================================
// 4. الأيقونات المرسومة لضمان الأناقة
// ==========================================
@Composable
fun CustomBadgeIcon() {
    Canvas(modifier = Modifier.size(38.dp)) {
        val w = size.width; val h = size.height
        drawCircle(color = TextLightPurple, radius = w * 0.30f, center = Offset(w/2, h*0.35f), style = Stroke(width = 4f))
        drawCircle(color = TextLightPurple, radius = w * 0.12f, center = Offset(w/2, h*0.35f), style = Stroke(width = 4f))
        drawLine(color = TextLightPurple, start = Offset(w*0.35f, h*0.6f), end = Offset(w*0.25f, h*0.9f), strokeWidth = 4f)
        drawLine(color = TextLightPurple, start = Offset(w*0.65f, h*0.6f), end = Offset(w*0.75f, h*0.9f), strokeWidth = 4f)
    }
}

@Composable
fun CustomBookIcon() {
    Canvas(modifier = Modifier.size(28.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(color = TextLightPurple, topLeft = Offset(w*0.05f, h*0.15f), size = Size(w*0.4f, h*0.7f), style = Stroke(3.5f))
        drawRoundRect(color = TextLightPurple, topLeft = Offset(w*0.55f, h*0.15f), size = Size(w*0.4f, h*0.7f), style = Stroke(3.5f))
        drawLine(color = TextLightPurple, start = Offset(w/2, h*0.15f), end = Offset(w/2, h*0.85f), strokeWidth = 3.5f)
    }
}

@Composable
fun CustomTouchIcon() {
    Canvas(modifier = Modifier.size(28.dp)) {
        drawCircle(color = TextLightPurple, radius = size.width*0.25f, center = Offset(size.width/2, size.height*0.35f), style = Stroke(3.5f))
        drawLine(color = TextLightPurple, start = Offset(size.width/2, size.height*0.6f), end = Offset(size.width/2, size.height*0.9f), strokeWidth = 3.5f)
        drawCircle(color = TextLightPurple, radius = 3.5f, center = Offset(size.width/2, size.height*0.35f))
    }
}

@Composable
fun CustomMoonIcon() {
    Canvas(modifier = Modifier.size(26.dp)) {
        val path = Path().apply { addArc(Rect(0f, 0f, size.width, size.height), -90f, 220f) }
        drawPath(path, color = TextLightPurple, style = Stroke(3.5f))
        drawCircle(color = TextLightPurple, radius = size.width/6f, center = Offset(size.width*0.4f, size.height*0.4f))
    }
}

// ==========================================
// 5. واجهة الشاشة الرئيسية (تطابق الصورة تماماً)
// ==========================================
@Composable
fun TopHeaderSection(isArabic: Boolean, onSettingsClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(text = if(isArabic) "إِبْكَـار" else "Ibkar", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = TextWhite, fontSize = 28.sp), modifier = Modifier.align(Alignment.Center))
            IconButton(onClick = onSettingsClick, modifier = Modifier.align(Alignment.CenterEnd)) { Icon(Icons.Filled.Settings, null, tint = TextLightPurple) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            Icon(Icons.Filled.LocationOn, null, tint = TextLightPurple, modifier = Modifier.size(14.dp))
            Text(text = if(isArabic) "الإسكندرية، مصر" else "Alexandria, Egypt", style = MaterialTheme.typography.labelMedium.copy(color = TextLightPurple, fontSize = 13.sp))
        }
    }
}

@Composable
fun HomeScreenContent(
    isArabic: Boolean, record: DailyRecord, dailyPoints: Int, totalDoneItems: Int, todayTimes: Map<String, Pair<Int, Int>>, 
    onTogglePrayer: (String) -> Unit, onOpenDhikrMenu: () -> Unit, onOpenWird: () -> Unit, onOpenTasbeeh: () -> Unit, onOpenHisn: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
        
        // بطاقة إجمالي النقاط
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).clip(RoundedCornerShape(20.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(20.dp)).padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    CustomBadgeIcon()
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = if(isArabic) "إجمالي النقاط" else "Total Points", style = MaterialTheme.typography.labelMedium.copy(color = TextLightPurple, fontSize = 14.sp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(text = "$dailyPoints", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, color = PrimaryAccent, fontSize = 38.sp))
                                Text(text = "/100", style = MaterialTheme.typography.bodyLarge.copy(color = TextLightPurple, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 6.dp))
                            }
                        }
                    }
                }
                val dayProgress = totalDoneItems.toFloat() / 8f
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(6.dp).clip(RoundedCornerShape(10.dp)).background(CardStroke)) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Box(modifier = Modifier.fillMaxWidth(dayProgress).height(6.dp).clip(RoundedCornerShape(10.dp)).background(PrimaryAccent))
                    }
                }
            }
        }

        // الأزرار الأربعة (الورد، التسبيح، الأذكار، حصن المسلم)
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(20.dp)).clickable { onOpenWird() }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CustomBookIcon()
                        Text(if(isArabic) "الورد" else "Wird", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp))
                    }
                }
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(20.dp)).clickable { onOpenTasbeeh() }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CustomTouchIcon()
                        Text(if(isArabic) "التسبيح" else "Tasbeeh", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp))
                    }
                }
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(20.dp)).clickable { onOpenDhikrMenu() }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CustomMoonIcon()
                        Text(if(isArabic) "الأذكار" else "Adhkar", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp))
                    }
                }
            }
        }
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).height(65.dp).clip(RoundedCornerShape(20.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(20.dp)).clickable { onOpenHisn() }, contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Lock, null, tint = TextLightPurple)
                    Text(if(isArabic) "حصن المسلم" else "Hisn Al-Muslim", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 16.sp))
                }
            }
        }

        // الصلوات المفروضة
        item {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(text = if(isArabic) "الصلوات المفروضة" else "Obligatory Prayers", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                fun t(k: String): String { val d = todayTimes[k] ?: return ""; val h = if(d.first%12==0) 12 else d.first%12; return "%02d:%02d".format(h, d.second) }
                
                PrayerItemRow(if(isArabic) "الفجر" else "Fajr", record.fajrDone, t("fajr")) { onTogglePrayer("fajr") }
                PrayerItemRow(if(isArabic) "الظهر" else "Dhuhr", record.dhuhrDone, t("dhuhr")) { onTogglePrayer("dhuhr") }
                PrayerItemRow(if(isArabic) "العصر" else "Asr", record.asrDone, t("asr")) { onTogglePrayer("asr") }
                PrayerItemRow(if(isArabic) "المغرب" else "Maghrib", record.maghribDone, t("maghrib")) { onTogglePrayer("maghrib") }
                PrayerItemRow(if(isArabic) "العشاء" else "Isha", record.ishaDone, t("isha")) { onTogglePrayer("isha") }
            }
        }
    }
}

// تصميم الصلاة (الاسم يمين، الوقت منتصف، الدائرة يسار)
@Composable
fun PrayerItemRow(name: String, isDone: Boolean, timeText: String, onToggle: () -> Unit) {
    val bgColor = if (isDone) PrimaryAccent.copy(alpha = 0.2f) else CardBackground
    val strokeColor = if (isDone) PrimaryAccent else CardStroke

    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bgColor).border(1.dp, strokeColor, RoundedCornerShape(16.dp)).clickable { onToggle() }.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isDone) Icon(Icons.Filled.CheckCircle, null, tint = PrimaryAccent, modifier = Modifier.size(24.dp))
        else Canvas(modifier = Modifier.size(22.dp)) { drawCircle(color = TextWhite.copy(alpha = 0.15f), style = Stroke(width = 4f)) }
        Spacer(Modifier.width(20.dp))
        Text(text = name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite))
        Spacer(Modifier.weight(1f))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(text = timeText, style = MaterialTheme.typography.bodyMedium.copy(color = TextLightPurple, fontWeight = FontWeight.Bold))
        }
    }
}

// ==========================================
// 6. النوافذ والشاشات الكاملة (عادت للعمل بالكامل)
// ==========================================
@Composable
fun AdhkarSelectionDialog(isArabic: Boolean, record: DailyRecord, onToggleMorning: () -> Unit, onToggleEvening: () -> Unit, onReadMorning: () -> Unit, onReadEvening: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBackground,
        title = { Text(if(isArabic) "الأذكار اليومية" else "Daily Adhkar", color = TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppBackground).border(1.dp, CardStroke, RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleMorning, modifier = Modifier.size(32.dp)) {
                        if (record.morningDhikrDone) Icon(Icons.Filled.CheckCircle, null, tint = PrimaryAccent) else Canvas(modifier = Modifier.size(22.dp)) { drawCircle(color = TextWhite.copy(alpha=0.2f), style = Stroke(width=4f)) }
                    }
                    Text(if(isArabic) "أذكار الصباح" else "Morning", color = TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable { onReadMorning() }.padding(horizontal = 16.dp), textAlign = TextAlign.Start)
                }
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppBackground).border(1.dp, CardStroke, RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleEvening, modifier = Modifier.size(32.dp)) {
                        if (record.eveningDhikrDone) Icon(Icons.Filled.CheckCircle, null, tint = PrimaryAccent) else Canvas(modifier = Modifier.size(22.dp)) { drawCircle(color = TextWhite.copy(alpha=0.2f), style = Stroke(width=4f)) }
                    }
                    Text(if(isArabic) "أذكار المساء" else "Evening", color = TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable { onReadEvening() }.padding(horizontal = 16.dp), textAlign = TextAlign.Start)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)) { Text(if(isArabic) "إغلاق" else "Close", color = TextWhite) } }
    )
}

@Composable
fun TasbeehGlassDialog(isArabic: Boolean, count: Int, onIncrement: () -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBackground,
        title = { Text(if(isArabic) "المسبحة الإلكترونية" else "Digital Tasbeeh", color = TextWhite, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(140.dp).clip(CircleShape).background(PrimaryAccent.copy(0.1f)).border(2.dp, PrimaryAccent, CircleShape).clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onIncrement() }, contentAlignment = Alignment.Center) {
                    Text("$count", fontSize = 48.sp, fontWeight = FontWeight.Black, color = TextWhite)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onReset() }) { Text(if(isArabic) "إعادة ضبط" else "Reset", color = TextLightPurple) }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)) { Text(if(isArabic) "إغلاق" else "Close", color=TextWhite) } }
    )
}

@Composable
fun WirdGlassDialog(isArabic: Boolean, pages: Int, onIncrease: () -> Unit, onDecrease: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBackground,
        title = { Text(if(isArabic) "ورد القرآن الكريم" else "Quran Wird", color = TextWhite, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(if(isArabic) "عدد الصفحات المقروءة اليوم" else "Pages read today", color = TextLightPurple, modifier = Modifier.padding(bottom = 16.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDecrease, modifier = Modifier.size(48.dp).background(AppBackground, CircleShape)) { Text("-", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                        Text("$pages", fontSize = 42.sp, fontWeight = FontWeight.Black, color = PrimaryAccent)
                        IconButton(onClick = onIncrease, modifier = Modifier.size(48.dp).background(PrimaryAccent, CircleShape)) { Text("+", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)) { Text(if(isArabic) "حفظ" else "Save", color=TextWhite) } }
    )
}

@Composable
fun SettingsDialog(isArabic: Boolean, onToggleLanguage: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBackground,
        title = { Text(if (isArabic) "الإعدادات" else "Settings", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if(isArabic) "Change to English" else "تغيير للغة العربية", color = TextWhite)
                    Switch(checked = !isArabic, onCheckedChange = { onToggleLanguage() }, colors = SwitchDefaults.colors(checkedThumbColor = PrimaryAccent, checkedTrackColor = PrimaryAccent.copy(alpha=0.5f)))
                }
                HorizontalDivider(color = CardStroke)
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isArabic) "إِبْكَـار - صُنع بكل حب بواسطة مصطفى الماظ" else "Ibkar - Made with love by Mostafa Almaz", style = MaterialTheme.typography.labelSmall.copy(color = TextLightPurple, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ibkar.vercel.app"))) } catch (e: Exception) {} }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent.copy(alpha = 0.2f))) { Text(if (isArabic) "الموقع" else "Web", color = PrimaryAccent) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)) { Text(if (isArabic) "إغلاق" else "Close", color = TextWhite) } }
    )
}

@Composable
fun AdvancedStatsScreen(isArabic: Boolean, history: List<DailyRecord>) {
    val totalScore = history.sumOf { it.calculatePoints() }
    val perfectDays = history.count { it.calculatePoints() == 100 }
    val historyItems = history.reversed()

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp, start = 24.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text(if(isArabic) "سجل الإنجازات" else "Achievement Log", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = TextWhite)) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(24.dp)).padding(16.dp)) {
                    Column {
                        Icon(Icons.Filled.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(32.dp).padding(bottom = 8.dp))
                        Text(if(isArabic) "النقاط الإجمالية" else "Total Points", style = MaterialTheme.typography.labelMedium.copy(color = TextLightPurple))
                        Text("$totalScore", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = TextWhite))
                    }
                }
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(24.dp)).padding(16.dp)) {
                    Column {
                        Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(32.dp).padding(bottom = 8.dp))
                        Text(if(isArabic) "أيام كاملة" else "Perfect Days", style = MaterialTheme.typography.labelMedium.copy(color = TextLightPurple))
                        Text("$perfectDays", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = TextWhite))
                    }
                }
            }
        }
        item { Text(text = if(isArabic) "أداء الأيام السابقة" else "Previous Days", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite), modifier = Modifier.padding(top = 10.dp)) }

        if (historyItems.isEmpty()) {
            item { Text(if(isArabic) "لا توجد بيانات بعد." else "No records yet.", color = TextLightPurple, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        } else {
            items(historyItems) { day ->
                val missed = mutableListOf<String>()
                if (!day.fajrDone) missed.add(if(isArabic) "الفجر" else "Fajr")
                if (!day.dhuhrDone) missed.add(if(isArabic) "الظهر" else "Dhuhr")
                if (!day.asrDone) missed.add(if(isArabic) "العصر" else "Asr")
                if (!day.maghribDone) missed.add(if(isArabic) "المغرب" else "Maghrib")
                if (!day.ishaDone) missed.add(if(isArabic) "العشاء" else "Isha")
                if (day.quranPages == 0) missed.add(if(isArabic) "القرآن" else "Quran")
                if (!day.morningDhikrDone) missed.add(if(isArabic) "الصباح" else "Morning")
                if (!day.eveningDhikrDone) missed.add(if(isArabic) "المساء" else "Evening")
                
                val pts = day.calculatePoints()
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(if (pts == 100) SuccessGreen.copy(alpha=0.2f) else PrimaryAccent.copy(alpha=0.2f)).border(2.dp, if (pts == 100) SuccessGreen else PrimaryAccent, CircleShape), contentAlignment = Alignment.Center) {
                        Text("$pts", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextWhite))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(text = day.date, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = TextWhite), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                        }
                        if (missed.isEmpty()) { Text(text = if(isArabic) "علامة كاملة، أحسنت!" else "Perfect score!", style = MaterialTheme.typography.labelSmall.copy(color = SuccessGreen, fontWeight = FontWeight.Bold)) } 
                        else { Text(text = (if(isArabic) "فاتك: " else "Missed: ") + missed.joinToString("، "), style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF87171))) }
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenHisn(isArabic: Boolean, onBack: () -> Unit) {
    var activeCategory by remember { mutableStateOf<String?>(null) }
    if (activeCategory == null) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowRight, null, tint = TextWhite) }
                Text(text = if(isArabic) "حصن المسلم" else "Hisn Al-Muslim", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = TextWhite), modifier = Modifier.padding(start = 16.dp))
            }
            val cats = listOf("sleep" to (if(isArabic) "أذكار النوم" else "Sleep"), "wakeup" to (if(isArabic) "الاستيقاظ" else "Wake up"), "food" to (if(isArabic) "الطعام" else "Food"), "travel" to (if(isArabic) "السفر" else "Travel"), "home" to (if(isArabic) "المنزل" else "Home"), "mosque" to (if(isArabic) "المسجد" else "Mosque"))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(cats) { cat ->
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(16.dp)).clickable { activeCategory = cat.first }.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = cat.second, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite))
                        Icon(Icons.Filled.KeyboardArrowLeft, null, tint = TextLightPurple)
                    }
                }
            }
        }
    } else {
        val list = hisnAlMuslimData[activeCategory] ?: emptyList()
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { activeCategory = null }) { Icon(Icons.Filled.KeyboardArrowRight, null, tint = TextWhite) }
                Text(text = if(isArabic) "الأذكار" else "Supplications", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = TextWhite), modifier = Modifier.padding(start = 16.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(list) { item ->
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(24.dp)).padding(20.dp)) {
                        Column {
                            Text(text = item.text, style = MaterialTheme.typography.titleMedium.copy(lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = TextWhite), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            if (item.benefit.isNotEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(12.dp)).background(AppBackground).padding(10.dp), contentAlignment = Alignment.Center) {
                                    Text(text = (if(isArabic) "الفضل: " else "Benefit: ") + item.benefit, style = MaterialTheme.typography.labelSmall.copy(color = TextLightPurple))
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
fun FullScreenDhikrReading(type: String, isArabic: Boolean, onComplete: () -> Unit, onDismiss: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val list = if (type == "morning") morningAdhkarList else eveningAdhkarList
    var currentIndex by remember { mutableStateOf(0) }
    val currentCountsLeft = remember { mutableStateListOf<Int>().apply { addAll(list.map { it.count }) } }
    val currentDhikr = list.getOrNull(currentIndex)
    val curCountLeft = currentCountsLeft.getOrNull(currentIndex) ?: 0
    var isFinished by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null, tint = TextWhite) }
            Text(text = if (type == "morning") (if(isArabic) "أذكار الصباح" else "Morning") else (if(isArabic) "أذكار المساء" else "Evening"), style = MaterialTheme.typography.titleLarge.copy(color = PrimaryAccent, fontWeight = FontWeight.Bold))
            IconButton(onClick = { currentIndex = 0; isFinished = false; currentCountsLeft.clear(); currentCountsLeft.addAll(list.map { it.count }) }) { Icon(Icons.Filled.Refresh, null, tint = TextWhite) }
        }

        if (!isFinished && currentDhikr != null) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(text = "${currentIndex + 1} / ${list.size}", style = MaterialTheme.typography.labelSmall.copy(color = TextLightPurple), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in list.indices) {
                        val c = when { i < currentIndex -> SuccessGreen; i == currentIndex -> PrimaryAccent; else -> CardStroke }
                        Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c))
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp).clip(RoundedCornerShape(24.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(24.dp)).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = currentDhikr.text, style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = TextWhite), textAlign = TextAlign.Center)
                    if (currentDhikr.benefit.isNotEmpty()) { Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(AppBackground).padding(horizontal = 16.dp, vertical = 10.dp)) { Text(text = (if(isArabic) "الفضل: " else "Benefit: ") + currentDhikr.benefit, style = MaterialTheme.typography.bodySmall.copy(color = TextLightPurple), textAlign = TextAlign.Center) } }
                }
            }
            Box(modifier = Modifier.size(115.dp).clip(CircleShape).background(PrimaryAccent.copy(alpha = 0.2f)).border(2.dp, PrimaryAccent, CircleShape).clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (curCountLeft > 1) { currentCountsLeft[currentIndex] = curCountLeft - 1 } else { currentCountsLeft[currentIndex] = 0; if (currentIndex < list.size - 1) currentIndex++ else isFinished = true }
            }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$curCountLeft", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = TextWhite)
                    Text(if(isArabic) "متبقي" else "Left", fontSize = 11.sp, color = TextWhite.copy(alpha = 0.8f))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { if (currentIndex > 0) { currentIndex--; currentCountsLeft[currentIndex] = list[currentIndex].count } }, enabled = currentIndex > 0) { Text(if(isArabic) "السابق" else "Previous", color = if (currentIndex > 0) PrimaryAccent else Color.Gray, fontWeight = FontWeight.Bold) }
                TextButton(onClick = { currentCountsLeft[currentIndex] = 0; if (currentIndex < list.size - 1) currentIndex++ else isFinished = true }) { Text(if(isArabic) "تخطي" else "Skip", color = PrimaryAccent, fontWeight = FontWeight.Bold) }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                CelebrationEffect()
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().align(Alignment.Center)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(80.dp))
                    Text(text = if(isArabic) "تقبل الله طاعتك!" else "Accepted!", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, color = SuccessGreen))
                    Button(onClick = onComplete, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if(isArabic) "إتمام" else "Done", color = Color.White) }
                }
            }
        }
    }
}

@Composable
fun CelebrationEffect() {
    val particles = remember { List(60) { val isB = Random.nextFloat() > 0.7f; Particle(Random.nextFloat() * 1000f, if (isB) 2500f + Random.nextFloat() * 500f else -100f - Random.nextFloat() * 500f, if (isB) -(3f + Random.nextFloat() * 4f) else (5f + Random.nextFloat() * 6f), (Random.nextFloat() - 0.5f) * 4f, listOf(Color(0xFF818CF8), Color(0xFF34D399), Color(0xFFFBBF24)).random(), isB, if (isB) 40f + Random.nextFloat() * 20f else 10f + Random.nextFloat() * 10f) } }
    var trigger by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { withFrameNanos { trigger += 1f }; particles.forEach { p -> p.y += p.speedY; p.x += p.speedX; if (!p.isBalloon && p.y > 3000f) p.y = -100f; if (p.isBalloon && p.y < -500f) p.y = 2500f } } }
    Canvas(modifier = Modifier.fillMaxSize()) { trigger.let { _ -> particles.forEach { p -> if (p.isBalloon) drawCircle(p.color.copy(alpha=0.8f), p.size, Offset(p.x, p.y)) else drawRect(p.color, Offset(p.x, p.y), Size(p.size, p.size)) } } }
}

data class Particle(var x: Float, var y: Float, var speedY: Float, var speedX: Float, val color: Color, val isBalloon: Boolean, val size: Float)
