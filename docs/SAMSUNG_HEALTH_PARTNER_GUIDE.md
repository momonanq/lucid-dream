# Доступ к Samsung Health Sensor SDK

Документ описывает два разных пути к сенсорам Galaxy Watch — **developer mode** (доступен сразу,
для разработки и валидации) и **Samsung Partner Program** (нужен для распространения) — и содержит
готовые формулировки для заявки.

> Проверено 2026-09-04 по официальной документации Samsung. Ссылки и требования меняются —
> сверяйтесь с первоисточником перед подачей.

---

## 0. Короткий ответ

|  | Developer mode | Partner Program |
|---|---|---|
| Что даёт | Полный доступ к IBI и PPG на **вашем** устройстве | То же самое для **всех** пользователей |
| Нужен для | Разработки, отладки, **сбора данных для валидации** | Публикации в Google Play / Galaxy Store |
| Как получить | Включается на часах, заявка не нужна | Заявка и одобрение Samsung |
| Ограничение | Работает только при включённом режиме разработчика; не для пользователей | — |

**Валидация алгоритма не заблокирована партнёрской заявкой.** Developer mode даёт настоящие IBI
и PPG уже сегодня — этого достаточно, чтобы собрать ночи на своих часах, сравнить confidence-скоры
со стадиями из Samsung Health и перекалибровать веса. Заявка нужна, чтобы **раздавать** приложение,
а не чтобы его проверить.

---

## 1. Зачем вообще нужен Samsung SDK

Стандартный Android `SensorManager` на Wear OS (`Sensor.TYPE_HEART_RATE`) отдаёт усреднённый
пульс в BPM. Он **не даёт межпульсовых интервалов**, а без них нельзя посчитать ни RMSSD, ни SDNN,
то есть компонент `hrv_score` (20% веса в `RemConfidenceEngine`) остаётся без входных данных.

Samsung Health Sensor SDK даёт:

1. **Непрерывный поток IBI (Inter-Beat Interval)** с миллисекундной точностью;
2. **Высокочастотный PPG** через `TrackerType.HEART_RATE_CONTINUOUS`.

Доступ ограничен системной службой `com.samsung.android.service.health.sensor` и активируется
для приложений с одобренной подписью (SHA-256) — либо, в developer mode, без подписи вовсе.

Поддерживаются **Galaxy Watch4 и новее** на Wear OS powered by Samsung. Pixel Watch, TicWatch и
прочие Wear OS-часы этот SDK не поддерживают в принципе.

