package com.moalmaz.ibkar

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moalmaz.ibkar.data.DailyRecord
import java.util.Calendar
import kotlin.random.Random

// الألوان الزجاجية
val GlassBgGradient = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B))
val GlassAccent = Color(0xFF818CF8)
val GlassAccentLight = Color(0xFFA5B4FC)
val GlassWhite = Color.White
val GlassPanelBg = Color.White.copy(alpha = 0.05f)
val GlassPanelBorder = Color.White.copy(alpha = 0.12f)
val GlassSuccess = Color(0xFF10B981)

@Composable
fun GlassCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(20.dp), onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    var mod = modifier.clip(RoundedCornerShape(24.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(24.dp))
    if (onClick != null) mod = mod.clickable { onClick() }
    Column(modifier = mod.padding(padding), content = content)
}

// 1. شريط التتابع العلوي
@Composable
fun TopStreakBar(streak: Int, cityName: String, isArabic: Boolean, onSettingsClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = if (isArabic) "إِبْكَـار" else "Ibkar", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = GlassWhite, fontSize = 28.sp))
                IconButton(onClick = onSettingsClick, modifier = Modifier.size(28.dp).clip(CircleShape).background(GlassPanelBg)) {
                    Icon(Icons.Filled.Settings, null, tint = GlassAccentLight, modifier = Modifier.size(16.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.LocationOn, null, tint = GlassAccentLight, modifier = Modifier.size(12.dp))
                Text(text = cityName, style = MaterialTheme.typography.labelSmall.copy(color = GlassAccentLight, fontWeight = FontWeight.Bold))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Icon(Icons.Filled.LocalFireDepartment, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
            Text(text = if (isArabic) "تتابع: $streak" else "Streak: $streak", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
        }
    }
}

// 2. الشاشة الرئيسية
@Composable
fun HomeScreen(
    isArabic: Boolean, record: DailyRecord, dailyPoints: Int, totalDoneItems: Int, 
    upcomingPrayer: UpcomingPrayerInfo?, todayTimes: Map<String, Pair<Int, Int>>, 
    onTogglePrayer: (String) -> Unit, onOpenDhikr: (String) -> Unit, onOpenWird: () -> Unit, onOpenTasbeeh: () -> Unit, onOpenHisn: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
        
        item {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text(text = if (isArabic) "نقاط اليوم" else "Today's Points", style = MaterialTheme.typography.labelMedium.copy(color = GlassAccentLight, fontWeight = FontWeight.Bold))
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "$dailyPoints", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, color = GlassAccent, fontSize = 40.sp))
                            Text(text = "/100", style = MaterialTheme.typography.bodyLarge.copy(color = GlassAccentLight, fontWeight = FontWeight.Bold, fontSize = 18.sp), modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = GlassSuccess, modifier = Modifier.size(36.dp))
                }
                val dayProgress = totalDoneItems.toFloat() / 8f
                Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(10.dp)).background(GlassWhite.copy(alpha = 0.1f))) {
                    Box(modifier = Modifier.fillMaxWidth(dayProgress).height(8.dp).clip(RoundedCornerShape(10.dp)).background(GlassAccent))
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(20.dp)).clickable { onOpenWird() }.padding(12.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.MenuBook, null, tint = GlassAccentLight, modifier = Modifier.size(32.dp))
                        Text(if (isArabic) "الورد" else "Wird", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                    }
                }
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(20.dp)).clickable { onOpenTasbeeh() }.padding(12.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.TouchApp, null, tint = GlassAccentLight, modifier = Modifier.size(32.dp))
                        Text(if (isArabic) "التسبيح" else "Tasbeeh", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                    }
                }
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(20.dp)).clickable { onOpenHisn() }.padding(12.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Shield, null, tint = GlassAccentLight, modifier = Modifier.size(32.dp))
                        Text(if (isArabic) "حصن" else "Hisn", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard(modifier = Modifier.weight(1f), padding = PaddingValues(16.dp), onClick = { onOpenDhikr("morning") }) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = if (isArabic) "أذكار الصباح" else "Morning", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                        if (record.morningDhikrDone) Icon(Icons.Filled.CheckCircle, null, tint = GlassSuccess) else Icon(Icons.Outlined.WbSunny, null, tint = GlassAccentLight)
                    }
                }
                GlassCard(modifier = Modifier.weight(1f), padding = PaddingValues(16.dp), onClick = { onOpenDhikr("evening") }) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = if (isArabic) "أذكار المساء" else "Evening", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                        if (record.eveningDhikrDone) Icon(Icons.Filled.CheckCircle, null, tint = GlassSuccess) else Icon(Icons.Outlined.NightsStay, null, tint = GlassAccentLight)
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = if (isArabic) "الصلوات المفروضة" else "Obligatory Prayers", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite), modifier = Modifier.padding(start = 4.dp))
                upcomingPrayer?.let { NextPrayerCountdownCard(it, isArabic) }

                fun t(k: String): String { val d = todayTimes[k] ?: return ""; val h = if(d.first%12==0) 12 else d.first%12; val a = if(d.first>=12) (if(isArabic) "م" else "PM") else (if(isArabic) "ص" else "AM"); return "%d:%02d %s".format(h, d.second, a) }
                PrayerItemRow(if (isArabic) "الفجر" else "Fajr", record.fajrDone, t("fajr")) { onTogglePrayer("fajr") }
                PrayerItemRow(if (isArabic) "الظهر" else "Dhuhr", record.dhuhrDone, t("dhuhr")) { onTogglePrayer("dhuhr") }
                PrayerItemRow(if (isArabic) "العصر" else "Asr", record.asrDone, t("asr")) { onTogglePrayer("asr") }
                PrayerItemRow(if (isArabic) "المغرب" else "Maghrib", record.maghribDone, t("maghrib")) { onTogglePrayer("maghrib") }
                PrayerItemRow(if (isArabic) "العشاء" else "Isha", record.ishaDone, t("isha")) { onTogglePrayer("isha") }
            }
        }
    }
}

