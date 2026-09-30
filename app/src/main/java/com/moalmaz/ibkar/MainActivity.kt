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
// 1. قاعدة بيانات الموقع والأذكار
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
    CityLocation("الأقصر", "Luxor", 25.6872f, 32.6396f), CityLocation("أسوان", "Aswan", 24.0889f, 32.8998f)
)

fun getNearestCity(lat: Float, lng: Float): CityLocation = egyptCities.minByOrNull { city ->
    val dLat = city.lat - lat; val dLng = city.lng - lng; (dLat * dLat) + (dLng * dLng)
} ?: egyptCities[0]

fun updateLocationOffline(context: Context, onResult: (Boolean, String, Float, Float) -> Unit) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    try {
        val isGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNet = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!isGps && !isNet) { onResult(false, "الرجاء تفعيل GPS", 0f, 0f); return }
        val loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (loc != null) onResult(true, "تم", loc.latitude.toFloat(), loc.longitude.toFloat()) else onResult(false, "تعذر التحديد", 0f, 0f)
    } catch (e: SecurityException) { onResult(false, "مرفوض", 0f, 0f) }
}

data class StepDhikr(val text: String, val count: Int, val benefit: String = "", val translation: String = "")

val morningAdhkarList = listOf(
    StepDhikr("أَعُوذُ بِاللهِ مِنْ الشَّيْطَانِ الرَّجِيمِ\nاللّهُ لاَ إِلَـهَ إِلاَّ هُوَ الْحَيُّ الْقَيُّومُ...", 1, "أجير من الجن حتى يمسي", "Ayat al-Kursi: Allah! There is no deity except Him, the Ever-Living..."),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ هُوَ ٱللَّهُ أَحَدٌ، ٱللَّهُ ٱلصَّمَدُ، لَمْ يَلِدْ وَلَمْ يُولَدْ، وَلَمْ يَكُن لَّهُۥ كُفُوًا أَحَدٌ.", 3, "تكفيه من كل شيء", "Surah Al-Ikhlas: Say, He is Allah, [who is] One..."),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ أَعُوذُ بِرَبِّ ٱلْفَلَقِ، مِن شَرِّ مَا خَلَقَ...", 3, "تكفيه من كل شيء", "Surah Al-Falaq: Say, I seek refuge in the Lord of daybreak..."),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ أَعُوذُ بِرَبِّ ٱلنَّاسِ، مَلِكِ ٱلنَّاسِ...", 3, "تكفيه من كل شيء", "Surah An-Nas: Say, I seek refuge in the Lord of mankind..."),
    StepDhikr("أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لا إِلَهَ إِلا اللَّهُ وَحْدَهُ لا شَرِيكَ لَهُ...", 1, "سؤال خير اليوم", "We have reached the morning and at this very time unto Allah belongs all sovereignty..."),
    StepDhikr("اللّهُـمَّ أَنْتَ رَبِّـي لا إِلهَ إِلاّ أَنْتَ، خَلَقْتَنـي وَأَنا عَبْـدُك...", 1, "سيد الاستغفار", "O Allah, You are my Lord, none has the right to be worshipped except You..."),
    StepDhikr("رَضِيتُ بِاللَّهِ رَبّاً، وَبِالْإِسْلَامِ دِيناً، وَبِمُحَمَّدٍ صلى الله عليه وسلم نَبِيّاً.", 3, "كان حقاً على الله أن يرضيه", "I am pleased with Allah as my Lord, with Islam as my religion..."),
    StepDhikr("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ.", 3, "لم يضره شيء", "In the Name of Allah, with Whose Name nothing can cause harm..."),
    StepDhikr("حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ.", 7, "كفاه الله ما أهمه", "Allah is sufficient for me. There is none worthy of worship but Him..."),
    StepDhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.", 100, "حُطّت خطاياه وإن كانت مثل زبد البحر", "Glory is to Allah and praise is to Him.")
)

