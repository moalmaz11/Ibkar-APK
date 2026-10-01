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
// 1. Data & Helpers (كاملة بدون نقص)
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

data class StepDhikr(val text: String, val count: Int, val benefit: String = "", val translation: String = "")

val morningAdhkarList = listOf(
    StepDhikr("أَعُوذُ بِاللهِ مِنْ الشَّيْطَانِ الرَّجِيمِ\nاللّهُ لاَ إِلَـهَ إِلاَّ هُوَ الْحَيُّ الْقَيُّومُ...", 1, "أجير من الجن حتى يمسي"),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ هُوَ ٱللَّهُ أَحَدٌ، ٱللَّهُ ٱلصَّمَدُ، لَمْ يَلِدْ وَلَمْ يُولَدْ، وَلَمْ يَكُن لَّهُۥ كُفُوًا أَحَدٌ.", 3, "تكفيه من كل شيء"),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ أَعُوذُ بِرَبِّ ٱلْفَلَقِ، مِن شَرِّ مَا خَلَقَ...", 3, "تكفيه من كل شيء"),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ أَعُوذُ بِرَبِّ ٱلنَّاسِ، مَلِكِ ٱلنَّاسِ...", 3, "تكفيه من كل شيء"),
    StepDhikr("أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لا إِلَهَ إِلا اللَّهُ وَحْدَهُ لا شَرِيكَ لَهُ...", 1, "سؤال خير اليوم"),
    StepDhikr("اللّهُـمَّ أَنْتَ رَبِّـي لا إِلهَ إِلاّ أَنْتَ، خَلَقْتَنـي وَأَنا عَبْـدُك...", 1, "سيد الاستغفار"),
    StepDhikr("رَضِيتُ بِاللَّهِ رَبّاً، وَبِالْإِسْلَامِ دِيناً، وَبِمُحَمَّدٍ صلى الله عليه وسلم نَبِيّاً.", 3, "كان حقاً على الله أن يرضيه"),
    StepDhikr("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ.", 3, "لم يضره شيء"),
    StepDhikr("حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ.", 7, "كفاه الله ما أهمه"),
    StepDhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.", 100, "حُطّت خطاياه وإن كانت مثل زبد البحر")
)

val eveningAdhkarList = listOf(
    StepDhikr("أَعُوذُ بِاللهِ مِنْ الشَّيْطَانِ الرَّجِيمِ\nاللّهُ لاَ إِلَـهَ إِلاَّ هُوَ الْحَيُّ الْقَيُّومُ...", 1, "أجير من الجن حتى يصبح"),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ هُوَ ٱللَّهُ أَحَدٌ، ٱللَّهُ ٱلصَّمَدُ...", 3, "تكفيه من كل شيء"),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ أَعُوذُ بِرَبِّ ٱلْفَلَقِ...", 3, "تكفيه من كل شيء"),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ أَعُوذُ بِرَبِّ ٱلنَّاسِ...", 3, "تكفيه من كل شيء"),
    StepDhikr("أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لا إِلَهَ إِلا اللَّهُ وَحْدَهُ لا شَرِيكَ لَهُ...", 1, "سؤال خير الليلة"),
    StepDhikr("اللّهُـمَّ أَنْتَ رَبِّـي لا إِلهَ إِلاّ أَنْتَ، خَلَقْتَنـي وَأَنا عَبْـدُك...", 1, "سيد الاستغفار"),
    StepDhikr("أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.", 3, "لم يضره شيء في تلك الليلة"),
    StepDhikr("رَضِيتُ بِاللَّهِ رَبّاً، وَبِالْإِسْلَامِ دِيناً، وَبِمُحَمَّدٍ صلى الله عليه وسلم نَبِيّاً.", 3, "كان حقاً على الله أن يرضيه"),
    StepDhikr("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ.", 3, "لم يضره شيء"),
    StepDhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.", 100, "حُطّت خطاياه وإن كانت مثل زبد البحر")
)

