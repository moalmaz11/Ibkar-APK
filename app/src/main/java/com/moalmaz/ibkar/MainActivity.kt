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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

// ==========================================
// 1. البيانات الأساسية (كما هي في ملفك)
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
// 2. الألوان (مسحوبة بدقة من صورتك المستهدفة)
// ==========================================
val AppBackground = Color(0xFF10132B)
val CardBackground = Color(0xFF22224A)
val CardStroke = Color(0xFF333366)
val PrimaryAccent = Color(0xFF6B7BFF)
val TextLightPurple = Color(0xFFAAA9D1)
val TextWhite = Color.White
val SuccessGreen = Color(0xFF10B981)

// ==========================================
// 3. الأيقونات المرسومة يدوياً (لتجنب أي أخطاء Build)
// ==========================================
@Composable
fun CustomBadgeIcon() {
    Canvas(modifier = Modifier.size(32.dp)) {
        val w = size.width; val h = size.height
        drawCircle(color = TextLightPurple, radius = w * 0.35f, center = Offset(w/2, h*0.4f), style = Stroke(width = 4f))
        drawLine(color = TextLightPurple, start = Offset(w*0.3f, h*0.7f), end = Offset(w*0.2f, h), strokeWidth = 4f)
        drawLine(color = TextLightPurple, start = Offset(w*0.7f, h*0.7f), end = Offset(w*0.8f, h), strokeWidth = 4f)
    }
}

@Composable
fun CustomBookIcon() {
    Canvas(modifier = Modifier.size(26.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(color = TextLightPurple, topLeft = Offset(w*0.1f, h*0.2f), size = Size(w*0.35f, h*0.6f), style = Stroke(3f))
        drawRoundRect(color = TextLightPurple, topLeft = Offset(w*0.55f, h*0.2f), size = Size(w*0.35f, h*0.6f), style = Stroke(3f))
        drawLine(color = TextLightPurple, start = Offset(w/2, h*0.2f), end = Offset(w/2, h*0.8f), strokeWidth = 3f)
    }
}

@Composable
fun CustomTouchIcon() {
    Canvas(modifier = Modifier.size(26.dp)) {
        drawCircle(color = TextLightPurple, radius = size.width/2.5f, style = Stroke(3f))
        drawCircle(color = TextLightPurple, radius = size.width/8f)
    }
}

@Composable
fun CustomMoonIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val path = Path().apply { addArc(Rect(0f, 0f, size.width, size.height), -90f, 180f) }
        drawPath(path, color = TextLightPurple, style = Stroke(3f))
        drawCircle(color = TextLightPurple, radius = size.width/6f, center = Offset(size.width*0.3f, size.height*0.3f))
    }
}

// ==========================================
// 4. الكود الأساسي (Main Activity)
// ==========================================
class MainActivity : ComponentActivity() {
    private var initialDhikrTypeState = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PrayerNotificationManager.createNotificationChannel(this)
        handleIntent(intent)
        setContent {
            MyApplicationTheme(darkTheme = true) {
                // فرض الاتجاه العربي (RTL) لضبط التصميم بدقة
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

enum class AppRoute { Home, Stats, FullScreenDhikr }

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

    val activeRecord = record ?: DailyRecord(date = DateHelper.getTodayDateString(context))
    val prayersDoneCount = listOf(activeRecord.fajrDone, activeRecord.dhuhrDone, activeRecord.asrDone, activeRecord.maghribDone, activeRecord.ishaDone).count { it }
    val isQuranDone = activeRecord.quranPages > 0
    val totalDoneItems = prayersDoneCount + (if (isQuranDone) 1 else 0) + (if (activeRecord.morningDhikrDone) 1 else 0) + (if (activeRecord.eveningDhikrDone) 1 else 0)
    val dailyPoints = activeRecord.calculatePoints()
    
    val todayTimesRaw = remember {
        val cal = PrayerTimeCalculator.getLocalCalendar(31.2001, 29.9187) // الإسكندرية
        PrayerTimeCalculator.calculatePrayerTimes(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), 31.2001, 29.9187, 5)
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
        Box(modifier = Modifier.fillMaxSize().background(AppBackground).padding(paddingValues)) {
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
                            onOpenTasbeeh = { showTasbeehDialog = true }
                        )
                    }
                }
                AppRoute.Stats -> AdvancedStatsScreen(historyData)
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
                else -> {}
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
// 5. مكونات واجهة المستخدم (مطابقة للصورة)
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
        