val eveningAdhkarList = listOf(
    StepDhikr("أَعُوذُ بِاللهِ مِنْ الشَّيْطَانِ الرَّجِيمِ\nاللّهُ لاَ إِلَـهَ إِلاَّ هُوَ الْحَيُّ الْقَيُّومُ...", 1, "أجير من الجن حتى يصبح", "Ayat al-Kursi: Allah! There is no deity except Him, the Ever-Living..."),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ هُوَ ٱللَّهُ أَحَدٌ، ٱللَّهُ ٱلصَّمَدُ...", 3, "تكفيه من كل شيء", "Surah Al-Ikhlas: Say, He is Allah, [who is] One..."),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ أَعُوذُ بِرَبِّ ٱلْفَلَقِ...", 3, "تكفيه من كل شيء", "Surah Al-Falaq: Say, I seek refuge in the Lord of daybreak..."),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ أَعُوذُ بِرَبِّ ٱلنَّاسِ...", 3, "تكفيه من كل شيء", "Surah An-Nas: Say, I seek refuge in the Lord of mankind..."),
    StepDhikr("أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لا إِلَهَ إِلا اللَّهُ وَحْدَهُ لا شَرِيكَ لَهُ...", 1, "سؤال خير الليلة", "We have reached the evening and at this very time unto Allah belongs all sovereignty..."),
    StepDhikr("اللّهُـمَّ أَنْتَ رَبِّـي لا إِلهَ إِلاّ أَنْتَ، خَلَقْتَنـي وَأَنا عَبْـدُك...", 1, "سيد الاستغفار", "O Allah, You are my Lord, none has the right to be worshipped except You..."),
    StepDhikr("أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.", 3, "لم يضره شيء في تلك الليلة", "I seek refuge in the Perfect Words of Allah from the evil of what He has created."),
    StepDhikr("رَضِيتُ بِاللَّهِ رَبّاً، وَبِالْإِسْلَامِ دِيناً، وَبِمُحَمَّدٍ صلى الله عليه وسلم نَبِيّاً.", 3, "كان حقاً على الله أن يرضيه", "I am pleased with Allah as my Lord, with Islam as my religion..."),
    StepDhikr("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ.", 3, "لم يضره شيء", "In the Name of Allah, with Whose Name nothing can cause harm..."),
    StepDhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.", 100, "حُطّت خطاياه وإن كانت مثل زبد البحر", "Glory is to Allah and praise is to Him.")
)

val hisnAlMuslimData = mapOf(
    "sleep" to listOf(StepDhikr("بِاسْمِكَ رَبِّـي وَضَعْـتُ جَنْـبي، وَبِكَ أَرْفَعُـه...", 1, "الحفظ أثناء النوم", "In Your name my Lord, I lie down..."), StepDhikr("اللَّهُمَّ إِنَّكَ خَلَقْتَ نَفْسِي وَأَنْتَ تَوَفَّاهَا...", 1, "تسليم الروح لله", "O Allah, You created my soul...")),
    "wakeup" to listOf(StepDhikr("الحَمْـدُ لِلّهِ الّذي أَحْـيانا بَعْـدَ ما أَماتَـنا وَإليه النُّـشور.", 1, "شكر الله", "All praise is to Allah who gave us life..."), StepDhikr("لا إلهَ إلاّ اللّهُ وَحْـدَهُ لا شَـريكَ له...", 1, "توحيد خالص", "None has the right to be worshipped except Allah...")),
    "food" to listOf(StepDhikr("بِسْمِ اللَّهِ.", 1, "عند البدء", "In the name of Allah."), StepDhikr("الْحَمْدُ لِلَّهِ الَّذِي أَطْعَمَنِي هَذَا وَرَزَقَنِيهِ مِنْ غَيْرِ حَوْلٍ مِنِّي وَلَا قُوَّةٍ.", 1, "عند الانتهاء", "Praise be to Allah who fed me this...")),
    "travel" to listOf(StepDhikr("سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ...", 1, "دعاء الركوب", "Glory to Him who has subjected this to us..."), StepDhikr("اللَّهُمَّ إِنَّا نَسْأَلُكَ فِي سَفَرِنَا هَذَا الْبِرَّ وَالتَّقْوَى...", 1, "دعاء السفر", "O Allah, we ask You on this journey for righteousness...")),
    "home" to listOf(StepDhikr("بِسْـمِ اللهِ وَلَجْنـا، وَبِسْـمِ اللهِ خَـرَجْنـا...", 1, "عند الدخول", "In the name of Allah we enter..."), StepDhikr("بِسْمِ اللَّهِ، تَوَكَّلْتُ عَلَى اللَّهِ، وَلَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ.", 1, "عند الخروج", "In the name of Allah, I place my trust in Allah...")),
    "mosque" to listOf(StepDhikr("اللَّهُمَّ افْتَحْ لِي أَبْوَابَ رَحْمَتِكَ.", 1, "عند الدخول", "O Allah, open the doors of Your mercy for me."), StepDhikr("اللَّهُمَّ إِنِّي أَسْأَلُكَ مِنْ فَضْلِكَ.", 1, "عند الخروج", "O Allah, I ask You from Your bounty.")),
    "toilet" to listOf(StepDhikr("بِسْمِ الله، اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْخُبْثِ وَالْخَبَائِثِ.", 1, "عند الدخول", "O Allah I seek refuge in You from evil..."), StepDhikr("غُفْرَانَكَ.", 1, "عند الخروج", "I ask You for forgiveness.")),
    "rain" to listOf(StepDhikr("اللَّهُمَّ صَيِّباً نَافِعاً.", 1, "عند نزول المطر", "O Allah, (bring) beneficial rain cloud."), StepDhikr("مُطِرْنَا بِفَضْلِ اللَّهِ وَرَحْمَتِهِ.", 1, "بعد نزول المطر", "It has rained by the bounty of Allah and His mercy."))
)