val hisnAlMuslimData = mapOf(
    "sleep" to listOf(StepDhikr("بِاسْمِكَ رَبِّـي وَضَعْـتُ جَنْـبي، وَبِكَ أَرْفَعُـه...", 1, "الحفظ أثناء النوم"), StepDhikr("اللَّهُمَّ إِنَّكَ خَلَقْتَ نَفْسِي وَأَنْتَ تَوَفَّاهَا...", 1, "تسليم الروح لله")),
    "wakeup" to listOf(StepDhikr("الحَمْـدُ لِلّهِ الّذي أَحْـيانا بَعْـدَ ما أَماتَـنا وَإليه النُّـشور.", 1, "شكر الله"), StepDhikr("لا إلهَ إلاّ اللّهُ وَحْـدَهُ لا شَـريكَ له...", 1, "توحيد خالص")),
    "food" to listOf(StepDhikr("بِسْمِ اللَّهِ.", 1, "عند البدء"), StepDhikr("الْحَمْدُ لِلَّهِ الَّذِي أَطْعَمَنِي هَذَا وَرَزَقَنِيهِ مِنْ غَيْرِ حَوْلٍ مِنِّي وَلَا قُوَّةٍ.", 1, "عند الانتهاء")),
    "travel" to listOf(StepDhikr("سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ...", 1, "دعاء الركوب"), StepDhikr("اللَّهُمَّ إِنَّا نَسْأَلُكَ فِي سَفَرِنَا هَذَا الْبِرَّ وَالتَّقْوَى...", 1, "دعاء السفر")),
    "home" to listOf(StepDhikr("بِسْـمِ اللهِ وَلَجْنـا، وَبِسْـمِ اللهِ خَـرَجْنـا...", 1, "عند الدخول"), StepDhikr("بِسْمِ اللَّهِ، تَوَكَّلْتُ عَلَى اللَّهِ، وَلَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ.", 1, "عند الخروج")),
    "mosque" to listOf(StepDhikr("اللَّهُمَّ افْتَحْ لِي أَبْوَابَ رَحْمَتِكَ.", 1, "عند الدخول"), StepDhikr("اللَّهُمَّ إِنِّي أَسْأَلُكَ مِنْ فَضْلِكَ.", 1, "عند الخروج")),
    "toilet" to listOf(StepDhikr("بِسْمِ الله، اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْخُبْثِ وَالْخَبَائِثِ.", 1, "عند الدخول"), StepDhikr("غُفْرَانَكَ.", 1, "عند الخروج")),
    "rain" to listOf(StepDhikr("اللَّهُمَّ صَيِّباً نَافِعاً.", 1, "عند نزول المطر"), StepDhikr("مُطِرْنَا بِفَضْلِ اللَّهِ وَرَحْمَتِهِ.", 1, "بعد نزول المطر"))
)

