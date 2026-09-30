package com.example

import android.content.Context
import android.location.LocationManager

// ==========================================
// 1. قاعدة بيانات الموقع
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
    val dLat = city.lat - lat
    val dLng = city.lng - lng
    (dLat * dLat) + (dLng * dLng)
} ?: egyptCities[0]

fun updateLocationOffline(context: Context, onResult: (Boolean, String, Float, Float) -> Unit) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    try {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!isGpsEnabled && !isNetworkEnabled) { onResult(false, "الرجاء تفعيل GPS", 0f, 0f); return }
        val loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (loc != null) onResult(true, "تم", loc.latitude.toFloat(), loc.longitude.toFloat())
        else onResult(false, "تعذر التحديد", 0f, 0f)
    } catch (e: SecurityException) { onResult(false, "مرفوض", 0f, 0f) }
}

// ==========================================
// 2. قاعدة بيانات الأذكار (كاملة ومترجمة)
// ==========================================
data class StepDhikr(val text: String, val count: Int, val benefit: String = "", val translation: String = "")

val morningAdhkarList = listOf(
    StepDhikr("أَعُوذُ بِاللهِ مِنْ الشَّيْطَانِ الرَّجِيمِ\nاللّهُ لاَ إِلَـهَ إِلاَّ هُوَ الْحَيُّ الْقَيُّومُ...", 1, "أجير من الجن حتى يمسي", "Ayat al-Kursi: Allah! There is no deity except Him, the Ever-Living..."),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ هُوَ ٱللَّهُ أَحَدٌ، ٱللَّهُ ٱلصَّمَدُ، لَمْ يَلِدْ وَلَمْ يُولَدْ، وَلَمْ يَكُن لَّهُۥ كُفُوًا أَحَدٌ.", 3, "تكفيه من كل شيء", "Surah Al-Ikhlas: Say, He is Allah, [who is] One..."),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ أَعُوذُ بِرَبِّ ٱلْفَلَقِ، مِن شَرِّ مَا خَلَقَ...", 3, "تكفيه من كل شيء", "Surah Al-Falaq: Say, I seek refuge in the Lord of daybreak..."),
    StepDhikr("بِسْمِ اللهِ الرَّحْمنِ الرَّحِيم\nقُلْ أَعُوذُ بِرَبِّ ٱلنَّاسِ، مَلِكِ ٱلنَّاسِ...", 3, "تكفيه من كل شيء", "Surah An-Nas: Say, I seek refuge in the Lord of mankind..."),
    StepDhikr("أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لا إِلَهَ إِلا اللَّهُ وَحْدَهُ لا شَرِيكَ لَهُ...", 1, "سؤال خير اليوم والتعوذ من شره", "We have reached the morning and at this very time unto Allah belongs all sovereignty..."),
    StepDhikr("اللّهُـمَّ أَنْتَ رَبِّـي لا إِلهَ إِلاّ أَنْتَ، خَلَقْتَنـي وَأَنا عَبْـدُك...", 1, "سيد الاستغفار - من قاله موقناً فمات دخل الجنة", "O Allah, You are my Lord, none has the right to be worshipped except You..."),
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
    StepDhikr("أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لا إِلَهَ إِلا اللَّهُ وَحْدَهُ لا شَرِيكَ لَهُ...", 1, "سؤال خير الليلة والتعوذ من شرها", "We have reached the evening and at this very time unto Allah belongs all sovereignty..."),
    StepDhikr("اللّهُـمَّ أَنْتَ رَبِّـي لا إِلهَ إِلاّ أَنْتَ، خَلَقْتَنـي وَأَنا عَبْـدُك...", 1, "سيد الاستغفار - من قاله موقناً فمات دخل الجنة", "O Allah, You are my Lord, none has the right to be worshipped except You..."),
    StepDhikr("أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.", 3, "لم يضره شيء في تلك الليلة", "I seek refuge in the Perfect Words of Allah from the evil of what He has created."),
    StepDhikr("رَضِيتُ بِاللَّهِ رَبّاً، وَبِالْإِسْلَامِ دِيناً، وَبِمُحَمَّدٍ صلى الله عليه وسلم نَبِيّاً.", 3, "كان حقاً على الله أن يرضيه", "I am pleased with Allah as my Lord, with Islam as my religion..."),
    StepDhikr("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ.", 3, "لم يضره شيء", "In the Name of Allah, with Whose Name nothing can cause harm..."),
    StepDhikr("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ.", 100, "حُطّت خطاياه وإن كانت مثل زبد البحر", "Glory is to Allah and praise is to Him.")
)

