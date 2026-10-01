package com.moalmaz.ibkar

import android.content.Context
import android.content.Intent
import android.location.LocationManager
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

// ==========================================
// 1. الألوان المطابقة لصورة التصميم بدقة
// ==========================================
val AppBackground = Color(0xFF131034)
val CardBackground = Color(0xFF221F4C)
val CardStroke = Color(0xFF322E68)
val PrimaryAccent = Color(0xFF6B61FF)
val TextLightPurple = Color(0xFFA19DCD)
val TextWhite = Color.White
val SuccessGreen = Color(0xFF10B981)

// ==========================================
// 2. البيانات الأساسية
// ==========================================
data class CityLocation(val nameAr: String, val nameEn: String, val lat: Float, val lng: Float)
val egyptCities = listOf(CityLocation("الإسكندرية", "Alexandria", 31.2001f, 29.9187f))
data class StepDhikr(val text: String, val count: Int, val benefit: String = "", val translation: String = "")
val morningAdhkarList = listOf(StepDhikr("أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ...", 1))
val eveningAdhkarList = listOf(StepDhikr("أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ...", 1))
val hisnAlMuslimData = mapOf("sleep" to listOf(StepDhikr("بِاسْمِكَ رَبِّـي وَضَعْـتُ جَنْـبي...", 1)))

class MainActivity : ComponentActivity() {
    private var initialDhikrTypeState = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PrayerNotificationManager.createNotificationChannel(this)
        handleIntent(intent)
        setContent {
            MyApplicationTheme(darkTheme = true) {
                // تفعيل دعم اللغة العربية لضبط الترتيب
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
    var activeDhikrType by remember { mutableStateOf<String?>("morning") }
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
        val cal = PrayerTimeCalculator.getLocalCalendar(31.2001, 29.9187)
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
// 3. الأيقونات المرسومة بدقة لتطابق الصورة تماماً
// ==========================================
@Composable
fun CustomBadgeIcon() {
    Canvas(modifier = Modifier.size(34.dp)) {
        val w = size.width; val h = size.height
        drawCircle(color = PrimaryAccent, radius = w * 0.28f, center = Offset(w/2, h*0.35f), style = Stroke(width = 4.5f))
        drawCircle(color = PrimaryAccent, radius = w * 0.10f, center = Offset(w/2, h*0.35f), style = Stroke(width = 4.5f))
        drawLine(color = PrimaryAccent, start = Offset(w*0.35f, h*0.6f), end = Offset(w*0.25f, h*0.9f), strokeWidth = 4.5f)
        drawLine(color = PrimaryAccent, start = Offset(w*0.65f, h*0.6f), end = Offset(w*0.75f, h*0.9f), strokeWidth = 4.5f)
    }
}

@Composable
fun CustomBookIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width; val h = size.height
        drawRoundRect(color = TextLightPurple, topLeft = Offset(w*0.05f, h*0.15f), size = Size(w*0.4f, h*0.7f), style = Stroke(3.5f))
        drawRoundRect(color = TextLightPurple, topLeft = Offset(w*0.55f, h*0.15f), size = Size(w*0.4f, h*0.7f), style = Stroke(3.5f))
        drawLine(color = TextLightPurple, start = Offset(w/2, h*0.15f), end = Offset(w/2, h*0.85f), strokeWidth = 3.5f)
    }
}

@Composable
fun CustomTouchIcon() {
    Canvas(modifier = Modifier.size(26.dp)) {
        drawCircle(color = TextLightPurple, radius = size.width*0.25f, center = Offset(size.width/2, size.height*0.35f), style = Stroke(3.5f))
        drawLine(color = TextLightPurple, start = Offset(size.width/2, size.height*0.6f), end = Offset(size.width/2, size.height*0.9f), strokeWidth = 3.5f)
        drawCircle(color = TextLightPurple, radius = 3.5f, center = Offset(size.width/2, size.height*0.35f))
    }
}

@Composable
fun CustomMoonIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val path = Path().apply { addArc(Rect(0f, 0f, size.width, size.height), -90f, 220f) }
        drawPath(path, color = TextLightPurple, style = Stroke(3.5f))
        drawCircle(color = TextLightPurple, radius = size.width/6f, center = Offset(size.width*0.4f, size.height*0.4f))
    }
}

// ==========================================
// 4. مكونات واجهة المستخدم المطابقة
// ==========================================
@Composable
fun TopHeaderSection() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "إِبْكَـار", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = TextWhite, fontSize = 28.sp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            Icon(Icons.Filled.LocationOn, null, tint = TextLightPurple, modifier = Modifier.size(14.dp))
            Text(text = "الإسكندرية، مصر", style = MaterialTheme.typography.labelMedium.copy(color = TextLightPurple, fontSize = 13.sp))
        }
    }
}