// ==========================================
// 2. Colors & Design System
// ==========================================
val AppBackground = Color(0xFF0F1123)
val CardBackground = Color(0xFF222045)
val CardStroke = Color(0xFF33315C)
val PrimaryAccent = Color(0xFF6764FF)
val TextLightPurple = Color(0xFFA5A4DF)
val TextWhite = Color.White
val SuccessGreen = Color(0xFF10B981)
val GlassBgGradient = listOf(Color(0xFF0F1123), Color(0xFF1B183E), Color(0xFF130E2B))
val GlassPanelBg = Color.White.copy(alpha = 0.05f)
val GlassPanelBorder = Color.White.copy(alpha = 0.10f)

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
            val isArabic by remember { mutableStateOf(themePrefs.getBoolean("is_arabic", true)) }

            MyApplicationTheme(darkTheme = true) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
                        MainAppNavigation(
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
fun MainAppNavigation(viewModel: WorshipViewModel = viewModel(), initialDhikrType: String?, onInitialDhikrHandled: () -> Unit) {
    val context = LocalContext.current
    var currentRoute by remember { mutableStateOf(AppRoute.Home) }
    var activeDhikrType by remember { mutableStateOf<String?>(initialDhikrType) }
    var historyData by remember { mutableStateOf<List<DailyRecord>>(emptyList()) }
    
    val recordState = viewModel.currentRecord.collectAsStateWithLifecycle()
    val record = recordState.value

    var showTasbeehDialog by remember { mutableStateOf(false) }
    var showWirdDialog by remember { mutableStateOf(false) }
    var showAdhkarDialog by remember { mutableStateOf(false) }

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
        val cal = PrayerTimeCalculator.getLocalCalendar(31.2001, 29.9187) // الإسكندرية
        PrayerTimeCalculator.calculatePrayerTimes(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), 31.2001, 29.9187, 5)
    }
    var upcomingPrayerInfoState by remember(todayTimesRaw) { mutableStateOf<UpcomingPrayerInfo?>(null) }
    val isTodaySelected = activeRecord.date == DateHelper.getTodayDateString(context)

    LaunchedEffect(todayTimesRaw, isTodaySelected) {
        if (isTodaySelected) { while (true) { upcomingPrayerInfoState = getUpcomingPrayer(todayTimesRaw, 31.2001, 29.9187); delay(1000L) } }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute == AppRoute.Home || currentRoute == AppRoute.Stats) {
                NavigationBar(containerColor = AppBackground, contentColor = TextWhite, tonalElevation = 0.dp) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, null, modifier = Modifier.size(24.dp)) },
                        label = { Text("الرئيسية", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        selected = currentRoute == AppRoute.Home,
                        onClick = { currentRoute = AppRoute.Home },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = PrimaryAccent, selectedTextColor = PrimaryAccent, unselectedIconColor = TextLightPurple, unselectedTextColor = TextLightPurple, indicatorColor = Color.Transparent)
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.List, null, modifier = Modifier.size(24.dp)) },
                        label = { Text("السجل", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        selected = currentRoute == AppRoute.Stats,
                        onClick = { currentRoute = AppRoute.Stats },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = PrimaryAccent, selectedTextColor = PrimaryAccent, unselectedIconColor = TextLightPurple, unselectedTextColor = TextLightPurple, indicatorColor = Color.Transparent)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(GlassBgGradient)).padding(paddingValues)) {
            when (currentRoute) {
                AppRoute.Home -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopHeaderSection()
                        HomeScreenContent(
                            record = activeRecord, dailyPoints = dailyPoints, totalDoneItems = totalDoneItems,
                            todayTimes = todayTimesRaw,
                            onTogglePrayer = { viewModel.togglePrayer(it) },
                            onOpenDhikrMenu = { showAdhkarDialog = true },
                            onOpenWird = { showWirdDialog = true },
                            onOpenTasbeeh = { showTasbeehDialog = true },
                            onOpenHisn = { currentRoute = AppRoute.FullScreenHisn }
                        )
                    }
                }
                AppRoute.Stats -> AdvancedStatsScreen(historyData)
                AppRoute.FullScreenHisn -> FullScreenHisn(onBack = { currentRoute = AppRoute.Home })
                AppRoute.FullScreenDhikr -> {
                    activeDhikrType?.let { type ->
                        FullScreenDhikrReading(
                            type = type, onDismiss = { currentRoute = AppRoute.Home },
                            onComplete = {
                                if (type == "morning" && !activeRecord.morningDhikrDone) viewModel.toggleMorningDhikr()
                                if (type == "evening" && !activeRecord.eveningDhikrDone) viewModel.toggleEveningDhikr()
                                currentRoute = AppRoute.Home
                            }
                        )
                    }
                }
            }

            if (showTasbeehDialog) TasbeehGlassDialog(activeRecord.dhikrCount, { viewModel.incrementDhikr() }, { viewModel.resetDhikr() }) { showTasbeehDialog = false }
            if (showWirdDialog) WirdGlassDialog(activeRecord.quranPages, { viewModel.setQuranPages(activeRecord.quranPages + 1) }, { viewModel.setQuranPages(activeRecord.quranPages - 1) }) { showWirdDialog = false }
            if (showAdhkarDialog) {
                AdhkarSelectionDialog(
                    record = activeRecord,
                    onToggleMorning = { viewModel.toggleMorningDhikr() },
                    onToggleEvening = { viewModel.toggleEveningDhikr() },
                    onReadMorning = { activeDhikrType = "morning"; currentRoute = AppRoute.FullScreenDhikr; showAdhkarDialog = false },
                    onReadEvening = { activeDhikrType = "evening"; currentRoute = AppRoute.FullScreenDhikr; showAdhkarDialog = false },
                    onDismiss = { showAdhkarDialog = false }
                )
            }
        }
    }
}

