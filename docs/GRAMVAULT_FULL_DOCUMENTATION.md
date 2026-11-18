# مستند جامع فنی، معماری لایه‌ها، فازهای انجام‌شده و نقشه راه باقی‌مانده — **GramVault Wallet**

این سند گزارش فنی کامل وضعیت فعلی پروژه **GramVault** (`applicationId = "com.gramvault.wallet"`، `namespace = "com.gramvault.wallet"`) است که بر اساس کدهای واقعی موجود در مخزن نوشته شده است. تمام نام کلاس‌ها، جداول دیتابیس، پروتکل‌ها، مسیرهای شبکه و محدودیت‌ها دقیقاً منطبق با پیاده‌سازی فعلی پروژه هستند.

---

## ۱. مشخصات پلتفرم و استک فنی

- **پلتفرم و زبان:** Android (Kotlin `2.2.10`, Java 17 Toolchain) + Jetpack Compose (Material Design 3)
- **تنظیمات SDK:** `minSdk = 26` (Android 8.0) | `targetSdk = 36` | `compileSdk = 36.1`
- **معماری کلی:** MVVM + Clean Core Modularization (بدون نیاز به سرور واسط اختصاصی / Zero-Server Architecture)
- **کتابخانه‌های کلیدی:**
  - **رمزنگاری و TON Core:** `org.ton.kotlin` نسخه `0.5.0` (`ton-kotlin-block-tlb`, `ton-kotlin-crypto`, `ton-kotlin-tvm`) برای Ed25519، سریال‌سازی Cell/BOC و کدک‌های TL-B
  - **امنیت سخت‌افزاری و احراز هویت:** `androidx.biometric:biometric:1.1.0` + Android Keystore (`AES-256-GCM`)
  - **پایداری داده‌های محلی:** `androidx.room:room-runtime:2.7.0` + `room-ktx` + KSP (`GramVaultDatabase` نسخه ۵) + `androidx.datastore:datastore-preferences:1.1.7`
  - **شبکه و سریال‌سازی:** `OkHttp 4.12.0` + `Moshi 1.15.2` (جریانی بدون نیاز به پروکسی)
  - **رابط کاربری و تصاویر:** `Compose BOM 2024.09.00` + `Coil Compose 2.7.0` + `ZXing Core 3.5.4` (تولید QR کد)
  - **مدیریت کلیدها:** `secrets-gradle-plugin:2.0.1` خواندن از `.env` / `.env.example` به `BuildConfig`
- **مجوزها (`AndroidManifest.xml`):** فقط `INTERNET` و `USE_BIOMETRIC`
- **ممنوعیت بکاپ سیستم:** `android:allowBackup="false"` به همراه مسدودسازی کامل کل دامنه‌ها در `backup_rules.xml` و `data_extraction_rules.xml` برای جلوگیری از خروج سید یا دیتابیس از دستگاه.

---

## ۲. نقشه لایه‌ها و ساختار پکیج‌ها

ساختار سورس‌کد زیر پکیج اصلی `app/src/main/java/com/example/` به‌صورت کاملاً ماژولار طراحی شده است:

### الف) لایه ارائه و رابط کاربری (`ui/` + `MainActivity.kt` + `WalletViewModel.kt`)
1. **مدیریت وضعیت و ناوبری:**
   - `WalletViewModel.kt`: نگهداری `AppUiState`، چرخه حیات، مانیتورینگ زنده در پیش‌زمینه (Foreground)، مدیریت خطاهای کاربرپسند و هماهنگی با لایه هسته.
   - `MainActivity.kt`: میزبان Compose با `enableEdgeToEdge()`، انیمیشن انتقال صفحات بر اساس عمق (`RouteKey.depth`)، و ناظر `Lifecycle.Event.ON_START` / `ON_STOP` برای قفل خودکار و پایش زنده.