val hisnAlMuslimData = mapOf(
    "sleep" to listOf(
        StepDhikr("بِاسْمِكَ رَبِّـي وَضَعْـتُ جَنْـبي، وَبِكَ أَرْفَعُـه...", 1, "الحفظ أثناء النوم", "In Your name my Lord, I lie down..."),
        StepDhikr("اللَّهُمَّ إِنَّكَ خَلَقْتَ نَفْسِي وَأَنْتَ تَوَفَّاهَا...", 1, "تسليم الروح لله", "O Allah, You created my soul...")
    ),
    "wakeup" to listOf(
        StepDhikr("الحَمْـدُ لِلّهِ الّذي أَحْـيانا بَعْـدَ ما أَماتَـنا وَإليه النُّـشور.", 1, "شكر الله على نعمة الحياة", "All praise is to Allah who gave us life..."),
        StepDhikr("لا إلهَ إلاّ اللّهُ وَحْـدَهُ لا شَـريكَ له، لهُ المُلـكُ ولهُ الحَمـد، وهوَ على كلّ شيءٍ قدير.", 1, "توحيد خالص", "None has the right to be worshipped except Allah...")
    ),
    "food" to listOf(
        StepDhikr("بِسْمِ اللَّهِ. (عند البدء)", 1, "البركة في الطعام", "In the name of Allah (Before eating)"),
        StepDhikr("الْحَمْدُ لِلَّهِ الَّذِي أَطْعَمَنِي هَذَا وَرَزَقَنِيهِ مِنْ غَيْرِ حَوْلٍ مِنِّي وَلَا قُوَّةٍ. (عند الانتهاء)", 1, "غفران ما تقدم من الذنب", "Praise be to Allah who fed me this... (After eating)")
    ),
    "travel" to listOf(
        StepDhikr("سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ * وَإِنَّا إِلَى رَبِّنَا لَمُنْقَلِبُونَ.", 1, "دعاء الركوب", "Glory to Him who has subjected this to us..."),
        StepDhikr("اللَّهُمَّ إِنَّا نَسْأَلُكَ فِي سَفَرِنَا هَذَا الْبِرَّ وَالتَّقْوَى...", 1, "دعاء السفر", "O Allah, we ask You on this journey for righteousness...")
    ),
    "home" to listOf(
        StepDhikr("بِسْـمِ اللهِ وَلَجْنـا، وَبِسْـمِ اللهِ خَـرَجْنـا، وَعَلـى رَبِّنـا تَوَكّّلْـنا. (الدخول)", 1, "السلامة في المنزل", "In the name of Allah we enter..."),
        StepDhikr("بِسْمِ اللَّهِ، تَوَكَّلْتُ عَلَى اللَّهِ، وَلَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ. (الخروج)", 1, "كُفيت ووُقيت وهُديت", "In the name of Allah, I place my trust in Allah...")
    ),
    "mosque" to listOf(
        StepDhikr("اللَّهُمَّ افْتَحْ لِي أَبْوَابَ رَحْمَتِكَ. (الدخول)", 1, "طلب الرحمة", "O Allah, open the doors of Your mercy for me."),
        StepDhikr("اللَّهُمَّ إِنِّي أَسْأَلُكَ مِنْ فَضْلِكَ. (الخروج)", 1, "طلب الفضل والرزق", "O Allah, I ask You from Your bounty.")
    ),
    "toilet" to listOf(
        StepDhikr("بِسْمِ الله، اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْخُبْثِ وَالْخَبَائِثِ. (الدخول)", 1, "الوقاية من الشياطين", "In the name of Allah, O Allah I seek refuge in You from evil..."),
        StepDhikr("غُفْرَانَكَ. (الخروج)", 1, "طلب المغفرة", "I ask You for forgiveness.")
    ),
    "rain" to listOf(
        StepDhikr("اللَّهُمَّ صَيِّباً نَافِعاً.", 1, "عند نزول المطر", "O Allah, (bring) beneficial rain cloud."),
        StepDhikr("مُطِرْنَا بِفَضْلِ اللَّهِ وَرَحْمَتِهِ.", 1, "بعد نزول المطر", "It has rained by the bounty of Allah and His mercy.")
    )
)