// ==========================================
// Custom Icons (To Match Image Exactly)
// ==========================================
@Composable
fun CustomBadgeIcon() {
    Canvas(modifier = Modifier.size(34.dp)) {
        val w = size.width; val h = size.height
        drawCircle(color = TextLightPurple, radius = w * 0.35f, center = Offset(w/2, h*0.4f), style = Stroke(width = 4f))
        drawLine(color = TextLightPurple, start = Offset(w*0.3f, h*0.7f), end = Offset(w*0.2f, h), strokeWidth = 4f)
        drawLine(color = TextLightPurple, start = Offset(w*0.7f, h*0.7f), end = Offset(w*0.8f, h), strokeWidth = 4f)
    }
}

@Composable
fun CustomBookIcon() {
    Canvas(modifier = Modifier.size(28.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(color = TextLightPurple, topLeft = Offset(w*0.1f, h*0.2f), size = Size(w*0.35f, h*0.6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f), style = Stroke(3f))
        drawRoundRect(color = TextLightPurple, topLeft = Offset(w*0.55f, h*0.2f), size = Size(w*0.35f, h*0.6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f), style = Stroke(3f))
        drawLine(color = TextLightPurple, start = Offset(w/2, h*0.2f), end = Offset(w/2, h*0.8f), strokeWidth = 3f)
    }
}

@Composable
fun CustomTouchIcon() {
    Canvas(modifier = Modifier.size(28.dp)) {
        drawCircle(color = TextLightPurple, radius = size.width/2.5f, style = Stroke(3f))
        drawCircle(color = TextLightPurple, radius = size.width/8f)
    }
}

@Composable
fun CustomMoonIcon() {
    Canvas(modifier = Modifier.size(26.dp)) {
        val path = Path().apply { addArc(Rect(0f, 0f, size.width, size.height), -90f, 180f) }
        drawPath(path, color = TextLightPurple, style = Stroke(3f))
        drawCircle(color = TextLightPurple, radius = size.width/6f, center = Offset(size.width*0.3f, size.height*0.3f))
    }
}

// ==========================================
// UI Components
// ==========================================
@Composable
fun TopHeaderSection() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "إِبْكَـار", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = TextWhite, fontSize = 28.sp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Filled.LocationOn, null, tint = TextLightPurple, modifier = Modifier.size(14.dp))
            Text(text = "الإسكندرية، مصر", style = MaterialTheme.typography.labelMedium.copy(color = TextLightPurple, fontSize = 12.sp))
        }
    }
}

@Composable
fun HomeScreenContent(
    record: DailyRecord, dailyPoints: Int, totalDoneItems: Int, todayTimes: Map<String, Pair<Int, Int>>, 
    onTogglePrayer: (String) -> Unit, onOpenDhikrMenu: () -> Unit, onOpenWird: () -> Unit, onOpenTasbeeh: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
        
        // 1. بطاقة إجمالي النقاط (بالتنسيق الصحيح)
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).clip(RoundedCornerShape(24.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(24.dp)).padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(text = "إجمالي النقاط", style = MaterialTheme.typography.labelMedium.copy(color = TextLightPurple, fontSize = 13.sp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(text = "$dailyPoints", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, color = PrimaryAccent, fontSize = 36.sp))
                                Text(text = "/100", style = MaterialTheme.typography.bodyLarge.copy(color = TextLightPurple, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 6.dp))
                            }
                        }
                    }
                    CustomBadgeIcon()
                }
                val dayProgress = totalDoneItems.toFloat() / 8f
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(6.dp).clip(RoundedCornerShape(10.dp)).background(AppBackground)) {
                    Box(modifier = Modifier.fillMaxWidth(dayProgress).height(6.dp).clip(RoundedCornerShape(10.dp)).background(PrimaryAccent))
                }
            }
        }

        // 2. المربعات الثلاثة (الأذكار يمين، التسبيح وسط، الورد يسار)
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(20.dp)).clickable { onOpenDhikrMenu() }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CustomMoonIcon()
                        Text("الأذكار", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp))
                    }
                }
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(20.dp)).clickable { onOpenTasbeeh() }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CustomTouchIcon()
                        Text("التسبيح", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp))
                    }
                }
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(20.dp)).clickable { onOpenWird() }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CustomBookIcon()
                        Text("الورد", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp))
                    }
                }
            }
        }

        // 3. الصلوات المفروضة
        item {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "الصلوات المفروضة", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                
                fun t(k: String): String { val d = todayTimes[k] ?: return ""; val h = if(d.first%12==0) 12 else d.first%12; return "%02d:%02d".format(h, d.second) }
                
                PrayerItemRow("الفجر", record.fajrDone, t("fajr")) { onTogglePrayer("fajr") }
                PrayerItemRow("الظهر", record.dhuhrDone, t("dhuhr")) { onTogglePrayer("dhuhr") }
                PrayerItemRow("العصر", record.asrDone, t("asr")) { onTogglePrayer("asr") }
                PrayerItemRow("المغرب", record.maghribDone, t("maghrib")) { onTogglePrayer("maghrib") }
                PrayerItemRow("العشاء", record.ishaDone, t("isha")) { onTogglePrayer("isha") }
            }
        }
    }
}