2. **سیستم طراحی و تم (`ui/theme/`):**
   - `Color.kt`, `Theme.kt`, `Type.kt`: پالت تاریک الهام‌گرفته از Tonkeeper (`VaultDarkBackground = #10161F`, `VaultDarkCard = #1D2633`, `VaultBlue = #45AEF5`, `UsdtGreen = #39CC83`, `TonkeeperAmber = #F5A623`, `TonkeeperRed = #FF4766`) همراه با فونت‌های محلی `Plus Jakarta Sans` و `JetBrains Mono`.
3. **کامپوننت‌های مشترک (`ui/components/`):**
   - `IosSheetHost.kt`: کانتینر مودال/باتم‌شیت نرم با فیزیک فنری.
   - `MotionAndWidgets.kt`: دکمه‌های دایره‌ای اکشن، کارت شیشه‌ای `VaultGlassCard`، افکت لمس `tactileClickable`، انیمیشن تغییر موجودی `AnimatedBalanceText` و نوار اکشن پایین `BottomThumbActionBar`.
   - `JettonAvatar.kt`: رندر لوگوی رسمی TON (`TonOfficialLogo` بر پایه وکتور `R.drawable.ic_ton_official` + لود تصویر از `ton.org` یا `STON.fi`) و آواتار جتون‌ها (`JettonAvatar`) به همراه نشان تأیید (`JettonVerificationInlineBadge`).
   - `QrCodeView.kt`: تولید QR کد دریافت برای آدرس و لینک `ton://transfer/...`.
   - `SecureClipboard.kt` و `SecureScreenEffect.kt`: پاک‌سازی خودکار کلیپ‌بورد حساس و جلوگیری از اسکرین‌شات در صفحات سید/بکاپ (`FLAG_SECURE`).
4. **صفحات (`ui/screens/`):**
   - `SetupScreens.kt`: خوش‌آمدگویی، نمایش عبارت بازیابی، چالش تأیید بکاپ ۳ کلمه‌ای، صفحه قفل، و بازنشانی خطای باطل شدن کلید سخت‌افزاری.
   - `ImportWalletScreen.kt`: ایمپورت ۱۲ یا ۲۴ کلمه‌ای و صفحه کشف حساب‌های مبهم ۲۴ کلمه‌ای (`ImportSelectAccountScreen`).
   - `WalletHomeScreen.kt`: هدر انتخاب کیف‌پول، بنر وضعیت شبکه و هشدار اختلاف ساعت، کارت موجودی و اکشن‌های Send / Receive / Buy / Swap، لیست دارایی‌ها (Gram با لوگوی رسمی + جتون‌ها)، لیست تراکنش‌های اخیر و کارت مانیتور زنده.
   - `WalletManagerSheet.kt`: مدیریت چند کیف‌پول (تغییر کیف‌پول فعال، ساخت کیف‌پول جدید، افزودن کیف‌پول Watch-Only، تغییر نام و حذف).
   - `SendScreens.kt`: فرم ارسال (GRAM و هر Jetton موجود)، دفترچه مخاطبین، بررسی آدرس/تگ، پیش‌نمایش کارمزد، و صفحه وضعیت زنده تراکنش.
   - `SwapScreens.kt`: فرم سواپ (`SwapFormScreen`)، انتخابگر توکن با جستجو، تنظیم Slippage (`0.5%`, `1%`, `3%`, `5%`)، جزئیات مسیر و قیمت، و صفحه تأیید و امضای چندپیامی (`SwapConfirmScreen`).
   - `BuySheetContent.kt`: باتم‌شیت خرید فیات با انتخاب دارایی (`TON (GRAM)` و `USDT (TON)`)، پیش‌نمایش آدرس Mainnet، کارت‌های Mercuryo و Onramper و دکمه کپی آدرس.
   - `ReceiveSheetContent.kt`: نمایش QR کد، کپی آدرس غیربانس‌ابل و اشتراک‌گذاری.
   - `AddressBookAndSettings.kt`: دفترچه آدرس‌ها، تاریخچه کامل تراکنش‌ها و تنظیمات امنیتی (تغییر پین، قفل خودکار، تغییر شبکه Mainnet/Testnet، سوییچ بین PIN و بیومتریک).

