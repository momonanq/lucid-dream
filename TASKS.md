# Project Tasks & Roadmap

## 📊 Фактический статус (аудит 2026-09-04)

| Слой | Готовность | Комментарий |
|---|---|---|
| `:core:model` | 100% | Модели данных полные, типизированы, сериализуемы, добавлены счетчики сэмплов |
| `:core:algorithm` | ~95% | Баги #1, #3, #4, #5, #7 исправлены, петля калибровки замкнута, guardrails усилены |
| `:core:data` | ~45% | In-Memory репозитории с реактивным Flow (Баг #6 исправлен), Samsung Health = мок |
| `:phoneApp` | ~10% | Консольный JVM-симулятор, не Android-приложение |
| `:wearApp` | ~30% | Сенсор-менеджер исправлен (Баг #1, #2, #3), пока JVM без Wear OS UI/сервиса |
| **MVP по спеке** | **~45%** | Алгоритмический и сенсорный слой безопасны, следующий шаг — Android/Wear OS |

**Главный разрыв:** в `build.gradle.kts` ко всем subprojects применяется только `kotlin("jvm")`.
Нет Android Gradle Plugin, `AndroidManifest.xml`, Compose и Wear OS зависимостей.
`phoneApp` импортирует `com.luciddream.wear.*` и исполняет «часы» в том же JVM-процессе —
граница телефон↔часы фиктивна, реальные отказы связи ночью не моделируются.

## ✅ Что действительно готово

- [x] Структура Gradle-модулей и однонаправленные зависимости (`model` ← `algorithm` ← `data` ← apps)
- [x] Модели домена: `NightSession`, `SensorWindow`, `CueEvent`, `DreamEntry`, `SleepImport`, `UserProfile`
- [x] `RemConfidenceEngine` — интерпретируемый взвешенный скоринг (time / motion / hrv / consistency) с гладкой непрерывной шкалой
- [x] `NightCueDecisionEngine` — guardrails: data sufficiency check, earliest window, max cues, cooldown, wake-spike abort
- [x] Протоколы: MILD, WBTB, SSILD, Reality Checks (расписания и тексты)
- [x] `CalibrationEngine` — постфактум-калибровка с адаптацией порога confidence и `baselineIbiVariance`
- [x] `SamsungSensorManager` — потокобезопасная агрегация окон с сохранением сэмплов последующих окон и контролем достаточности данных
- [x] Unit-тесты на алгоритмы, сенсоры, репозитории + E2E-симуляция (все тесты зеленые)
- [x] M0: Git-репозиторий инициализирован, `.gitignore` настроен, CI workflow добавлен
- [x] MVP-спецификация с честными ограничениями по точности REM-детекции

## ❌ Что НЕ готово (следующие шаги)

- [ ] Android-приложение (телефон): проект пока JVM, требуется конвертация в AGP + Jetpack Compose
- [ ] Wear OS-приложение (часы): нет foreground service, нет Tile/Complication, нет Compose for Wear OS
- [ ] Реальная доставка сигнала: физический вибромотор через `VibratorManager` и звук через `AudioTrack`/`ExoPlayer`
- [ ] Samsung Health Sensor SDK: подключение к нативным сенсорным API Galaxy Watch
- [ ] Samsung Health Data SDK: подключение реального Health Connect / Samsung Health Data API вместо мока
- [ ] Персистентность: Room SQLite вместо `InMemory*`
- [ ] Транспорт телефон↔часы: Wearable Data Layer (`MessageClient` / `DataClient`)
- [ ] Энергопотребление и работа в фоне 8 часов
- [ ] Валидация алгоритма на реальных данных PPG/IBI

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
- [ ] Добавить AGP, конвертировать `:phoneApp` в `com.android.application` (minSdk 30, Compose)
- [ ] Конвертировать `:wearApp` в Wear OS `com.android.application` + Compose for Wear OS
- [ ] `core/*` оставить чистыми JVM/KMP-модулями — они изменений не заметят
- [ ] Реальный транспорт поверх Wearable Data Layer (`MessageClient` / `DataClient`) под существующий `WearSyncMessageProtocol`
- [ ] Обработка разрыва связи ночью: очередь событий на часах, догон при реконнекте
- [ ] `ForegroundService` на часах вместо `CoroutineScope(Dispatchers.Default)`, wake locks, исключение из Doze
- [ ] Реальная хаптика (`Vibrator` / `VibratorManager`) и реальный аудио-cue (`AudioTrack` / `ExoPlayer`, поверх DND-политики)
- [ ] Room вместо `InMemory*` репозиториев + миграции

### M3 — Интеграция Samsung (блокируется внешним одобрением)
- [ ] **Подать партнёрскую заявку на Samsung Health Sensor SDK — начать немедленно, занимает недели**
- [ ] Реализовать `SamsungHealthDataGateway` поверх Samsung Health Data SDK (sleep sessions + stages)
- [ ] Подключить `HEART_RATE_CONTINUOUS` и IBI трекеры в `SamsungSensorManager`
- [ ] Экраны разрешений и graceful degradation при отсутствии Samsung Health
- [ ] Замерить энергопотребление за полную ночь, подобрать частоту опроса

### M4 — Валидация и калибровка (после M3)
- [ ] **Ночи только на сбор данных, cue отключены** — алгоритм ни разу не видел настоящий PPG
- [ ] Сравнить confidence-скоры с постфактум-стадиями Samsung Health, посчитать реальный hit-rate
- [ ] Перекалибровать веса `RemConfidenceEngine` на собранных данных
- [ ] Пилот на 5–10 пользователях

### M5 — Продуктовая поверхность
- [ ] Экраны телефона: Onboarding, Home/Tonight, Dream Journal, Reality Checks, Night Session Setup, Sleep Review, Insights
- [ ] Экраны часов: Ready, Start Session, Night Running, Quick Morning Feedback
- [ ] Голосовой ввод снов
- [ ] Уведомления и reality-check reminders

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

## ⚠️ Внешние блокеры и риски

- **Samsung Health Sensor SDK выдаётся по партнёрской заявке.** Без одобрения режим Watch Assist невозможен физически. Заявку подавать первой, параллельно с M1–M2.
- **Батарея.** Непрерывный HR + IBI + акселерометр 8 часов — главный инженерный риск Wear-приложения, пока не адресован никак.
- **Метрики на моке ничего не значат.** `MockSamsungHealthDataGateway` генерирует гипнограмму с REM ровно там, где алгоритм его ожидает. Любая «точность попадания в REM» до M4 — самоисполняющееся пророчество.
- **Фрагментация сна.** Спека честно отказывается лечить парасомнии, но не отсеивает таких пользователей. Нужны: лимит ночей в неделю, авто-стоп при деградации recall или росте wake spikes N ночей подряд, возрастной порог, скрининг на апноэ/бессонницу/парасомнии в онбординге.

## 📝 Architectural Decisions & Log

- Инициализирован AI Workspace (2026-08-26 17:28)
- Создана модульная архитектура Gradle Kotlin (Java 17, Coroutines, Serialization) (2026-08-26 17:51)
- Реализованы алгоритмы REM-эвристики, протоколы индукции (MILD, WBTB, SSILD, TLR, Reality Checks) и логика калибровки (2026-08-26 17:54)
- 19 тестовых задач выполнены — **на моках, без реальных сенсоров** (2026-08-26 17:56)
- **Аудит состояния (2026-09-04):** выявлен реестр из 7 багов и отсутствие нативного Android/Wear стека. Разработан роадмап M0–M5.
- **Выполнены этапы M0 и M1 (2026-09-04):**
  - M0: репозиторий зафиксирован в Git, настроены `.gitignore` и CI workflow на GitHub Actions.
  - M1: устранены баги #1–#7. Введен жесткий контроль достаточности данных (`isDataSufficient`), ликвидирована потеря сэмплов между окнами в `SamsungSensorManager`, замкнута петля персонализации в `CalibrationEngine`, сглажен скоринг моторной активности, `getActiveSession()` переведен на реактивный `Flow.map`. Добавлены юнит-тесты на проверку безопасности. Все тесты успешны.

