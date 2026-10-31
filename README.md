# Smart Word

تطبيق أندرويد بسيط وسريع لفتح وقراءة وتعديل وإنشاء ملفات Microsoft Word بصيغة DOCX، بواجهة عربية تدعم الكتابة من اليمين إلى اليسار (RTL).

## الحالة

المرحلة 1 (محرك DOCX) مكتملة — انظر [docs/ROADMAP.md](docs/ROADMAP.md).

## المواصفات

| البند | القيمة |
|---|---|
| اللغة | Kotlin |
| الواجهة | Jetpack Compose + Material 3 |
| المعمارية | MVVM |
| الحد الأدنى للنظام | Android 8.0 (API 26) |
| اسم الحزمة | `com.smartword.app` |
| اللغة الأساسية | العربية (RTL)، مع إمكانية إضافة الإنجليزية لاحقًا |

## هيكل المشروع

```
app/            تطبيق أندرويد (الواجهة والمحرر)
core/docx/      محرك DOCX (Kotlin/JVM نقي، قابل للاختبار بدون جهاز)
docs/           الوثائق وخطة العمل
.github/        البناء والاختبار التلقائي (CI)
```

## البناء

يتطلب JDK 17 وAndroid SDK (أو Android Studio).

```
gradle testDebugUnitTest :core:docx:test
gradle assembleDebug
```

عند فتح المشروع في Android Studio يُنشأ Gradle Wrapper تلقائيًا. كما يبني GitHub Actions ملف APK تجريبيًا عند كل دفع.

## نهج التعامل مع DOCX

يُعامل الملف كأرشيف ZIP يحوي أجزاء XML. يعدّل المحرك الأجزاء التي يفهمها فقط، ويعيد كتابة الباقي (صور، جداول، رؤوس، إلخ) كما هو، حفاظًا على تنسيق المستند الأصلي.