### ب) لایه دامنه و مدل‌ها (`core/model/`)
- `Network.kt`: تفکیک `MAINNET (v5r1WalletId = 0x7FFFFF11L)` و `TESTNET (0x7FFFFFFDL)`.
- `TonAddress.kt`: پارس و اعتبارسنجی آدرس‌های Raw (`workchain:64hex`) و Friendly با بررسی CRC16-XMODEM، تگ `bounceable` و ایزوله‌سازی اکید Mainnet/Testnet.
- `Money.kt`: محاسبات دقیق عدد صحیح بزرگ (`BigInteger`) بدون خطای ممیز شناور برای TON (۹ رقم اعشار) و توکن‌ها + مدل `BalanceState`.
- `WalletModels.kt`: `MnemonicPhrase` با `CharArray` قابل پاک‌سازی (`wipe()`), `VaultMetadata` (شامل `walletId`, `walletName`, `isWatchOnly`, `walletVersion`, `mnemonicScheme`, `securityMode`), `VaultRegistry` و `VaultState`.
- `JettonModels.kt`: مدل `JettonHolding`، سطوح اعتبارسنجی (`ALLOWLISTED`, `VERIFIED`, `UNVERIFIED`, `BLACKLISTED`)، فیلتر ضداسکم (`JettonAllowList.isLikelyScamMetadata`) و پاک‌سازی URL تصاویر و IPFS (`JettonUrlSanitizer`).
- `SwapModels.kt`: مدل‌های `SlippageTolerance`, `SwapAsset`, `SwapQuote`, `SwapInternalMessage`, `SwapPreview`.
- `TxModels.kt`: وضعیت‌های چرخه حیات تراکنش (`DRAFT`, `SIGNED`, `BROADCASTED`, `CONFIRMED`, `FAILED`, `EXPIRED`, `UNCERTAIN`) و `ChainMonitorState`.

### ج) لایه امنیت و کلیدها (`core/keystore/`)
- `SecureKeyStore.kt`:
  - رمزنگاری دو لایه برای سید ۳۲ بایتی + عبارت بازیابی به صورت فایل‌های باینری اتمیک به ازای هر کیف‌پول (`vault_seed_<walletId>.bin` و فایل legacy `vault_seed.bin`).
  - الگوریتم لایه سخت‌افزاری (`AndroidKeyStoreCipherProvider`): `AES/GCM/NoPadding` با کلید ۲۵۶ بیتی و برچسب احراز هویت ۱۲۸ بیتی.
  - سه حالت امنیتی (`VaultSecurityMode`):
    1. `DEVICE_KEYSTORE`: کلید سخت‌افزاری دارای پنجره ۳۰ ثانیه‌ای متصل به بیومتریک یا قفل دستگاه.
    2. `APP_PIN_WITH_KEYSTORE`: رمزنگاری داخلی با `PBKDF2WithHmacSHA256` (۱۰۰,۰۰۰ تکرار + نمک ۱۶ بایتی تصادفی) + رمزنگاری بیرونی Keystore. دارای محدودیت تلاش نادرست PIN و قفل موقت ضد Brute-Force.
    3. `DEV_UNAUTH_KEYSTORE`: فقط در بیلدهای دیباگ/امولاتور (`devSkipAuth`). در بیلد Release به‌صورت اکید مسدود است.
  - بازرسی سطح سخت‌افزار (`KeySecurityLevel`: StrongBox, TEE, Software).
- `BiometricGate.kt`: پل ارتباطی با `BiometricPrompt` و مدیریت `AUTH_BIOMETRIC_STRONG` / `DEVICE_CREDENTIAL`.

