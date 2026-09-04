# Project Tasks & Roadmap

## 📊 Фактический статус (аудит 2026-09-04)

| Слой | Готовность | Комментарий |
|---|---|---|
| `:core:model` | 100% | Модели домена полные, сериализуемые |
| `:core:algorithm` | ~95% | Скоринг, решения, калибровка, гардрейлы. Все баги реестра закрыты. **Не валидирован на реальных данных** |
| `:core:data` | ~70% | Room, репозитории, транспорт. Samsung Health Data = Health Connect + мок |
| `:phoneApp` | ~85% | Android + Compose Material 3: Tonight, Dream Journal, Sleep Review, скрининг |
| `:wearApp` | ~70% | Wear OS + Compose, foreground service, хаптика. Сенсоры — только стандартный HR |
| **MVP по спеке** | **~70%** | Архитектура и продуктовая поверхность готовы |

**Главный разрыв больше не архитектурный.** Проект переведён на Android/Wear OS (M2), транспорт,
персистентность, хаптика и аудио реальные. Осталось два блокера, и оба про данные, а не про код:

1. **Нет доступа к настоящим IBI/PPG.** `SamsungSensorDataSourceStub` — заглушка поверх стандартного
   Wear OS HR-датчика, который межпульсовых интервалов не отдаёт. Без них `hrv_score` исключается из
   скоринга (веса перенормируются), то есть на любых часах без Samsung SDK алгоритм работает
   в ослабленном режиме. Разблокируется developer mode — заявка для этого не нужна.
2. **Алгоритм не валидирован.** Ни одной ночи на реальном PPG не собрано.

## ✅ Что действительно готово

- [x] Структура Gradle-модулей и однонаправленные зависимости (`model` ← `algorithm` ← `data` ← apps)
- [x] Модели домена: `NightSession`, `SensorWindow`, `CueEvent`, `DreamEntry`, `SleepImport`, `UserProfile`
- [x] `RemConfidenceEngine` — интерпретируемый взвешенный скоринг (time / motion / hrv / consistency) с гладкой непрерывной шкалой
- [x] `NightCueDecisionEngine` — guardrails: data sufficiency check, earliest window, max cues, cooldown, wake-spike abort
- [x] Протоколы: MILD, WBTB, SSILD, Reality Checks (расписания и тексты)
- [x] `CalibrationEngine` — постфактум-калибровка с адаптацией порога confidence и `baselineIbiVariance`
- [x] `SamsungSensorManager` — потокобезопасная агрегация окон с сохранением сэмплов последующих окон и контролем достаточности данных
- [x] `SleepSafetyGuardian` — гардрейлы против фрагментации сна между ночами (недельный лимит, ночи отдыха, авто-стоп по wake spikes и качеству сна, скрининг в онбординге)
- [x] Unit-тесты на алгоритмы, сенсоры, репозитории + E2E-симуляция (все тесты зеленые)
- [x] M0: Git-репозиторий инициализирован, `.gitignore` настроен, CI workflow добавлен
- [x] MVP-спецификация с честными ограничениями по точности REM-детекции

## ❌ Что НЕ готово (следующие шаги)

- [ ] **Samsung Health Sensor SDK**: реальный трекер вместо `SamsungSensorDataSourceStub` — единственный источник настоящих IBI/PPG
- [ ] **Валидация алгоритма на реальных данных PPG/IBI** — не сделана, см. M4
- [ ] Партнёрская заявка Samsung — нужна для распространения, не для разработки
- [ ] Samsung Health Data SDK: реальный импорт стадий сна вместо `MockSamsungHealthDataGateway`
- [ ] Замер энергопотребления за полную ночь на живом устройстве
- [ ] Релизный keystore и подпись (нужны для заявки и публикации)
- [ ] Настоящая Room-миграция вместо `fallbackToDestructiveMigration`

---

## 🗺 Роадмап

### M0 — Гигиена репозитория (0.5 дня)
- [x] Сделать первый коммит в git
- [x] Настроить `.gitignore`: исключены `build/`, `.gradle/`, `.kotlin/`, `.idea/`, `graphify-out/`, `.antigravity/`
- [x] Настроить CI (`.github/workflows/ci.yml` с автоматическим запуском Gradle test)