> [!NOTE]
> Без этого SDK приложение работает, но в ослабленном режиме: `AndroidStandardSensorDataSource`
> не отдаёт IBI вовсе (раньше он их синтезировал — баг #10), поэтому `hrv_score` исключается из
> скоринга, а оставшиеся веса перенормируются. Движение и время ночи продолжают работать.

---

## 2. Путь 1 — Developer mode (доступен сразу)

Официальная инструкция: <https://developer.samsung.com/health/sensor/guide/developer-mode.html>

1. Скачайте SDK со страницы <https://developer.samsung.com/health/sensor/process.html>
   (на 2026-09-04 — **Samsung Health Sensor SDK v1.4.1**, ~70 КБ). Регистрация в Samsung
   Developer Portal бесплатна, партнёрская заявка не требуется.
2. Подключите AAR в `wearApp/libs/` и добавьте зависимость в `wearApp/build.gradle.kts`.
3. Включите developer mode службы Health Sensor Service на часах по инструкции Samsung.
4. Пересоберите `wearApp`. Сборочный скрипт сам подхватит AAR и подключит `src/samsung/kotlin`
   с реализацией `SamsungHealthSensorDataSource`; без AAR компилируется заглушка `src/noSamsung`.

**Нужны физические часы.** Из документации: «A Galaxy Watch is also needed to develop and run the
app». Эмулятор Wear OS этот SDK не поддерживает.

Ограничение прямо из документации: режим предназначен **только для тестирования и отладки**,
приложение работает лишь пока режим включён, и это не путь для конечных пользователей.

> [!CAUTION]
> Samsung отдельно требует: *«Do not share the developer mode guide with app users»*. В этом
> репозитории — и вообще в любом публичном месте — можно **ссылаться** на инструкцию Samsung, но
> нельзя копировать её шаги. Репозиторий публичный, так что это не формальность.

Для сбора пилотных данных по [PILOT_STUDY_PROTOCOL.md](PILOT_STUDY_PROTOCOL.md) этого достаточно,
если участники — вы и коллеги с собственными часами.

---

## 3. Путь 2 — Partner Program (нужен для распространения)

**Форма заявки:**
<https://developer.samsung.com/SHealth/business-partner/m48wzh9rwz606k0h>

Подавать нужно **до** начала распространения приложения. После одобрения package name и
SHA-256 регистрируются в системе Samsung Health.

> [!NOTE]
> На форуме разработчиков Samsung встречалось сообщение о том, что приём заявок в Partner Apps
> Program временно приостановлен на время обновления программы, а желающим предлагалось оставить
> email через support request, чтобы получить уведомление о возобновлении. Дату этого сообщения
> подтвердить не удалось — страница форума не читается без JavaScript. **Проверьте статус на самой
> форме перед подачей**; если приём закрыт, оставьте заявку через support и работайте в developer
> mode.

### Чек-лист перед подачей

- [ ] Аккаунт на [Samsung Developer Portal](https://developer.samsung.com/)
- [ ] Релизный keystore приложения часов (в проекте его пока нет — нужно создать)
- [ ] Отпечаток SHA-256:
  ```bash
  keytool -list -v -keystore lucid-release-key.jks -alias lucid_wear_alias
  ```
- [ ] Package name часов: `com.luciddream`
      (это `applicationId`, а не namespace — он общий у телефона и часов, иначе Wearable Data
      Layer не соединит их; именно его регистрирует Samsung вместе с SHA-256)
- [ ] Прочитано [лицензионное соглашение](https://developer.samsung.com/health/sensor/sdk-license-partner-service-agreement.html)

Срок рассмотрения Samsung публично не декларирует — планируйте недели, а не дни, и не ставьте
релиз в зависимость от конкретной даты.

---

## 4. Формулировки для заявки

### App Name & Overview
- **Application Name**: Lucid Dream Companion
- **Category**: Health & Fitness / Sleep Science & Circadian Wellness
- **Target Devices**: Galaxy Watch 4 и новее (Wear OS powered by Samsung)

### Detailed Purpose of Use

> Our application provides nocturnal circadian rhythm monitoring and probabilistic REM-like state
> estimation to help users practise lucid dreaming through cognitive protocols (TLR, MILD, WBTB).
> To estimate the physiological signature of REM sleep — motor quiescence combined with phasic
> autonomic variability — we require millisecond-accurate Inter-Beat Intervals from
> `TrackerType.HEART_RATE_CONTINUOUS` together with synchronous 3-axis accelerometer data.
> Standard Android heart rate metrics provide only low-frequency averages, which cannot support
> time-domain heart rate variability analysis (RMSSD, SDNN).
>
> The application does not claim clinical sleep staging. Published evaluations of Galaxy Watch
> sleep staging report moderate agreement with polysomnography, so our confidence score is used
> as an operational threshold for delivering a gentle cue, never as a medical determination of
> sleep stage, and this is stated to users in the product itself.

### Requested Privileges
- `com.samsung.health.sensor.heart_rate_continuous` — continuous PPG and inter-beat intervals
- `com.samsung.health.sensor.accelerometer` — motor quiescence during sleep

### Data Privacy & Storage

> All raw biometric readings (HR, IBI, accelerometer) are processed entirely on-device in real
> time. No raw PPG waveforms or biometric time series are uploaded to any external server. Sensor
> samples are aggregated locally into 60-second windows and stored only in a local Room SQLite
> database on the user's own devices. There is no cloud sync, no analytics SDK, and no third-party
> data sharing.

### Safety & Medical Disclaimer

> The application is consumer wellness software and states plainly that it is not a medical device,
> does not diagnose sleep stages clinically, and does not treat sleep disorders.
>
> Because the product delivers sensory stimuli during sleep, it enforces explicit safety limits in
> code rather than in policy text: an onboarding screening withholds all nocturnal cues from users
> under 18 or reporting sleep apnea, chronic insomnia, parasomnia, narcolepsy, seizure disorders or
> treatment for a psychotic disorder; exposure is capped at three cue nights per rolling week with a
> mandatory rest night between them; and cueing stops automatically after two consecutive nights in
> which cues woke the user, or when self-reported sleep quality declines. When any limit trips, the
> night runs without cues rather than the app blocking access.

---

## 5. После одобрения

Samsung добавляет package name и SHA-256 в белый список `HealthTrackingService`.

В проекте нужно:

1. Зарегистрировать в форме `applicationId` (`com.luciddream`) и SHA-256 релизной подписи.
2. Подписать сборку тем же ключом и проверить, что SDK работает **без** developer mode.

> [!TIP]
> Индикатор на `WatchReadyScreen` отражает фактическую доставку данных, а не выбор источника:
> при `SDK_POLICY_ERROR` он переключается на «● Standard Wear OS HR» в течение ~5 секунд после
> старта трекинга. Если после включения developer mode он там и остался — политика доступа всё
> ещё режет данные; смотрите `adb logcat -s SamsungSensorSource`.

### Проверено на Galaxy Watch Ultra (SM-L705F, Android 16), 2026-09-05

Без developer mode и без партнёрского одобрения цепочка проходит до конца и упирается в политику:

```
HealthTrackingConnector: Tracker Service Connected with appID: com.luciddream
HealthTrackingService: getHealthTacker of type HEART_RATE_CONTINUOUS called
SHS#WearTrackerService: SetListener of type HEART_RATE and appID com.luciddream called
E SamsungSensorSource: Heart rate tracker error: SDK_POLICY_ERROR
```

То есть привязка службы, наличие трекера в `supportedList` и установка слушателя ещё ничего не
говорят о доступе к данным. Признак успеха — появление `onDataReceived`, а не `Connected`.

---

## Источники

- [Samsung Health Sensor SDK](https://developer.samsung.com/health/sensor)
- [App creation process](https://developer.samsung.com/health/sensor/process.html)
- [Developer mode of Health Sensor Service](https://developer.samsung.com/health/sensor/guide/developer-mode.html)
- [Partner service & SDK license agreement](https://developer.samsung.com/health/sensor/sdk-license-partner-service-agreement.html)
- [FAQ](https://developer.samsung.com/health/sensor/faq.html)