### د) لایه منطق کیف‌پول و تراکنش‌های زنجیره‌ای (`core/wallet/` & `core/tx/`)
- `MnemonicService.kt` و `Bip39Wordlist.kt`: تولید سید ۱۲ کلمه‌ای BIP-39 با چک‌سام SHA-256، پشتیبانی کامل از دو استاندارد **Multichain (`m/44'/607'/0'`)** و **TON Legacy ۲۴ کلمه‌ای** (`TON seed version` + `TON default seed`) و کشف خودکار حساب‌های مبهم.
- `AddressService.kt`: ساخت قطعی `StateInit` و استخراج آدرس برای ۴ نسخه استاندارد قرارداد کیف‌پول TON: **`V5R1`، `V4R2`، `V3R2` و `V3R1`**.
- `TransferBuilder.kt`:
  - ساخت و امضای off-chain با Ed25519 برای ارسال تک‌پیامی و **چندپیامی (Multi-Message تا ۴ پیام داخلی همزمان)** مخصوص سواپ‌های split‌شده.
  - ساخت بدنه استاندارد `TEP-74 (transfer#0f8a7ea5)` برای ارسال تمام جتون‌ها (USDT و غیره) با کارمزد الصاقی `0.055 GRAM`.
  - تولید همزمان `messageHashHex` و `normalizedHashHex` (برای تطبیق دقیق روی Indexerها) و ساختار Dummy Signature برای تخمین کارمزد.
- `TonTransferUri.kt`: پارس و ساخت لینک‌های استاندارد `ton://transfer/...`.
- `BuyOnRampHelper.kt`: ساخت امن لینک‌های پرداخت Mercuryo و Onramper فقط برای Mainnet با بررسی placeholder نبودن کلیدهای `BuildConfig`.
- `VaultMetadataStore.kt`: ذخیره و مدیریت رجیستری چند کیف‌پول در `vault_registry.json` به همراه پشتیبانی از مهاجرت تک‌کیف‌پول قدیمی (`vault_meta.json`).
- `TxManager.kt`: مدیریت صف ارسال با قفل `txMutex`، ذخیره اتمیک تراکنش امضاشده **قبل از ارسال شبکه** (ضد double-spend و ضد crash)، بازپخش خودکار پیام‌های `SIGNED` در خطای موقت شبکه، پیگیری تأییدیه با polling، و همگام‌سازی افزایشی تاریخچه (`syncAccountHistory`) به همراه ترمیم رکوردهای واریزی قدیمی.

### هـ) لایه داده محلی (`core/data/`)
- `GramVaultDatabase.kt` (Room نسخه ۵ با مهاجرت‌های `MIGRATION_3_4` و `MIGRATION_4_5`):
  - `balance_cache`: موجودی TON، وضعیت قرارداد، `lastTxLt` و TTL ۲۵ ثانیه‌ای.
  - `jetton_cache`: کش کامل تمام جتون‌های کیف‌پول (آدرس کانترکت مستر، جتون والت، نماد، نام، اعشار، URL لوگو، وضعیت تأیید و مقدار).
  - `tx`: دفترکل کامل تراکنش‌های ارسالی و دریافتی (GRAM و Jetton) با ایندکس روی حساب، شبکه، وضعیت و seqno.
  - `address_book`: دفترچه مخاطبین تاییدشده با ایندکس یکتا به ازای `(network, rawAddress)`.
- `BalanceRepository.kt` و `AddressBookRepository.kt`: ترکیب جریان‌های Room با وضعیت شبکه برای نمایش آفلاین بدون چشمک زدن.