### M1 — Безопасность алгоритма (1–2 дня)
- [x] **Баг #1**: введено состояние `isDataSufficient = false` и жесткое подавление cue при сбое/отсутствии сенсоров
- [x] **Баг #2**: устранена потеря сэмплов при агрегации окна (использование `peek()` и сохранение будущих сэмплов в очереди)
- [x] **Баг #3**: раздельные счётчики сэмплов по модальностям (`hrSampleCount`, `ibiSampleCount`, `motionSampleCount`) вместо смешанной суммы
- [x] **Баг #4**: сверка направления `hrv_score` и изоляция проверки сэмплов IBI
- [x] **Баг #5**: замкнута петля персонализации (адаптация `confidenceThreshold` и `baselineIbiVariance` в `UserProfile`)
- [x] **Баг #6**: реактивный `getActiveSession()` через `Flow.map` в `NightSessionRepository`
- [x] **Баг #7**: устранена ступенька в `calculateMotionScore`, удален неиспользуемый импорт `kotlin.math.exp`
- [x] Добавлены специализированные unit-тесты на защиту от ложных срабатываний и достаточность данных

### M2 — Перевод на Android/Wear OS (1–2 недели)
- [x] Добавить AGP (8.7.3), конвертировать `:phoneApp` в `com.android.application` (minSdk 30, Jetpack Compose Material 3)
- [x] Конвертировать `:wearApp` в Wear OS `com.android.application` + Compose for Wear OS (minSdk 30, Wear Compose 1.4.1)
- [x] `:core:model` и `:core:algorithm` оставлены чистыми Kotlin JVM модулями (быстрые тесты за миллисекунды)
- [x] Реальный транспорт поверх Wearable Data Layer (`MessageClient` / `NodeClient` / `WearableListenerService`)
- [x] Обработка разрыва связи ночью: персистентная очередь событий на часах (`RoomOfflineEventQueue` в Room SQLite), последовательный догон при реконнекте
- [x] `WatchTrackingForegroundService` на часах с `PARTIAL_WAKE_LOCK`, Ongoing Activity нотификацией, Doze-устойчивостью и тикером 60с
- [x] Реальная хаптика (`AndroidWatchHapticEngine` поверх `Vibrator` / `VibratorManager` с прогрессивным 3-tap паттерном и защитой по длительности) и реальный аудио-cue (`AndroidTlrAudioEngine` на `AudioTrack` с 432 Гц синусоидой и бинауральными 6 Гц тета-ритмами)
- [x] Room SQLite база данных (`LucidDatabase`) + персистентные репозитории (`RoomNightSessionRepository`, `RoomDreamJournalRepository`, `RoomUserProfileRepository`)
- [x] Нативные UI экраны:
  - Phone: `TonightScreen` (селектор протоколов, слайдеры громкости/вибрации, запуск трекинга), `DreamJournalScreen` (список снов, FAB ввода, авто-экстракция dream signs), `SleepReviewScreen` (утренний отчет, таймлайн REM, калибровка)
  - Wear OS: `WatchReadyScreen` (кнопка старта, тест хаптики), `WatchTrackingScreen` (индикатор REM, счетчик сигналов, защита от случайной остановки), `WatchMorningFeedbackScreen` (экспресс-опрос из 3 шагов)

### M3 — Интеграция Samsung Health & Энергоэффективность
- [x] **Подготовлен гайд и шаблоны формуляров для подачи партнерской заявки на Samsung Health Sensor SDK** (`docs/SAMSUNG_HEALTH_PARTNER_GUIDE.md`)
- [x] Реализован `HealthConnectSleepGateway` поверх Android Health Connect / Samsung Health Data с graceful degradation
- [x] Подключены `SensorDataSource` и `SensorDataSourceFactory`: поддержка `SamsungSensorDataSourceStub` и нативного `AndroidStandardSensorDataSource` (`Sensor.TYPE_HEART_RATE`)
- [x] Экраны разрешений и индикатор аппаратного статуса на часах (`WatchReadyScreen`: "Samsung IBI Active" / "Standard Wear OS HR")
- [x] `BatteryDutyCycleManager`: адаптивный циркадный опрос (15с/2м в фазе N3, непрерывно в пиковой зоне REM 4.5–8ч, Low Battery Guard <20%) для гарантии сохранения заряда за 8 часов сна

