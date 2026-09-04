# Гайд по подаче партнерской заявки на Samsung Health Sensor SDK

Данный документ содержит пошаговую инструкцию и шаблоны формуляров для получения официального партнерского доступа (**Samsung Partner Privilege**) к **Samsung Health Sensor SDK** на Galaxy Watch (Wear OS 4 / 5).

---

## 1. Зачем требуется партнерская заявка?

По умолчанию на Wear OS стандартный Android `SensorManager` (`Sensor.TYPE_HEART_RATE`) отдаёт усредненный пульс (BPM) с задержкой в несколько секунд. 

Для предиктивного определения фазы быстрого сна (REM-сна) нашему алгоритму требуется:
1. **Непрерывный поток сырых межпульсовых интервалов (IBI — Inter-Beat Interval)** с миллисекундной точностью для вычисления вариабельности ритма сердца (RMSSD, SDNN).
2. **Высокочастотный фотоплетизмографический датчик (PPG)** (`TrackerType.HEART_RATE_CONTINUOUS`).

Samsung ограничивает этот доступ через системную службу `com.samsung.android.service.health.sensor`. Доступ активируется только для приложений с одобренным цифровым сертификатом подписи (SHA-256 fingerprint).

> [!NOTE]
> В приложении уже реализована **Graceful Degradation**: до момента одобрения заявки часы работают на стандартном Android `SensorManager` (`AndroidStandardSensorDataSource`) без сбоев.

---

## 2. Чек-лист перед подачей заявки

- [ ] Создан аккаунт разработчика на [Samsung Developer Portal](https://developer.samsung.com/).
- [ ] Сгенерирован Keystore релизной подписи приложения (`lucid-release-key.jks`).
- [ ] Получен отпечаток SHA-256 ключа подписи:
  ```bash
  keytool -list -v -keystore lucid-release-key.jks -alias lucid_wear_alias
  ```
- [ ] Зафиксирован Package Name приложения часов:
  `com.luciddream.wear`

---

## 3. Пошаговый процесс подачи в Samsung Developer Portal

1. Перейдите в раздел **Samsung Health SDK** $\rightarrow$ **Partnership Request**.
2. Выберите тип SDK: **Samsung Health Sensor SDK for Wear OS**.
3. Заполните форму заявки на английском языке (шаблоны ниже).
4. Укажите Package Name: `com.luciddream.wear`.
5. Вставьте отпечаток SHA-256 вашего сертификата.
6. Отправьте форму на модерацию (срок рассмотрения обычно составляет от 7 до 21 рабочего дня).

---

## 4. Шаблоны ответов для модерации Samsung

### App Name & Overview
- **Application Name**: Lucid Dream Companion
- **Category**: Health & Fitness / Sleep Science & Circadian Wellness
- **Target Devices**: Samsung Galaxy Watch 4, Galaxy Watch 5, Galaxy Watch 6, Galaxy Watch 7 (Wear OS)

### Detailed Purpose of Use (Обоснование запроса)
> "Our application provides nocturnal circadian rhythm monitoring and non-invasive REM sleep state tracking to help users cultivate lucid dreaming through cognitive protocols (TLR, MILD, WBTB). To accurately detect physiological signs of REM sleep (parasympathetic atonia coupled with phasic heart-rate variability), we require raw millisecond-accurate Inter-Beat Intervals (IBI) from `TrackerType.HEART_RATE_CONTINUOUS` and synchronous 3-axis accelerometer data. Standard Android heart rate metrics provide only low-frequency averages that are inadequate for time-domain heart-rate variability (HRV / RMSSD) analysis."

### Requested Privileges (Запрашиваемые привилегии)
- `com.samsung.health.sensor.heart_rate_continuous` (Continuous photoplethysmography and inter-beat intervals)
- `com.samsung.health.sensor.accelerometer` (High-resolution motor quiescence and sleep atonia tracking)

### Data Privacy & Storage (Безопасность и хранение данных)
> "All raw biometric sensor readings (HR, IBI, Accelerometer) are processed entirely on-device in real-time. No raw PPG waveforms or biometric time-series are uploaded to any external server or cloud database. Sensor data is aggregated locally in ephemeral memory buffers into 60-second windows and stored exclusively in a secure local Room SQLite database on the user's paired device. The application is completely non-diagnostic and wellness-focused."

### Medical Disclaimer Confirmation (Медицинский дисклеймер)
> "The application includes clear disclaimers that it is not intended for medical diagnosis, treatment, or prevention of sleep disorders (e.g. sleep apnea, narcolepsy, insomnia). It is purely an exploratory tool for cognitive awareness and circadian sleep optimization."

---

## 5. Что делать после получения одобрения?

После одобрения заявки Samsung активирует ваш Package Name и SHA-256 в белом списке службы `HealthTrackingService`.
В проекте достаточно переключить фабрику в `SensorDataSourceFactory`:
```kotlin
// wearApp/src/main/kotlin/com/luciddream/wear/sensor/SensorDataSource.kt
val dataSource = SamsungSensorDataSourceStub(context)
```
и подключить официальный AAR/JAR `samsung-health-sensor-sdk.aar` в `wearApp/libs/`.
Часы автоматически начнут поставлять аппаратно ускоренный поток с субмиллисекундным IBI!