// 3. شاشة السجل المتطورة
@Composable
fun AdvancedStatsScreen(isArabic: Boolean, history: List<DailyRecord>) {
    val totalScore = history.sumOf { it.calculatePoints() }
    val perfectDays = history.count { it.calculatePoints() == 100 }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text(text = if (isArabic) "سجل الإنجازات" else "Achievement Log", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = GlassWhite)) }
        
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassCard(modifier = Modifier.weight(1f), padding = PaddingValues(16.dp)) {
                    Icon(Icons.Outlined.EmojiEvents, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(32.dp).padding(bottom = 8.dp))
                    Text(if (isArabic) "النقاط الإجمالية" else "Total Points", style = MaterialTheme.typography.labelMedium.copy(color = GlassAccentLight))
                    Text("$totalScore", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = GlassWhite))
                }
                GlassCard(modifier = Modifier.weight(1f), padding = PaddingValues(16.dp)) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = GlassSuccess, modifier = Modifier.size(32.dp).padding(bottom = 8.dp))
                    Text(if (isArabic) "أيام كاملة" else "Perfect Days", style = MaterialTheme.typography.labelMedium.copy(color = GlassAccentLight))
                    Text("$perfectDays", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = GlassWhite))
                }
            }
        }

        item {
            Text(text = if (isArabic) "أداء الأيام السابقة" else "Previous Days", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite), modifier = Modifier.padding(top = 10.dp))
        }

        if (history.isEmpty()) {
            item { Text(if (isArabic) "لا توجد بيانات بعد." else "No records yet.", color = GlassAccentLight, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        } else {
            items(history.reversed()) { day ->
                val missed = mutableListOf<String>()
                if (!day.fajrDone) missed.add(if (isArabic) "الفجر" else "Fajr")
                if (!day.dhuhrDone) missed.add(if (isArabic) "الظهر" else "Dhuhr")
                if (!day.asrDone) missed.add(if (isArabic) "العصر" else "Asr")
                if (!day.maghribDone) missed.add(if (isArabic) "المغرب" else "Maghrib")
                if (!day.ishaDone) missed.add(if (isArabic) "العشاء" else "Isha")
                if (day.quranPages == 0) missed.add(if (isArabic) "القرآن" else "Quran")
                if (!day.morningDhikrDone) missed.add(if (isArabic) "الصباح" else "Morning")
                if (!day.eveningDhikrDone) missed.add(if (isArabic) "المساء" else "Evening")
                
                val pts = day.calculatePoints()

                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = day.date, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                        if (missed.isEmpty()) {
                            Text(text = if (isArabic) "علامة كاملة، أحسنت!" else "Perfect score, well done!", style = MaterialTheme.typography.labelSmall.copy(color = GlassSuccess, fontWeight = FontWeight.Bold))
                        } else {
                            Text(text = (if (isArabic) "فاتك: " else "Missed: ") + missed.joinToString("، "), style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF87171)))
                        }
                    }
                    Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(if (pts == 100) GlassSuccess.copy(alpha=0.2f) else GlassAccent.copy(alpha=0.2f)).border(2.dp, if (pts == 100) GlassSuccess else GlassAccent, CircleShape), contentAlignment = Alignment.Center) {
                        Text("$pts", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = GlassWhite))
                    }
                }
            }
        }
    }
}