### و) لایه شبکه (`core/network/`)
- `ProviderRouter.kt` و `ProviderRateLimiter.kt`: روتر Failover بدون سرور واسط (TON Center به‌عنوان مسیر اصلی با فاصله ۱,۱۵۰ میلی‌ثانیه و TonAPI به‌عنوان پشتیبان با فاصله ۴,۲۵۰ میلی‌ثانیه)، Circuit Breaker و Backoff نمایی.
- `TonCenterClient.kt`: ارتباط با TON Center v3 و v2 + شبیه‌سازی Trace (`emulateTrace`).
- `TonApiClient.kt`: ارتباط با TonAPI v2 به عنوان پشتیبان کامل (ترازها، جتون‌ها، تاریخچه، شبیه‌سازی و پخش BOC).
- `ChainTimeOracle.kt`: کالیبراسیون اختلاف ساعت گوشی با هدر `Date` سرورهای TON برای دقیق ماندن `validUntil` تراکنش‌ها.
- `TonCommentDecoder.kt`: دیکدر محلی Cellهای BOC برای استخراج یادداشت‌های متنی (`0x00000000`) حتی در نبود فیلد متنی آماده از Indexer.
- `PriceService.kt`: دریافت نرخ دلاری TON و Jettonها (TonAPI با پشتیبان CoinGecko و کش ۹۰ ثانیه‌ای).
- `SwapService.kt` (`SwapCoffeeService`): اتصال مستقیم به `backend.swap.coffee` (`/v1/route` و `/v2/route/transactions`) برای مسیریابی بین DEXها (STON.fi، DeDust، TONCO و غیره) و ادغام با لیست توکن‌های رسمی `https://api.ston.fi/v1/assets`.

---

## ۳. فازهای انجام‌شده تا امروز (خلاصه مرحله به مرحله)

| فاز | عنوان فاز | دستاوردهای فنی پیاده‌سازی‌شده |
| :--- | :--- | :--- |
| **فاز ۱ و ۲** | هسته رمزنگاری و کیف‌پول TON | پیاده‌سازی BIP-39 + SLIP-0010 (`m/44'/607'/0'`) و TON Legacy ۲۴ کلمه‌ای، استخراج قطعی آدرس‌های `V5R1 / V4R2 / V3R2 / V3R1`، اعتبارسنجی CRC16 و ایزوله‌سازی Mainnet/Testnet. |
| **فاز ۳** | خزانه امن کلید (Vault Security) | ساخت `SecureKeyStore` با Android Keystore AES-256-GCM، حالت‌های بیومتریک و App PIN (با PBKDF2 و قفل ضد Brute-Force)، شناسایی باطل شدن کلید و پاک‌سازی حافظه (`CharArray`). |
| **فاز ۴** | شبکه بدون سرور و تخمین کارمزد | کلاینت‌های مستقیم `TonCenterClient` و `TonApiClient`، ریت‌لیمیت و Circuit Breaker در `ProviderRouter`، تخمین کارمزد با Trace Emulation، دیکد کامنت BOC و همگام‌سازی ساعت شبکه (`ChainTimeOracle`). |
| **فاز ۵ و ۶** | پایپ‌لاین تراکنش و بازیابی خرابی | `TransferBuilder` برای امضای off-chain، قاعده پویا `bounce` برای آدرس‌های `UNINIT`، و `TxManager` با ذخیره اتمیک پیش از ارسال در Room (`SIGNED -> BROADCASTED -> CONFIRMED`) و همگام‌سازی افزایشی تاریخچه. |
| **فاز ۷ و ۸** | پشتیبانی جتون‌ها و امنیت ضداسکم | پشتیبانی کامل قراردادهای **TEP-74** (از جمله USDT رسمی و تمام جتون‌های تاییدشده)، جدول `jetton_cache`، فیلتر نام‌ها و لینک‌های مخرب، و بررسی دامنه‌دار URL تصاویر/IPFS. |
| **فاز ۹** | چند کیف‌پول و حالت Watch-Only | پشتیبانی از رجیستری چند کیف‌پول (`vault_registry.json`)، فایل سید جداگانه به ازای هر کیف‌پول، جابجایی فوری، تغییر نام، حذف و **کیف‌پول فقط-خواندنی (Watch-Only)** بدون نیاز به کلید خصوصی. |
| **فاز ۱۰** | طراحی Tonkeeper-Style و انیمیشن‌ها | بازطراحی کامل UI با تم تاریک مدرن، فونت‌های اختصاصی، شیت‌های فنری (`IosSheetHost`)، انیمیشن اعداد، Pull-to-Refresh، صفحه امنیت و لوگوی اختصاصی برنامه (`ic_launcher`). |
| **فاز ۱۱** | پیاده‌سازی Swap (DEX Aggregator) | اتصال به `swap.coffee` و `STON.fi`، پشتیبانی از تراکنش‌های Split‌شده تا ۴ پیام داخلی در `TransferBuilder.buildSignedMultiTransfer`، تخمین کارمزد واقعی، تنظیم Slippage و صفحات کامل `SwapFormScreen` و `SwapConfirmScreen`. |
| **فاز ۱۲** | پیاده‌سازی Buy (Fiat On-Ramp) | راه‌اندازی `BuyOnRampHelper` و `BuySheetContent` برای خرید TON و USDT روی Mainnet از طریق **Mercuryo** و **Onramper** به همراه کپی مستقیم آدرس و مدیریت کلیدهای پارتنر در `.env`. |
| **فاز ۱۳** | لوگوی رسمی TON / Gram | جایگزینی کامل آیکون‌های الماس پیش‌فرض با وکتور رسمی برند TON (`ic_ton_official.xml` با رنگ `#0098EA`) و کامپوننت `TonOfficialLogo` با بارگذاری تصویر رسمی در صفحات خانه، سواپ و ارسال. |