### M4 — Валидация и калибровка

**Инструментарий ✅ / сама валидация ⬜.** Ниже сделаны средства измерения, а не измерение:
ни одной ночи на реальных данных не собрано, алгоритм не видел настоящий PPG.

Инструментарий:
- [x] Режим пассивного сбора без стимулов (`NightMode.BEGINNER`)
- [x] `PilotValidationEngine`: сопоставление confidence-скоров со стадиями Samsung Health, hit-rate, precision, specificity, F1
- [x] `optimizeWeights` — перекалибровка весов `RemConfidenceEngine` по собранным данным
- [x] Экспорт сессий в CSV (`generatePilotCsv`) и регламент пилота (`docs/PILOT_STUDY_PROTOCOL.md`)

Собственно валидация:
- [ ] **Не заблокирована партнёрской заявкой.** Developer mode Health Sensor Service даёт настоящие IBI и PPG без одобрения Samsung — см. [SAMSUNG_HEALTH_PARTNER_GUIDE.md](docs/SAMSUNG_HEALTH_PARTNER_GUIDE.md), раздел 2
- [x] Исправить баг #10 — фабрикация IBI убрана, HRV исключается из скоринга при недоступности
- [ ] Подключить реальный трекер Samsung вместо `SamsungSensorDataSourceStub`
- [ ] Собрать ночи в пассивном режиме на своих часах
- [ ] Посчитать реальный hit-rate против стадий Samsung Health
- [ ] Перекалибровать веса и только потом заявлять точность
- [ ] Пилот на 5–10 пользователях

### M5 — Продуктовая поверхность
- [x] **Экран скрининга безопасности в онбординге** (`ScreeningScreen` + `ScreeningViewModel`) — заполняет `UserProfile.screening`, показывает последствия ответов до отправки, допускает пропуск (без ночных сигналов) и повторное прохождение из баннера на Tonight
- [x] Экраны телефона: Tonight, Dream Journal, Sleep Review, навигация Material 3
- [x] Экраны часов: Ready, Night Running, Quick Morning Feedback на Wear Compose
- [x] Голосовой ввод снов (Android SpeechRecognizer / RecognizerIntent с кнопкой микрофона в `AddDreamDialog`)
- [x] Уведомления и reality-check reminders в течение дня (`AndroidRealityCheckScheduler`, `RealityCheckReceiver`, `BootReceiver` и карточка управления в `TonightScreen`)
- [x] Интерактивная гипнограмма стадий сна и наложенный график вероятности REM в `SleepReviewScreen`

---

## 🐞 Известные баги

| # | Файл | Проблема | Статус |
|---|---|---|:---:|
| 1 | `wearApp/.../sensor/SamsungSensorManager.kt` | При пустых буферах подставлялись дефолты (сбой датчика выглядел как идеальный REM) | ✅ Исправлен |
| 2 | `wearApp/.../sensor/SamsungSensorManager.kt` | `poll()` извлекал и отбрасывал сэмплы последующего окна | ✅ Исправлен |
| 3 | `wearApp/.../sensor/SamsungSensorManager.kt` | `sampleCount` смешивал разные модальности датчиков | ✅ Исправлен |
| 4 | `core/algorithm/.../RemConfidenceEngine.kt` | Проверка `sampleCount < 5` вместо раздельной IBI; сверка HRV профиля REM/N3 | ✅ Исправлен |
| 5 | `core/algorithm/.../CalibrationEngine.kt` | Петля персонализации не была замкнута (порог и базовые показатели не обновлялись) | ✅ Исправлен |
| 6 | `core/data/.../repository/NightSessionRepository.kt` | `getActiveSession()` возвращал разовый статичный снимок вместо реактивного Flow | ✅ Исправлен |
| 7 | `core/algorithm/.../RemConfidenceEngine.kt` | Разрыв в `calculateMotionScore` на границе 0.05, неиспользуемый импорт | ✅ Исправлен |
| 8 | `core/algorithm/.../NightCueDecisionEngine.kt` | Литерал `0.65` служил sentinel-значением («порог не переопределяли»): сравнение `Double` на неравенство, явно переданный `0.65` молча игнорировался в пользу профиля, семантика ломалась при смене дефолта. Заменено на `confidenceThresholdOverride: Double? = null` + `?: userProfile.confidenceThreshold`, добавлены 2 регрессионных теста | ✅ Исправлен |
| 9 | `phoneApp/.../phone/Main.kt` | 338 строк мёртвого кода: консольный `fun main()` из JVM-прототипа остался в Android-модуле, где никогда не вызывается | ✅ Исправлен (файл удалён) |
| 10 | `wearApp/.../sensor/SensorDataSource.kt` | **`AndroidStandardSensorDataSource` синтезирует `IbiReading`** из обычного HR-датчика: либо интервал между колбэками (артефакт частоты опроса), либо `60000/bpm` (детерминированная функция от BPM). Из этих чисел считается RMSSD/SDNN, то есть `hrv_score` — 20% веса — берётся из шума. Хуже: фабрикация обходит гардрейл достаточности данных (`ibis.size >= 5`), который вводился ровно затем, чтобы отсутствие IBI блокировало сигнал | ✅ Исправлен |
| 11 | `wearApp/.../sensor/SensorDataSource.kt` | `SamsungSensorDataSourceStub` объявляет `fidelity = SAMSUNG_CONTINUOUS_IBI`, но делегирует всё `AndroidStandardSensorDataSource`. На Galaxy Watch `WatchReadyScreen` показывает «● Samsung IBI Active» при неподключённом SDK | ✅ Исправлен |

