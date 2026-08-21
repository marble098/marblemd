# MarbleMD

MarbleMD is a native Android Markdown reader focused on correct multilingual typography and bidirectional text.

## ویژگی‌ها

- خواندن فایل‌های `.md` / Markdown با Android Storage Access Framework؛ بدون نیاز به مجوز Storage قدیمی.
- سه حالت جهت متن: **Auto / RTL / LTR**.
- در حالت Auto از `FIRST_STRONG` + `TEXT_ALIGNMENT_TEXT_START` استفاده می‌شود تا هر پاراگراف بر اساس جهت واقعی خودش چیده شود.
- اعمال **Vazirmatn** روی اسکریپت Arabic (فارسی، عربی، اردو، کردی و...) و **Noto Sans** (Google) روی Latin/Greek/Cyrillic؛ سایر اسکریپت‌ها از fallback چندزبانه خود Android استفاده می‌کنند.
- نمایش درست متن‌های ترکیبی فارسی/انگلیسی بر پایه الگوریتم Unicode Bidirectional خود Android.
- تغییر اندازه متن بین 12sp و 34sp.
- CommonMark + GFM tables + task lists + strikethrough + HTML + links + remote images + code blocks + quotes + lists.
- انتخاب و کپی متن، لینک‌های قابل کلیک، high-quality line breaking.
- Material 3 + Jetpack Compose؛ AndroidView فقط برای TextView/Markwon استفاده شده تا Bidi و Markdown spanهای Android با حداکثر کیفیت حفظ شوند.
- Dark/Light خودکار بر اساس سیستم.
- GitHub Actions: test + lint + signed release APK + SHA256.
- `compileSdk/targetSdk 37`, AGP 9.3.0, Gradle 9.5.0, Kotlin/Compose Compiler 2.4.10, Compose BOM 2026.08.00.

## فونت‌ها

فایل‌های فونت عمداً داخل سورس commit نمی‌شوند. هنگام build، task `fetchFonts` نسخه‌های OFL زیر را مستقیماً دریافت و داخل assets قرار می‌دهد:

- Vazirmatn variable TTF
- Google Noto Sans variable TTF

اگر شبکه در زمان build قطع باشد، می‌توانید این دو فایل را دستی در `app/src/main/assets/fonts/` با نام‌های `Vazirmatn.ttf` و `NotoSans.ttf` قرار دهید.

## Build

```bash
gradle --no-daemon testDebugUnitTest lintDebug assembleRelease
```

GitHub Actions همین فرایند را خودکار انجام می‌دهد و APKها را به‌عنوان Artifact منتشر می‌کند.

## معماری‌ها

MarbleMD هیچ native `.so` ندارد، بنابراین APK universal روی `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86` و معماری‌های سازگار بدون تفاوت کد ماشین اجرا می‌شود. Workflow علاوه بر universal، خروجی‌های ABI-labelled را هم در Artifact قرار می‌دهد تا توزیع برای هر معماری ساده باشد.

## انتشار بدون clone

فایل `publish-marblemd.sh` که همراه ZIP ارائه شده، PAT را به‌صورت مخفی می‌گیرد، ریپو `marblemd` را در حساب GitHub احراز هویت‌شده می‌سازد و کل tree سورس را با Git Data API در یک commit روی `main` قرار می‌دهد؛ هیچ `git clone` انجام نمی‌شود.

## License

MIT. Font files are fetched from their upstream OFL-licensed projects during build and are not vendored in this source archive.