// صف الصلاة (الدائرة يمين، الاسم يمين، الوقت يسار)
@Composable
fun PrayerItemRow(name: String, isDone: Boolean, timeText: String, onToggle: () -> Unit) {
    val bgColor = if (isDone) PrimaryAccent.copy(alpha = 0.2f) else CardBackground
    val strokeColor = if (isDone) PrimaryAccent else CardStroke

    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bgColor).border(1.dp, strokeColor, RoundedCornerShape(16.dp)).clickable { onToggle() }.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isDone) Icon(Icons.Filled.CheckCircle, null, tint = PrimaryAccent, modifier = Modifier.size(24.dp))
        else Canvas(modifier = Modifier.size(22.dp)) { drawCircle(color = TextWhite.copy(alpha = 0.15f), style = Stroke(width = 3f)) }
        
        Spacer(Modifier.width(16.dp))
        Text(text = name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite))
        Spacer(Modifier.weight(1f))
        Text(text = timeText, style = MaterialTheme.typography.bodyMedium.copy(color = TextLightPurple))
    }
}

// نافذة الأذكار الذكية
@Composable
fun AdhkarSelectionDialog(record: DailyRecord, onToggleMorning: () -> Unit, onToggleEvening: () -> Unit, onReadMorning: () -> Unit, onReadEvening: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBackground,
        title = { Text("الأذكار اليومية", color = TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppBackground).border(1.dp, CardStroke, RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleMorning, modifier = Modifier.size(32.dp)) {
                        if (record.morningDhikrDone) Icon(Icons.Filled.CheckCircle, null, tint = PrimaryAccent) else Canvas(modifier = Modifier.size(22.dp)) { drawCircle(color = TextWhite.copy(alpha=0.2f), style = Stroke(width=3f)) }
                    }
                    Text("أذكار الصباح", color = TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable { onReadMorning() }.padding(horizontal = 16.dp), textAlign = TextAlign.Start)
                }
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppBackground).border(1.dp, CardStroke, RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleEvening, modifier = Modifier.size(32.dp)) {
                        if (record.eveningDhikrDone) Icon(Icons.Filled.CheckCircle, null, tint = PrimaryAccent) else Canvas(modifier = Modifier.size(22.dp)) { drawCircle(color = TextWhite.copy(alpha=0.2f), style = Stroke(width=3f)) }
                    }
                    Text("أذكار المساء", color = TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable { onReadEvening() }.padding(horizontal = 16.dp), textAlign = TextAlign.Start)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)) { Text("إغلاق", color = TextWhite) } }
    )
}