// ==========================================
// 2. الكود الأساسي (Main Activity)
// ==========================================
val GlassBgGradient = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B))
val GlassAccent = Color(0xFF818CF8)
val GlassAccentLight = Color(0xFFA5B4FC)
val GlassWhite = Color.White
val GlassPanelBg = Color.White.copy(alpha = 0.05f)
val GlassPanelBorder = Color.White.copy(alpha = 0.12f)
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
    
    val recordState = viewModel.currentRecord.collectAsStateWithLifecycle()
    val record = recordState.value
    
    val streakState = viewModel.currentStreak.collectAsStateWithLifecycle()
    val streak = streakState.value

    val prefs = remember(context) { context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE) }
    var notifyPrayers by remember { mutableStateOf(prefs.getBoolean("notify_prayers", true)) }
    var notifyMorningDhikr by remember { mutableStateOf(prefs.getBoolean("notify_morning_dhikr", true)) }
    var notifyEveningDhikr by remember { mutableStateOf(prefs.getBoolean("notify_evening_dhikr", true)) }
    
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
    
    val todayTimesRaw = remember(prefs.getFloat("user_latitude", 30.0444f), prefs.getFloat("user_longitude", 31.2357f), prefs.getInt("prayer_calc_method", 0)) {
        val cal = PrayerTimeCalculator.getLocalCalendar(prefs.getFloat("user_latitude", 30.0444f).toDouble(), prefs.getFloat("user_longitude", 31.2357f).toDouble())
        PrayerTimeCalculator.calculatePrayerTimes(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), prefs.getFloat("user_latitude", 30.0444f).toDouble(), prefs.getFloat("user_longitude", 31.2357f).toDouble(), prefs.getInt("prayer_calc_method", 0))
    }
    var upcomingPrayerInfoState by remember(todayTimesRaw) { mutableStateOf<UpcomingPrayerInfo?>(null) }
    val isTodaySelected = activeRecord.date == DateHelper.getTodayDateString(context)

    LaunchedEffect(todayTimesRaw, isTodaySelected) {
        if (isTodaySelected) { while (true) { upcomingPrayerInfoState = getUpcomingPrayer(todayTimesRaw, prefs.getFloat("user_latitude", 30.0444f).toDouble(), prefs.getFloat("user_longitude", 31.2357f).toDouble(), isArabic); delay(1000L) } }
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
                        icon = { Icon(if (currentRoute == AppRoute.Stats) Icons.Filled.List else Icons.Outlined.List, null) },
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
                                Switch(checked = notifyPrayers, onCheckedChange = { notifyPrayers = it; prefs.edit().putBoolean("notify_prayers", it).apply(); PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, colors = SwitchDefaults.colors(checkedThumbColor = GlassSuccess, checkedTrackColor = GlassSuccess.copy(alpha=0.5f)))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if(isArabic) "أذكار الصباح" else "Morning Dhikr", color = GlassWhite)
                                Switch(checked = notifyMorningDhikr, onCheckedChange = { notifyMorningDhikr = it; prefs.edit().putBoolean("notify_morning_dhikr", it).apply(); PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, colors = SwitchDefaults.colors(checkedThumbColor = GlassSuccess, checkedTrackColor = GlassSuccess.copy(alpha=0.5f)))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if(isArabic) "أذكار المساء" else "Evening Dhikr", color = GlassWhite)
                                Switch(checked = notifyEveningDhikr, onCheckedChange = { notifyEveningDhikr = it; prefs.edit().putBoolean("notify_evening_dhikr", it).apply(); PrayerNotificationManager.scheduleDailyPrayerReminders(context) }, colors = SwitchDefaults.colors(checkedThumbColor = GlassSuccess, checkedTrackColor = GlassSuccess.copy(alpha=0.5f)))
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

// ------------------------------------------------------------------------
// UI Components
// ------------------------------------------------------------------------

@Composable
fun GlassCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(20.dp), onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    var mod = modifier.clip(RoundedCornerShape(24.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(24.dp))
    if (onClick != null) mod = mod.clickable { onClick() }
    Column(modifier = mod.padding(padding), content = content)
}

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
            Icon(Icons.Filled.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
            Text(text = if (isArabic) "تتابع: $streak" else "Streak: $streak", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
        }
    }
}

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
                    Icon(Icons.Filled.CheckCircle, null, tint = GlassSuccess, modifier = Modifier.size(36.dp))
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
                        Icon(Icons.Filled.List, null, tint = GlassAccentLight, modifier = Modifier.size(32.dp))
                        Text(if (isArabic) "الورد" else "Wird", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                    }
                }
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(20.dp)).clickable { onOpenTasbeeh() }.padding(12.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Add, null, tint = GlassAccentLight, modifier = Modifier.size(32.dp))
                        Text(if (isArabic) "التسبيح" else "Tasbeeh", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                    }
                }
                Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(20.dp)).clickable { onOpenHisn() }.padding(12.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Lock, null, tint = GlassAccentLight, modifier = Modifier.size(32.dp))
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
                        if (record.morningDhikrDone) Icon(Icons.Filled.CheckCircle, null, tint = GlassSuccess) else Icon(Icons.Filled.Done, null, tint = GlassAccentLight)
                    }
                }
                GlassCard(modifier = Modifier.weight(1f), padding = PaddingValues(16.dp), onClick = { onOpenDhikr("evening") }) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = if (isArabic) "أذكار المساء" else "Evening", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                        if (record.eveningDhikrDone) Icon(Icons.Filled.CheckCircle, null, tint = GlassSuccess) else Icon(Icons.Filled.Done, null, tint = GlassAccentLight)
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