// 4. الشاشات المنبثقة والنوافذ
@Composable
fun TasbeehGlassDialog(isArabic: Boolean, count: Int, onIncrement: () -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF1E1B4B),
        title = { Text(if(isArabic) "المسبحة الإلكترونية" else "Digital Rosary", color = GlassWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(140.dp).clip(CircleShape).background(GlassAccent.copy(0.2f)).border(2.dp, GlassAccent, CircleShape).clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onIncrement() }, contentAlignment = Alignment.Center) {
                    Text("$count", fontSize = 48.sp, fontWeight = FontWeight.Black, color = GlassWhite)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onReset() }) { Text(if(isArabic) "إعادة ضبط ↺" else "Reset ↺", color = GlassAccentLight) }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = GlassAccent)) { Text(if(isArabic) "إغلاق" else "Close", color = GlassWhite) } }
    )
}

@Composable
fun WirdGlassDialog(isArabic: Boolean, pages: Int, onIncrease: () -> Unit, onDecrease: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF1E1B4B),
        title = { Text(if(isArabic) "ورد القرآن الكريم" else "Quran Wird", color = GlassWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(if(isArabic) "عدد الصفحات المقروءة اليوم" else "Pages read today", color = GlassAccentLight, modifier = Modifier.padding(bottom = 16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecrease, modifier = Modifier.size(48.dp).background(GlassPanelBorder, CircleShape)) { Text("-", color = GlassWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    Text("$pages", fontSize = 42.sp, fontWeight = FontWeight.Black, color = GlassAccent)
                    IconButton(onClick = onIncrease, modifier = Modifier.size(48.dp).background(GlassAccent, CircleShape)) { Text("+", color = GlassWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = GlassAccent)) { Text(if(isArabic) "حفظ وإغلاق" else "Save & Close", color = GlassWhite) } }
    )
}

@Composable
fun FullScreenHisn(isArabic: Boolean, onBack: () -> Unit) {
    var activeCategory by remember { mutableStateOf<String?>(null) }
    
    if (activeCategory == null) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = GlassWhite, modifier = Modifier.scale(if(isArabic) -1f else 1f)) }
                Text(text = if (isArabic) "حصن المسلم" else "Hisn Al-Muslim", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = GlassWhite), modifier = Modifier.padding(start = 16.dp))
            }
            val cats = listOf("sleep" to (if(isArabic) "أذكار النوم" else "Sleep"), "wakeup" to (if(isArabic) "الاستيقاظ" else "Wake up"), "food" to (if(isArabic) "الطعام" else "Food"), "travel" to (if(isArabic) "السفر" else "Travel"), "home" to (if(isArabic) "المنزل" else "Home"), "mosque" to (if(isArabic) "المسجد" else "Mosque"), "toilet" to (if(isArabic) "الخلاء" else "Toilet"), "rain" to (if(isArabic) "المطر" else "Rain"))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(cats) { cat ->
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(16.dp)).clickable { activeCategory = cat.first }.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = cat.second, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                        Icon(Icons.Outlined.ChevronRight, null, tint = GlassAccentLight, modifier = Modifier.scale(if(isArabic) -1f else 1f))
                    }
                }
            }
        }
    } else {
        val list = hisnAlMuslimData[activeCategory] ?: emptyList()
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { activeCategory = null }) { Icon(Icons.Outlined.ArrowBack, null, tint = GlassWhite, modifier = Modifier.scale(if(isArabic) -1f else 1f)) }
                Text(text = if (isArabic) "الأذكار" else "Supplications", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = GlassWhite), modifier = Modifier.padding(start = 16.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(list) { item ->
                    GlassCard {
                        Text(text = item.text, style = MaterialTheme.typography.titleMedium.copy(lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = GlassWhite), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        if (!isArabic && item.translation.isNotEmpty()) {
                            Text(text = item.translation, style = MaterialTheme.typography.bodyMedium.copy(color = GlassAccentLight), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                        }
                        if (item.benefit.isNotEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(12.dp)).background(GlassPanelBorder).padding(10.dp), contentAlignment = Alignment.Center) {
                                Text(text = if(isArabic) "الفضل: ${item.benefit}" else "Benefit: ${item.benefit}", style = MaterialTheme.typography.labelSmall.copy(color = GlassAccentLight))
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
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, null, tint = GlassWhite) }
            Text(text = if (type == "morning") (if(isArabic) "أذكار الصباح" else "Morning Dhikr") else (if(isArabic) "أذكار المساء" else "Evening Dhikr"), style = MaterialTheme.typography.titleLarge.copy(color = GlassAccent, fontWeight = FontWeight.Bold))
            IconButton(onClick = { currentIndex = 0; isFinished = false; currentCountsLeft.clear(); currentCountsLeft.addAll(list.map { it.count }) }) { Icon(Icons.Outlined.Refresh, null, tint = GlassWhite) }
        }

        if (!isFinished && currentDhikr != null) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "${currentIndex + 1} / ${list.size}", style = MaterialTheme.typography.labelSmall.copy(color = GlassAccentLight))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in list.indices) {
                        val c = when { i < currentIndex -> GlassSuccess; i == currentIndex -> GlassAccent; else -> GlassPanelBorder }
                        Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c))
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp).clip(RoundedCornerShape(24.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(24.dp)).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = currentDhikr.text, style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = GlassWhite), textAlign = TextAlign.Center)
                    if (!isArabic && currentDhikr.translation.isNotEmpty()) { Text(text = currentDhikr.translation, style = MaterialTheme.typography.bodyLarge.copy(color = GlassAccentLight), textAlign = TextAlign.Center) }
                    if (currentDhikr.benefit.isNotEmpty()) { Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(GlassPanelBorder).padding(horizontal = 16.dp, vertical = 10.dp)) { Text(text = if(isArabic) "الفضل: ${currentDhikr.benefit}" else "Benefit: ${currentDhikr.benefit}", style = MaterialTheme.typography.bodySmall.copy(color = GlassAccentLight), textAlign = TextAlign.Center) } }
                }
            }
            Box(modifier = Modifier.size(115.dp).clip(CircleShape).background(GlassAccent.copy(alpha = 0.2f)).border(2.dp, GlassAccent, CircleShape).clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (curCountLeft > 1) { currentCountsLeft[currentIndex] = curCountLeft - 1 } else { currentCountsLeft[currentIndex] = 0; if (currentIndex < list.size - 1) currentIndex++ else isFinished = true }
            }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$curCountLeft", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = GlassWhite)
                    Text(if (isArabic) "متبقي" else "Left", fontSize = 11.sp, color = GlassWhite.copy(alpha = 0.8f))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { if (currentIndex > 0) { currentIndex--; currentCountsLeft[currentIndex] = list[currentIndex].count } }, enabled = currentIndex > 0) { Text(if (isArabic) "السابق" else "Previous", color = if (currentIndex > 0) GlassAccent else Color.Gray, fontWeight = FontWeight.Bold) }
                TextButton(onClick = { currentCountsLeft[currentIndex] = 0; if (currentIndex < list.size - 1) currentIndex++ else isFinished = true }) { Text(if (isArabic) "تخطي" else "Skip", color = GlassAccent, fontWeight = FontWeight.Bold) }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CheckCircle, null, tint = GlassSuccess, modifier = Modifier.size(80.dp))
                Text(text = if (isArabic) "تقبل الله طاعتك!" else "Accepted!", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, color = GlassSuccess))
                Button(onClick = onComplete, colors = ButtonDefaults.buttonColors(containerColor = GlassSuccess), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (isArabic) "إتمام" else "Done", color = Color.White) }
            }
        }
    }
}