## ⚠️ Внешние блокеры и риски

- **Samsung Health Sensor SDK выдаётся по партнёрской заявке.** Без одобрения режим Watch Assist невозможен физически. Заявку подавать первой, параллельно с M1–M2.
- **Батарея.** Непрерывный HR + IBI + акселерометр 8 часов — главный инженерный риск Wear-приложения, пока не адресован никак.
- **Метрики на моке ничего не значат.** `MockSamsungHealthDataGateway` генерирует гипнограмму с REM ровно там, где алгоритм его ожидает. Любая «точность попадания в REM» до M4 — самоисполняющееся пророчество.
- **Фрагментация сна.** Закрыто кодом: `SleepSafetyGuardian` (`core/algorithm`) ограничивает экспозицию между ночами — лимит 3 ночи с сигналами на скользящую неделю, обязательная ночь отдыха между ними, авто-стоп после 2 ночей подряд с wake spike ≥50%, авто-стоп при среднем субъективном качестве сна ≤2 из 5 за 3 ночи, скрининг в онбординге (`SafetyScreening`: возраст <18, апноэ, хроническая бессонница, парасомнии, нарколепсия, судорожные расстройства, лечение психоза). Гардрейл не блокирует приложение, а понижает ночь до `BEGINNER`, где `NightCueDecisionEngine` независимо подавляет все сигналы. Скрининг собирается на экране онбординга (`ScreeningScreen`); до его прохождения `screening.isComplete = false` и ночные сигналы выключены. Пропуск разрешён и не блокирует дневник, recall и reality checks.

## 📝 Architectural Decisions & Log

- Инициализирован AI Workspace (2026-08-26 17:28)
- Создана модульная архитектура Gradle Kotlin (Java 17, Coroutines, Serialization) (2026-08-26 17:51)
- Реализованы алгоритмы REM-эвристики, протоколы индукции (MILD, WBTB, SSILD, TLR, Reality Checks) и логика калибровки (2026-08-26 17:54)
- 19 тестовых задач выполнены — **на моках, без реальных сенсоров** (2026-08-26 17:56)
- **Аудит состояния (2026-09-04):** выявлен реестр из 7 багов и отсутствие нативного Android/Wear стека. Разработан роадмап M0–M5.
- **Выполнены этапы M0 и M1 (2026-09-04):**
  - M0: репозиторий зафиксирован в Git, настроены `.gitignore` и CI workflow на GitHub Actions.
  - M1: устранены баги #1–#7. Введен жесткий контроль достаточности данных (`isDataSufficient`), ликвидирована потеря сэмплов между окнами в `SamsungSensorManager`, замкнута петля персонализации в `CalibrationEngine`, сглажен скоринг моторной активности, `getActiveSession()` переведен на реактивный `Flow.map`. Добавлены юнит-тесты на проверку безопасности. Все тесты успешны.

