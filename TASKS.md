# Project Tasks & Roadmap

## 📊 Фактический статус (аудит 2026-09-04)

| Слой | Готовность | Комментарий |
|---|---|---|
| `:core:model` | ~90% | Модели данных полные, сериализуемые |
| `:core:algorithm` | ~70% | Логика есть, петля калибровки не замкнута, есть баги |
| `:core:data` | ~30% | Только In-Memory репозитории, Samsung Health = мок |
| `:phoneApp` | ~10% | Консольный JVM-симулятор, не Android-приложение |
| `:wearApp` | ~10% | Консольный JVM-модуль, не Wear OS, SDK не подключён |
| **MVP по спеке** | **~30%** | Продуктовой поверхности нет |

**Главный разрыв:** в `build.gradle.kts` ко всем subprojects применяется только `kotlin("jvm")`.
Нет Android Gradle Plugin, `AndroidManifest.xml`, Compose и Wear OS зависимостей.
`phoneApp` импортирует `com.luciddream.wear.*` и исполняет «часы» в том же JVM-процессе —
граница телефон↔часы фиктивна, реальные отказы связи ночью не моделируются.

## ✅ Что действительно готово

- [x] Структура Gradle-модулей и однонаправленные зависимости (`model` ← `algorithm` ← `data` ← apps)
- [x] Модели домена: `NightSession`, `SensorWindow`, `CueEvent`, `DreamEntry`, `SleepImport`, `UserProfile`
- [x] `RemConfidenceEngine` — интерпретируемый взвешенный скоринг (time / motion / hrv / consistency)
- [x] `NightCueDecisionEngine` — guardrails: earliest window, max cues, cooldown, wake-spike abort
- [x] Протоколы: MILD, WBTB, SSILD, Reality Checks (расписания и тексты)
- [x] `CalibrationEngine` — постфактум-сопоставление cue со стадиями сна
- [x] Unit-тесты на алгоритмы + E2E-симуляция на моках (19 кейсов)
- [x] MVP-спецификация с честными ограничениями по точности REM-детекции

## ❌ Что НЕ готово (вопреки прошлой записи в этом файле)

- [ ] Android-приложение (телефон): проект вообще не Android
- [ ] Wear OS-приложение (часы): нет foreground service, нет Tile/Complication, нет UI
- [ ] Реальная доставка сигнала: `TlrAudioEngine` и `WatchHapticEngine` только пишут запись в список и делают `delay()` — механизм TLR отсутствует
- [ ] Samsung Health Sensor SDK: `SamsungSensorManager` к SDK не подключён, данные подаются вручную
- [ ] Samsung Health Data SDK: только `MockSamsungHealthDataGateway` с идеализированной гипнограммой
- [ ] Персистентность: всё `InMemory*`, ночная сессия теряется при убийстве процесса
- [ ] Транспорт телефон↔часы: `WearSyncMessageProtocol` описан, но поверх Wearable Data Layer не реализован
- [ ] Энергопотребление и работа в фоне 8 часов
- [ ] Валидация алгоритма на реальных данных PPG/IBI

---

## 🗺 Роадмап

### M0 — Гигиена репозитория (0.5 дня)
- [ ] Сделать первый коммит: в git сейчас **ноль коммитов**, всё untracked
- [ ] Проверить `.gitignore`: исключить `build/`, `.gradle/`, `.kotlin/`, `graphify-out/`
- [ ] Настроить CI (в `.github/` пока только `copilot-instructions.md`, workflow нет)

### M1 — Безопасность алгоритма (1–2 дня, делать до любого железа)
Эти правки предотвращают подачу сигнала на мусорных данных.
- [ ] **Баг #1**: ввести состояние `INSUFFICIENT_DATA` и жёстко блокировать cue при нём
- [ ] **Баг #2**: не терять сэмплы при агрегации окна
- [ ] **Баг #3**: раздельные счётчики сэмплов по модальностям вместо суммы
- [ ] **Баг #4**: сверить направление `hrv_score` с литературой (RMSSD vs SDNN в REM/N3)
- [ ] Добавить property-based тесты: при любых входах с пустыми буферами решение = `Suppressed`

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
- [ ] Замкнуть петлю персонализации: адаптация порога confidence и `baselineIbiVariance` (**Баг #5**)
- [ ] Перекалибровать веса `RemConfidenceEngine` на собранных данных
- [ ] Пилот на 5–10 пользователях

### M5 — Продуктовая поверхность
- [ ] Экраны телефона: Onboarding, Home/Tonight, Dream Journal, Reality Checks, Night Session Setup, Sleep Review, Insights
- [ ] Экраны часов: Ready, Start Session, Night Running, Quick Morning Feedback
- [ ] Голосовой ввод снов
- [ ] Уведомления и reality-check reminders

---

## 🐞 Известные баги

| # | Файл | Проблема | Приоритет |
|---|---|---|---|
| 1 | `wearApp/.../sensor/SamsungSensorManager.kt` | При пустых буферах подставляются дефолты (`movementIndex=0.0`, `rmssd=40.0`, `meanHr=60.0`). Сбой датчика выглядит как идеальный REM → cue при отключившемся сенсоре | 🔴 Критический |
| 2 | `wearApp/.../sensor/SamsungSensorManager.kt` | `while (buffer.isNotEmpty()) { poll(); if (ts in start..end) add }` — опоздавшие сэмплы и сэмплы следующего окна извлекаются и молча выбрасываются | 🔴 Высокий |
| 3 | `wearApp/.../sensor/SamsungSensorManager.kt` | `sampleCount = hrs + ibis + motions` смешивает модальности; `calculateHrvScore` проверяет `sampleCount < 5` и не сработает при 20 motion / 0 IBI | 🟡 Средний |
| 4 | `core/algorithm/.../RemConfidenceEngine.kt` | Направление `hrv_score` спорно: в комментарии «N3 shows very low RMSSD», в литературе обычно наоборот. RMSSD и SDNN смешаны в один профиль → 20% веса могут работать в неверную сторону | 🟡 Средний (требует сверки) |
| 5 | `core/algorithm/.../CalibrationEngine.kt` | Петля персонализации не замкнута: порог confidence захардкожен `0.65` в конструкторе движка и отсутствует в `UserProfile`; `baselineIbiVariance` используется в скоринге, но никогда не обновляется | 🟡 Средний |
| 6 | `core/data/.../repository/NightSessionRepository.kt` | `getActiveSession()` возвращает снимок `MutableStateFlow(active)`, который никогда не обновится — тип обещает Flow, поведение разовое | 🟡 Средний |
| 7 | `core/algorithm/.../RemConfidenceEngine.kt` | Разрыв в `calculateMotionScore` на границе `0.05` (скачок 1.0 → 0.85); неиспользуемые импорты `exp` / `abs` | 🟢 Низкий |

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
- **Аудит состояния (2026-09-04):** предыдущая отметка «Реализация ключевых модулей ✅» была преждевременной. Проект — консольный JVM-симулятор, а не Android/Wear OS приложение; Samsung SDK не подключены; доставка сигнала (аудио/хаптика) не реализована. Статус пересмотрен, добавлены роадмап M0–M5 и реестр из 7 багов. Решение: чинить безопасность алгоритма (M1) до перевода на Android (M2), партнёрскую заявку Samsung подавать немедленно как самый длинный внешний путь.