// نوافذ الورد والتسبيح
@Composable
fun TasbeehGlassDialog(count: Int, onIncrement: () -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBackground,
        title = { Text("المسبحة الإلكترونية", color = TextWhite, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(140.dp).clip(CircleShape).background(PrimaryAccent.copy(0.1f)).border(2.dp, PrimaryAccent, CircleShape).clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onIncrement() }, contentAlignment = Alignment.Center) {
                    Text("$count", fontSize = 48.sp, fontWeight = FontWeight.Black, color = TextWhite)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onReset() }) { Text("إعادة ضبط", color = TextLightPurple) }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)) { Text("إغلاق") } }
    )
}

@Composable
fun WirdGlassDialog(pages: Int, onIncrease: () -> Unit, onDecrease: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBackground,
        title = { Text("ورد القرآن الكريم", color = TextWhite, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("عدد الصفحات المقروءة اليوم", color = TextLightPurple, modifier = Modifier.padding(bottom = 16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecrease, modifier = Modifier.size(48.dp).background(AppBackground, CircleShape)) { Text("-", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    Text("$pages", fontSize = 42.sp, fontWeight = FontWeight.Black, color = PrimaryAccent)
                    IconButton(onClick = onIncrease, modifier = Modifier.size(48.dp).background(PrimaryAccent, CircleShape)) { Text("+", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)) { Text("حفظ", color = TextWhite) } }
    )
}

// شاشة الإحصائيات (كاملة)
@Composable
fun AdvancedStatsScreen(history: List<DailyRecord>) {
    val totalScore = history.sumOf { it.calculatePoints() }
    val perfectDays = history.count { it.calculatePoints() == 100 }
    val historyItems = history.reversed()

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text(text = "سجل الإنجازات", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = TextWhite)) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(24.dp)).padding(16.dp)) {
                    Column {
                        Icon(Icons.Filled.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(32.dp).padding(bottom = 8.dp))
                        Text("النقاط الإجمالية", style = MaterialTheme.typography.labelMedium.copy(color = TextLightPurple))
                        Text("$totalScore", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = TextWhite))
                    }
                }
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(24.dp)).padding(16.dp)) {
                    Column {
                        Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(32.dp).padding(bottom = 8.dp))
                        Text("أيام كاملة", style = MaterialTheme.typography.labelMedium.copy(color = TextLightPurple))
                        Text("$perfectDays", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = TextWhite))
                    }
                }
            }
        }
        item { Text(text = "أداء الأيام السابقة", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite), modifier = Modifier.padding(top = 10.dp)) }

        if (historyItems.isEmpty()) {
            item { Text("لا توجد بيانات بعد.", color = TextLightPurple, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        } else {
            items(historyItems) { day ->
                val missed = mutableListOf<String>()
                if (!day.fajrDone) missed.add("الفجر")
                if (!day.dhuhrDone) missed.add("الظهر")
                if (!day.asrDone) missed.add("العصر")
                if (!day.maghribDone) missed.add("المغرب")
                if (!day.ishaDone) missed.add("العشاء")
                if (day.quranPages == 0) missed.add("القرآن")
                if (!day.morningDhikrDone) missed.add("الصباح")
                if (!day.eveningDhikrDone) missed.add("المساء")
                
                val pts = day.calculatePoints()
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(if (pts == 100) SuccessGreen.copy(alpha=0.2f) else PrimaryAccent.copy(alpha=0.2f)).border(2.dp, if (pts == 100) SuccessGreen else PrimaryAccent, CircleShape), contentAlignment = Alignment.Center) {
                        Text("$pts", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = TextWhite))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = day.date, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = TextWhite))
                        if (missed.isEmpty()) { Text(text = "علامة كاملة، أحسنت!", style = MaterialTheme.typography.labelSmall.copy(color = SuccessGreen, fontWeight = FontWeight.Bold)) } 
                        else { Text(text = "فاتك: " + missed.joinToString("، "), style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF87171))) }
                    }
                }
            }
        }
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
fun FullScreenHisn(onBack: () -> Unit) {}