@Composable
fun HomeScreenContent(
    record: DailyRecord, dailyPoints: Int, totalDoneItems: Int, todayTimes: Map<String, Pair<Int, Int>>, 
    onTogglePrayer: (String) -> Unit, onOpenDhikrMenu: () -> Unit, onOpenWird: () -> Unit, onOpenTasbeeh: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
        
        // بطاقة إجمالي النقاط (محاذاة وشكل سليم 100%)
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).clip(RoundedCornerShape(20.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(20.dp)).padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(text = "إجمالي النقاط", style = MaterialTheme.typography.labelMedium.copy(color = TextLightPurple, fontSize = 14.sp))
                        // إصلاح مشكلة الرقم المنعكس بفرض التنسيق الإنجليزي للنص فقط
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(text = "$dailyPoints", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, color = PrimaryAccent, fontSize = 38.sp))
                                Text(text = "/100", style = MaterialTheme.typography.bodyLarge.copy(color = TextLightPurple, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 6.dp))
                            }
                        }
                    }
                    CustomBadgeIcon()
                }
                val dayProgress = totalDoneItems.toFloat() / 8f
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(6.dp).clip(RoundedCornerShape(10.dp)).background(CardStroke)) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Box(modifier = Modifier.fillMaxWidth(dayProgress).height(6.dp).clip(RoundedCornerShape(10.dp)).background(PrimaryAccent))
                    }
                }
            }
        }

        // الأزرار الثلاثة (بالترتيب الصحيح يميناً ويساراً)
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // اليمين: الورد
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(20.dp)).clickable { onOpenWird() }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CustomBookIcon()
                        Text("الورد", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp))
                    }
                }
                // الوسط: التسبيح
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(20.dp)).clickable { onOpenTasbeeh() }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CustomTouchIcon()
                        Text("التسبيح", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp))
                    }
                }
                // اليسار: الأذكار
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(CardBackground).border(1.dp, CardStroke, RoundedCornerShape(20.dp)).clickable { onOpenDhikrMenu() }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CustomMoonIcon()
                        Text("الأذكار", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp))
                    }
                }
            }
        }

        // الصلوات المفروضة
        item {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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

// صف الصلاة (الدائرة يمين، الاسم وسط، الوقت يسار - كما في الصورة تماماً)
@Composable
fun PrayerItemRow(name: String, isDone: Boolean, timeText: String, onToggle: () -> Unit) {
    val bgColor = if (isDone) PrimaryAccent.copy(alpha = 0.2f) else CardBackground
    val strokeColor = if (isDone) PrimaryAccent else CardStroke

    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bgColor).border(1.dp, strokeColor, RoundedCornerShape(16.dp)).clickable { onToggle() }.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        // دائرة التحديد على اليمين
        if (isDone) Icon(Icons.Filled.CheckCircle, null, tint = PrimaryAccent, modifier = Modifier.size(24.dp))
        else Canvas(modifier = Modifier.size(22.dp)) { drawCircle(color = TextWhite.copy(alpha = 0.15f), style = Stroke(width = 4f)) }
        
        Spacer(Modifier.width(20.dp))
        // الاسم في المنتصف
        Text(text = name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextWhite))
        Spacer(Modifier.weight(1f))
        // الوقت على اليسار بتنسيق إنجليزي لضبط الأرقام
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(text = timeText, style = MaterialTheme.typography.bodyMedium.copy(color = TextLightPurple, fontWeight = FontWeight.Bold))
        }
    }
}

// نافذة الأذكار السريعة
@Composable
fun AdhkarSelectionDialog(record: DailyRecord, onToggleMorning: () -> Unit, onToggleEvening: () -> Unit, onReadMorning: () -> Unit, onReadEvening: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBackground,
        title = { Text("الأذكار اليومية", color = TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppBackground).border(1.dp, CardStroke, RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleMorning, modifier = Modifier.size(32.dp)) {
                        if (record.morningDhikrDone) Icon(Icons.Filled.CheckCircle, null, tint = PrimaryAccent) else Canvas(modifier = Modifier.size(22.dp)) { drawCircle(color = TextWhite.copy(alpha=0.2f), style = Stroke(width=4f)) }
                    }
                    Text("أذكار الصباح", color = TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable { onReadMorning() }.padding(horizontal = 16.dp), textAlign = TextAlign.Start)
                }
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppBackground).border(1.dp, CardStroke, RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleEvening, modifier = Modifier.size(32.dp)) {
                        if (record.eveningDhikrDone) Icon(Icons.Filled.CheckCircle, null, tint = PrimaryAccent) else Canvas(modifier = Modifier.size(22.dp)) { drawCircle(color = TextWhite.copy(alpha=0.2f), style = Stroke(width=4f)) }
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
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDecrease, modifier = Modifier.size(48.dp).background(AppBackground, CircleShape)) { Text("-", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                        Text("$pages", fontSize = 42.sp, fontWeight = FontWeight.Black, color = PrimaryAccent)
                        IconButton(onClick = onIncrease, modifier = Modifier.size(48.dp).background(PrimaryAccent, CircleShape)) { Text("+", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)) { Text("حفظ", color=TextWhite) } }
    )
}

@Composable
fun AdvancedStatsScreen(history: List<DailyRecord>) {
    val itemsList = history.reversed()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item { Text("سجل الإنجازات", style = MaterialTheme.typography.titleMedium.copy(color = TextWhite, fontWeight = FontWeight.Bold)) }
        items(itemsList) { day ->
            Text(day.date, color = TextWhite, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}
@Composable
fun FullScreenHisn(onBack: () -> Unit) {}
@Composable
fun FullScreenDhikrReading(type: String, onComplete: () -> Unit, onDismiss: () -> Unit) {}