@Composable
fun AdvancedStatsScreen(isArabic: Boolean, history: List<DailyRecord>) {
    val totalScore = history.sumOf { it.calculatePoints() }
    val perfectDays = history.count { it.calculatePoints() == 100 }
    val historyItems = history.reversed()

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text(text = if (isArabic) "سجل الإنجازات" else "Achievement Log", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = GlassWhite)) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassCard(modifier = Modifier.weight(1f), padding = PaddingValues(16.dp)) {
                    Icon(Icons.Filled.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(32.dp).padding(bottom = 8.dp))
                    Text(if (isArabic) "النقاط الإجمالية" else "Total Points", style = MaterialTheme.typography.labelMedium.copy(color = GlassAccentLight))
                    Text("$totalScore", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = GlassWhite))
                }
                GlassCard(modifier = Modifier.weight(1f), padding = PaddingValues(16.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = GlassSuccess, modifier = Modifier.size(32.dp).padding(bottom = 8.dp))
                    Text(if (isArabic) "أيام كاملة" else "Perfect Days", style = MaterialTheme.typography.labelMedium.copy(color = GlassAccentLight))
                    Text("$perfectDays", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = GlassWhite))
                }
            }
        }
        item { Text(text = if (isArabic) "أداء الأيام السابقة" else "Previous Days", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite), modifier = Modifier.padding(top = 10.dp)) }

        if (historyItems.isEmpty()) {
            item { Text(if (isArabic) "لا توجد بيانات بعد." else "No records yet.", color = GlassAccentLight, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        } else {
            items(historyItems) { day ->
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
                        if (missed.isEmpty()) { Text(text = if (isArabic) "علامة كاملة، أحسنت!" else "Perfect score!", style = MaterialTheme.typography.labelSmall.copy(color = GlassSuccess, fontWeight = FontWeight.Bold)) } 
                        else { Text(text = (if (isArabic) "فاتك: " else "Missed: ") + missed.joinToString("، "), style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF87171))) }
                    }
                    Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(if (pts == 100) GlassSuccess.copy(alpha=0.2f) else GlassAccent.copy(alpha=0.2f)).border(2.dp, if (pts == 100) GlassSuccess else GlassAccent, CircleShape), contentAlignment = Alignment.Center) {
                        Text("$pts", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = GlassWhite))
                    }
                }
            }
        }
    }
}

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
                IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardArrowLeft, null, tint = GlassWhite, modifier = Modifier.scale(if(isArabic) -1f else 1f)) }
                Text(text = if (isArabic) "حصن المسلم" else "Hisn Al-Muslim", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = GlassWhite), modifier = Modifier.padding(start = 16.dp))
            }
            val cats = listOf("sleep" to (if(isArabic) "أذكار النوم" else "Sleep"), "wakeup" to (if(isArabic) "الاستيقاظ" else "Wake up"), "food" to (if(isArabic) "الطعام" else "Food"), "travel" to (if(isArabic) "السفر" else "Travel"), "home" to (if(isArabic) "المنزل" else "Home"), "mosque" to (if(isArabic) "المسجد" else "Mosque"), "toilet" to (if(isArabic) "الخلاء" else "Toilet"), "rain" to (if(isArabic) "المطر" else "Rain"))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(cats) { cat ->
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GlassPanelBg).border(1.dp, GlassPanelBorder, RoundedCornerShape(16.dp)).clickable { activeCategory = cat.first }.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = cat.second, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = GlassWhite))
                        Icon(Icons.Filled.KeyboardArrowRight, null, tint = GlassAccentLight, modifier = Modifier.scale(if(isArabic) -1f else 1f))
                    }
                }
            }
        }
    } else {
        val list = hisnAlMuslimData[activeCategory] ?: emptyList()
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { activeCategory = null }) { Icon(Icons.Filled.KeyboardArrowLeft, null, tint = GlassWhite, modifier = Modifier.scale(if(isArabic) -1f else 1f)) }
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
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null, tint = GlassWhite) }
            Text(text = if (type == "morning") (if(isArabic) "أذكار الصباح" else "Morning Dhikr") else (if(isArabic) "أذكار المساء" else "Evening Dhikr"), style = MaterialTheme.typography.titleLarge.copy(color = GlassAccent, fontWeight = FontWeight.Bold))
            IconButton(onClick = { currentIndex = 0; isFinished = false; currentCountsLeft.clear(); currentCountsLeft.addAll(list.map { it.count }) }) { Icon(Icons.Filled.Refresh, null, tint = GlassWhite) }
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

data class UpcomingPrayerInfo(val tag: String, val name: String, val timeStr: String, val diffMinutes: Int, val diffSeconds: Int)

fun getUpcomingPrayer(todayTimes: Map<String, Pair<Int, Int>>, latitude: Double, longitude: Double, isArabic: Boolean): UpcomingPrayerInfo? {
    val now = PrayerTimeCalculator.getLocalCalendar(latitude, longitude)
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