// 5. المكونات المساعدة للزجاج العصري
data class UpcomingPrayerInfo(val tag: String, val name: String, val timeStr: String, val diffMinutes: Int, val diffSeconds: Int)

fun getUpcomingPrayer(todayTimes: Map<String, Pair<Int, Int>>, latitude: Double, longitude: Double, isArabic: Boolean): UpcomingPrayerInfo? {
    val now = com.example.notification.PrayerTimeCalculator.getLocalCalendar(latitude, longitude)
    val cMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val cSec = now.get(Calendar.SECOND)
    val cTotal = cMin * 60 + cSec
    val pList = if (isArabic) listOf("fajr" to "الفجر", "dhuhr" to "الظهر", "asr" to "العصر", "maghrib" to "المغرب", "isha" to "العشاء") else listOf("fajr" to "Fajr", "dhuhr" to "Dhuhr", "asr" to "Asr", "maghrib" to "Maghrib", "isha" to "Isha")
    for (p in pList) {
        val t = todayTimes[p.first]
        if (t != null) {
            val pTotal = (t.first * 60 + t.second) * 60
            if (pTotal > cTotal) {
                val r = pTotal - cTotal
                val h12 = if (t.first % 12 == 0) 12 else t.first % 12
                val a = if (t.first >= 12) (if(isArabic) "م" else "PM") else (if(isArabic) "ص" else "AM")
                return UpcomingPrayerInfo(p.first, p.second, "%d:%02d %s".format(h12, t.second, a), r / 60, r % 60)
            }
        }
    }
    val t = todayTimes["fajr"]
    if (t != null) {
        val pTotal = ((t.first + 24) * 60 + t.second) * 60
        val r = pTotal - cTotal
        val h12 = if (t.first % 12 == 0) 12 else t.first % 12
        val a = if(isArabic) "ص" else "AM"
        return UpcomingPrayerInfo("fajr", if (isArabic) "فجر الغد" else "Tomorrow's Fajr", "%d:%02d %s".format(h12, t.second, a), r / 60, r % 60)
    }
    return null
}