        // 1. بطاقة إجمالي النقاط (نفس الأبعاد والترتيب)
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).clip(RoundedCornerShape(24.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(24.dp)).padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    CustomBadgeIcon()
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "إجمالي النقاط", style = MaterialTheme.typography.labelMedium.copy(color = TextLightPurple, fontSize = 13.sp))
                        // إصلاح مشكلة الأرقام المعكوسة (80/100) باستخدام LTR
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(text = "$dailyPoints", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, color = PrimaryAccent, fontSize = 38.sp))
                                Text(text = "/100", style = MaterialTheme.typography.bodyLarge.copy(color = TextLightPurple, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 6.dp))
                            }
                        }
                    }
                }
                // شريط التقدم
                val dayProgress = totalDoneItems.toFloat() / 8f
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(6.dp).clip(RoundedCornerShape(10.dp)).background(AppBackground)) {
                    Box(modifier = Modifier.fillMaxWidth(dayProgress).height(6.dp).clip(RoundedCornerShape(10.dp)).background(PrimaryAccent))
                }
            }
        }

        // 2. الأزرار الثلاثة (الأذكار، التسبيح، الورد)
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // زر الأذكار (يمين)
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(20.dp)).clickable { onOpenDhikrMenu() }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CustomMoonIcon()
                        Text("الأذكار", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp))
                    }
                }
                // زر التسبيح (وسط)
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(20.dp)).clickable { onOpenTasbeeh() }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CustomTouchIcon()
                        Text("التسبيح", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp))
                    }
                }
                // زر الورد (يسار)
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

// تصميم صف الصلاة (الدائرة يمين، الاسم يمين، الوقت يسار)
@Composable
fun PrayerItemRow(name: String, isDone: Boolean, timeText: String, onToggle: () -> Unit) {
    val bgColor = if (isDone) PrimaryAccent.copy(alpha = 0.2f) else CardBackground
    val strokeColor = if (isDone) PrimaryAccent else CardStroke

    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bgColor).border(1.dp, strokeColor, RoundedCornerShape(16.dp)).clickable { onToggle() }.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        // دائرة التحديد على اليمين
        if (isDone) Icon(Icons.Filled.CheckCircle, null, tint = PrimaryAccent, modifier = Modifier.size(24.dp))
        else Canvas(modifier = Modifier.size(22.dp)) { drawCircle(color = TextWhite.copy(alpha = 0.15f), style = Stroke(width = 3f)) }
        
        Spacer(Modifier.width(16.dp))
        // الاسم
        Text(text = name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite))
        Spacer(Modifier.weight(1f))
        // الوقت على اليسار
        Text(text = timeText, style = MaterialTheme.typography.bodyMedium.copy(color = TextLightPurple))
    }
}

// نافذة الأذكار السريعة لحل مشكلة عدم الاحتساب السريع
@Composable
fun AdhkarSelectionDialog(record: DailyRecord, onToggleMorning: () -> Unit, onToggleEvening: () -> Unit, onReadMorning: () -> Unit, onReadEvening: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBackground,
        title = { Text("الأذكار اليومية", color = TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // الصباح
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppBackground).border(1.dp, CardStroke, RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleMorning, modifier = Modifier.size(32.dp)) {
                        if (record.morningDhikrDone) Icon(Icons.Filled.CheckCircle, null, tint = PrimaryAccent) else Canvas(modifier = Modifier.size(22.dp)) { drawCircle(color = TextWhite.copy(alpha=0.2f), style = Stroke(width=3f)) }
                    }
                    Text("أذكار الصباح", color = TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable { onReadMorning() }.padding(horizontal = 16.dp), textAlign = TextAlign.Start)
                }
                // المساء
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
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBackground,
        title = { Text("المسبحة الإلكترونية", color = TextWhite, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(140.dp).clip(CircleShape).background(PrimaryAccent.copy(0.1f)).border(2.dp, PrimaryAccent, CircleShape).clickable { onIncrement() }, contentAlignment = Alignment.Center) {
                    Text("$count", fontSize = 48.sp, fontWeight = FontWeight.Black, color = TextWhite)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onReset) { Text("إعادة ضبط", color = TextLightPurple) }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)) { Text("إغلاق", color=TextWhite) } }
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
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)) { Text("حفظ", color=TextWhite) } }
    )
}

@Composable
fun AdvancedStatsScreen(history: List<DailyRecord>) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item { Text("سجل الإنجازات", style = MaterialTheme.typography.titleMedium.copy(color = TextWhite)) }
    }
}
@Composable
fun FullScreenDhikrReading(type: String, onComplete: () -> Unit, onDismiss: () -> Unit) {}