---

## ۴. وضعیت تست‌ها و کیفیت کد

در مخزن **`app/src/test/java/com/example/`** مجموعاً **۱۱ فایل تست و ۱۱۲ تست خودکار (Unit + Robolectric)** وجود دارد که همگی اجرا و تأیید شده‌اند (`gradle :app:testDebugUnitTest`):

| فایل تست | تعداد تست | حوزه پوشش |
| :--- | :---: | :--- |
| `ExampleUnitTest.kt` | ۳۱ | BIP-39، SLIP-0010، TON Legacy، StateInit نسخه V5R1..V3R1، امضای Ed25519، آدرس‌ها، URI و Failover شبکه |
| `ExampleRobolectricTest.kt` | ۴۳ | Keystore، PIN، کش Room، قفل چرخه حیات TxManager، بازیابی خطا، ارسال USDT، دفترچه آدرس و رندر Compose |
| `SwapAndBuyUnitTest.kt` | ۶ | پارس مسیرها و تراکنش‌های `swap.coffee`، لیست `STON.fi`، ساخت پیام‌های چندتایی BOC، لینک‌های Buy و لوگوی رسمی |
| `SwapAndBuyRobolectricTest.kt` | ۵ | جریان کامل Swap و Buy در UI، انتخاب جفت‌ارز و رفتار دکمه‌های خرید در نبود/وجود کلید |
| `MultiJettonUnitTest.kt` و `MultiJettonRobolectricTest.kt` | ۱۰ | کش چندجتون (v5)، فیلتر اسکم و نمایش دارایی‌های متعدد در UI |
| `MultiWalletRobolectricTest.kt` | ۳ | ساخت، سوییچ بین چند کیف‌پول و محدودیت‌های کیف‌پول Watch-Only |
| `SecurityAndIosSheetRobolectricTest.kt` | ۳ | قفل Brute-Force پین، مدیریت سشن و رندر باتم‌شیت‌های iOS-like |
| `Phase7And8UnitTest.kt` | ۴ | کدک کامل TEP-74، `JettonAllowList` و نرخ قیمت `PriceService` |
| `StressAndVectorsUnitTest.kt` و `StressAndRecoveryRobolectricTest.kt` | ۷ | تست‌های استرس همزمانی، وکتورهای مرزی رمزنگاری و بازیابی وضعیت |

> **نکته:** به دلیل نبود امولاتور زنده Android/ADB در محیط ابری ساخت، تست‌های Instrumentation (`androidTest`) اجرا نشده‌اند و کلیه تست‌های UI و دیتابیس از طریق Robolectric + Unit Test روی JVM اجرا گردیده‌اند.