@Composable
fun PrayerItemRow(name: String, isDone: Boolean, timeText: String, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (isDone) GlassAccent.copy(alpha=0.2f) else GlassPanelBg).border(1.dp, if (isDone) GlassAccent.copy(alpha=0.5f) else GlassPanelBorder, RoundedCornerShape(16.dp)).clickable { onToggle() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = timeText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GlassAccentLight), modifier = Modifier.width(65.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = if(isDone) GlassWhite else GlassWhite.copy(alpha=0.8f)), modifier = Modifier.weight(1f))
        if (isDone) Icon(Icons.Filled.CheckCircle, null, tint = GlassAccent, modifier = Modifier.size(24.dp))
        else Canvas(modifier = Modifier.size(22.dp)) { drawCircle(color = GlassWhite.copy(alpha = 0.3f), style = Stroke(width = 4f)) }
    }
}

@Composable
fun NextPrayerCountdownCard(upcoming: UpcomingPrayerInfo, isArabic: Boolean) {
    val h = upcoming.diffMinutes / 60; val m = upcoming.diffMinutes % 60; val s = upcoming.diffSeconds
    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(text = if (isArabic) "الوقت المتبقي للأذان:" else "Time until Adhan:", style = MaterialTheme.typography.labelSmall.copy(color = GlassAccentLight, fontWeight = FontWeight.Bold))
                Text(text = if(h>0) "%02d:%02d:%02d".format(h,m,s) else "%02d:%02d".format(m,s), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = GlassAccent, fontSize = 24.sp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = upcoming.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = GlassWhite))
                Box(modifier = Modifier.clip(RoundedCornerShape(50.dp)).background(GlassWhite.copy(alpha=0.1f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(text = upcoming.timeStr, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                }
            }
        }
    }
}

// 6. الاحتفالات والتأثيرات
@Composable
fun WorshipCelebrationDialog(title: String, description: String, isArabic: Boolean, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        CelebrationEffect()
        AlertDialog(
            onDismissRequest = onDismiss, containerColor = Color(0xFF1E1B4B),
            title = { Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = { Text(description, style = MaterialTheme.typography.bodyMedium.copy(color = GlassAccentLight), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = GlassSuccess), modifier = Modifier.fillMaxWidth()) { Text(if (isArabic) "متابعة" else "Continue", color = Color.White, fontWeight = FontWeight.Bold) } }
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