@Composable
fun FullScreenDhikrReading(type: String, onComplete: () -> Unit, onDismiss: () -> Unit) {
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
            Text(text = if (type == "morning") "أذكار الصباح" else "أذكار المساء", style = MaterialTheme.typography.titleLarge.copy(color = PrimaryAccent, fontWeight = FontWeight.Bold))
            IconButton(onClick = { currentIndex = 0; isFinished = false; currentCountsLeft.clear(); currentCountsLeft.addAll(list.map { it.count }) }) { Icon(Icons.Filled.Refresh, null, tint = TextWhite) }
        }

        if (!isFinished && currentDhikr != null) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "${currentIndex + 1} / ${list.size}", style = MaterialTheme.typography.labelSmall.copy(color = TextLightPurple))
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
                    if (currentDhikr.benefit.isNotEmpty()) { Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(CardStroke).padding(horizontal = 16.dp, vertical = 10.dp)) { Text(text = "الفضل: ${currentDhikr.benefit}", style = MaterialTheme.typography.bodySmall.copy(color = TextLightPurple), textAlign = TextAlign.Center) } }
                }
            }
            Box(modifier = Modifier.size(115.dp).clip(CircleShape).background(PrimaryAccent.copy(alpha = 0.2f)).border(2.dp, PrimaryAccent, CircleShape).clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (curCountLeft > 1) { currentCountsLeft[currentIndex] = curCountLeft - 1 } else { currentCountsLeft[currentIndex] = 0; if (currentIndex < list.size - 1) currentIndex++ else isFinished = true }
            }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$curCountLeft", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = TextWhite)
                    Text("متبقي", fontSize = 11.sp, color = TextWhite.copy(alpha = 0.8f))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { if (currentIndex > 0) { currentIndex--; currentCountsLeft[currentIndex] = list[currentIndex].count } }, enabled = currentIndex > 0) { Text("السابق", color = if (currentIndex > 0) PrimaryAccent else Color.Gray, fontWeight = FontWeight.Bold) }
                TextButton(onClick = { currentCountsLeft[currentIndex] = 0; if (currentIndex < list.size - 1) currentIndex++ else isFinished = true }) { Text("تخطي", color = PrimaryAccent, fontWeight = FontWeight.Bold) }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(80.dp))
                Text(text = "تقبل الله طاعتك!", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, color = SuccessGreen))
                Button(onClick = onComplete, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("إتمام", color = Color.White) }
            }
        }
    }
}

@Composable
fun WorshipCelebrationDialog(title: String, description: String, isArabic: Boolean, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        CelebrationEffect()
        AlertDialog(
            onDismissRequest = onDismiss, containerColor = CardBackground,
            title = { Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = { Text(description, style = MaterialTheme.typography.bodyMedium.copy(color = TextLightPurple), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), modifier = Modifier.fillMaxWidth()) { Text("متابعة", color = Color.White, fontWeight = FontWeight.Bold) } }
        )
    }
}

data class Particle(var x: Float, var y: Float, var speedY: Float, var speedX: Float, val color: Color, val isBalloon: Boolean, val size: Float)

@Composable
fun CelebrationEffect() {
    val particles = remember { List(60) { val isB = Random.nextFloat() > 0.7f; Particle(Random.nextFloat() * 1000f, if (isB) 2500f + Random.nextFloat() * 500f else -100f - Random.nextFloat() * 500f, if (isB) -(3f + Random.nextFloat() * 4f) else (5f + Random.nextFloat() * 6f), (Random.nextFloat() - 0.5f) * 4f, listOf(Color(0xFF818CF8), Color(0xFF34D399), Color(0xFFFBBF24)).random(), isB, if (isB) 40f + Random.nextFloat() * 20f else 10f + Random.nextFloat() * 10f) } }
    var trigger by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { withFrameNanos { trigger += 1f }; particles.forEach { p -> p.y += p.speedY; p.x += p.speedX; if (!p.isBalloon && p.y > 3000f) p.y = -100f; if (p.isBalloon && p.y < -500f) p.y = 2500f } } }
    Canvas(modifier = Modifier.fillMaxSize()) { trigger.let { _ -> particles.forEach { p -> if (p.isBalloon) drawCircle(p.color.copy(alpha=0.8f), p.size, Offset(p.x, p.y)) else drawRect(p.color, Offset(p.x, p.y), Size(p.size, p.size)) } } }
}