---

## ۵. موارد باقی‌مانده، کلیدهای مورد نیاز و نقشه راه آینده

### الف) تنظیمات لازم برای فعال‌سازی ۱۰۰٪ در پروداکشن (Secrets)
در حال حاضر کلیدهای زیر در `.env.example` با مقدار نمونه (`YOUR_*`) تعریف شده‌اند و توسط کد به‌درستی به‌عنوان «پیکربندی‌نشده» تشخیص داده می‌شوند. برای فعال‌سازی واقعی باید در بخش **Secrets** تعریف شوند:
1. **`MERCURYO_WIDGET_ID`:** برای فعال‌سازی دکمه خرید کارت بانکی/فیات از طریق Mercuryo در صفحه `Buy`.
2. **`ONRAMPER_API_KEY`:** برای فعال‌سازی درگاه تجمیع‌کننده Onramper در صفحه `Buy`.
3. **`SWAP_COFFEE_API_KEY` و `SWAP_COFFEE_REFERRAL_NAME`** *(اختیاری)*: سرویس Swap هم‌اکنون بدون کلید هم کار می‌کند، اما برای دریافت سهم کارمزد پارتنر (مدل درآمدی) و سقف درخواست بالاتر قابل تنظیم است.
4. *(توصیه‌شده برای ترافیک بالا)* استفاده از API Key اختصاصی برای TON Center / TonAPI به‌جای tier عمومی بدون کلید.

### ب) قابلیت‌های باقی‌مانده برای فازهای بعدی (Roadmap)
1. **پشتیبانی از TON Connect v2 و اتصال به dAppها:**
   - امضای اتصال به صرافی‌ها و مارکت‌های اکوسیستم TON (مثل Fragment، Getgems، STON.fi) از طریق لینک و QR کد.
2. **اسکنر زنده دوربین QR (CameraX):**
   - پکیج‌های CameraX در `app/build.gradle.kts` به‌صورت آماده (کامنت‌شده) وجود دارند، اما رابط دوربین زنده برای اسکن سریع آدرس، `ton://transfer/...` و لینک‌های TON Connect هنوز پیاده‌سازی نشده است.
3. **بخش NFTها و دامنه‌های `.ton` (TEP-62 / TEP-64):**
   - نمایش کلکسیون‌های NFT، بررسی اصالت و انتقال آن‌ها در صفحه جداگانه یا تب Collectibles.
4. **استیکینگ (Staking):**
   - یکپارچه‌سازی با استخرهای استیکینگ TON (مانند Tonstakers یا استخرهای Nominator) جهت کسب سود سالانه توسط کاربر و کارمزد سرویس برای پلتفرم.
5. **افزودن MoonPay به بخش Buy (در صورت نیاز):**
   - به‌دلیل الزام MoonPay به امضای سروری URL با HMAC-SHA256 (`signature` parameter) و کلید خصوصی، پیاده‌سازی امن آن نیازمند افزودن یک سرویس امضای سبک سمت سرور (مثلاً Cloud Function) است تا کلید مخفی درون APK لو نرود.
6. **نوتیفیکیشن پس‌زمینه (Push Notifications):**
   - پایش تراکنش‌های ورودی فعلی فقط در **پیش‌زمینه (Foreground)** هر ۱۰ ثانیه اجرا می‌شود. ارسال نوتیفیکیشن هنگام بسته بودن کامل برنامه نیازمند یک Indexer/Webhook سبک سمت سرور و اتصال به FCM است.
7. **آزمون نهایی روی دستگاه واقعی و آماده‌سازی انتشار Google Play:**
   - تست بیومتریک و StrongBox روی سخت‌افزار واقعی گوشی، بررسی تراکنش واقعی Mainnet با مبالغ خرد و تنظیم کلید امضای انتشار (`release` keystore).
