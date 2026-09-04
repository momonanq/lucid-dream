package com.luciddream.phone

import com.luciddream.algorithm.protocols.MildProtocolManager
import com.luciddream.algorithm.protocols.RealityCheckScheduler
import com.luciddream.algorithm.protocols.SsildProtocolManager
import com.luciddream.algorithm.protocols.WbtbScheduler
import com.luciddream.data.repository.*
import com.luciddream.data.samsung.MockSamsungHealthDataGateway
import com.luciddream.data.sync.QuickMorningFeedbackPayload
import com.luciddream.model.*
import com.luciddream.phone.audio.TlrAudioEngine
import com.luciddream.phone.service.PhoneSessionCoordinator
import com.luciddream.phone.ui.*
import com.luciddream.wear.haptic.WatchHapticEngine
import com.luciddream.wear.sensor.SamsungSensorManager
import com.luciddream.wear.service.WatchNightTrackingService
import com.luciddream.wear.ui.WatchMainWorkflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Scanner

fun main(args: Array<String>) = runBlocking {
    println("=================================================================")
    println("   LUCID DREAM MVP — SAMSUNG GALAXY PHONE + WATCH RUNTIME")
    println("=================================================================")
    println("Экосистема: Samsung Galaxy Phone + Galaxy Watch (Wear OS)")
    println("Интеграции: Samsung Health Sensor SDK + Samsung Health Data SDK")
    println("Протоколы: MILD, WBTB, TLR (Targeted Lucidity Reactivation), SSILD, ADA")
    println("=================================================================\n")

    // Dependencies setup
    val sessionRepo = InMemoryNightSessionRepository()
    val journalRepo = InMemoryDreamJournalRepository()
    val profileRepo = InMemoryUserProfileRepository()
    val healthGateway = MockSamsungHealthDataGateway()
    val phoneAudio = TlrAudioEngine()
    val coordinator = PhoneSessionCoordinator(sessionRepo, profileRepo, healthGateway, phoneAudio)
    val analyticsRepo = AnalyticsRepository(sessionRepo)

    val tonightVm = TonightViewModel(coordinator, sessionRepo, profileRepo)
    val journalVm = DreamJournalViewModel(journalRepo)
    val realityCheckVm = RealityCheckViewModel()
    val insightsVm = InsightsViewModel(sessionRepo, analyticsRepo)

    // Pre-populate initial sample dream journal
    val sampleDream = DreamEntry(
        id = "sample_dream_1",
        dateIso = "2026-08-26",
        timestampMs = System.currentTimeMillis() - 86400000,
        title = "Полёт над старым городом",
        transcript = "Я летал над школой и старой квартирой, затем посмотрел на свои странные руки и понял, что сплю.",
        tags = setOf(DreamTag.LUCID, DreamTag.FLYING),
        dreamSigns = journalRepo.extractDreamSigns("Я летал над школой и старой квартирой, странные руки"),
        lucidityLevel = 4
    )
    journalRepo.saveEntry(sampleDream)

    if (args.contains("--demo") || args.contains("--auto")) {
        runAutomatedDemo(coordinator, sessionRepo, profileRepo, healthGateway, phoneAudio, journalRepo)
        return@runBlocking
    }

    val scanner = Scanner(System.`in`)
    var running = true

    while (running) {
        println("\n--- ГЛАВНОЕ МЕНЮ ---")
        println("1. 🌙 Запустить сквозную симуляцию ночи (Phone + Galaxy Watch + TLR + Sync)")
        println("2. 📖 Дневник снов и распознавание маркеров сна (Dream Signs)")
        println("3. 🎯 Дневные проверки реальности (Reality Checks & ADA)")
        println("4. 🧠 Интерактивные протоколы индукции (MILD / SSILD / WBTB)")
        println("5. 📊 Аналитика и профиль калибровки (Insights & Calibration)")
        println("6. 🚀 Запустить полный авто-тест системы (Full Auto Demo)")
        println("0. ❌ Выход")
        print("\nВыберите пункт: ")

        val input = if (scanner.hasNextLine()) scanner.nextLine().trim() else "6"

        when (input) {
            "1" -> runInteractiveNightSimulation(coordinator, sessionRepo, profileRepo, healthGateway, phoneAudio)
            "2" -> runDreamJournalMenu(journalVm, scanner)
            "3" -> runRealityCheckMenu(realityCheckVm, scanner)
            "4" -> runProtocolsMenu(scanner)
            "5" -> runInsightsMenu(insightsVm, profileRepo)
            "6" -> runAutomatedDemo(coordinator, sessionRepo, profileRepo, healthGateway, phoneAudio, journalRepo)
            "0", "exit", "quit" -> {
                println("Завершение работы.")
                running = false
            }
            else -> {
                println("Выполняю авто-демонстрацию...")
                runAutomatedDemo(coordinator, sessionRepo, profileRepo, healthGateway, phoneAudio, journalRepo)
                running = false
            }
        }
    }
}

suspend fun runInteractiveNightSimulation(
    coordinator: PhoneSessionCoordinator,
    sessionRepo: InMemoryNightSessionRepository,
    profileRepo: InMemoryUserProfileRepository,
    healthGateway: MockSamsungHealthDataGateway,
    phoneAudio: TlrAudioEngine
) {
    println("\n=======================================================")
    println(" 🌙 СИМУЛЯЦИЯ НОЧНОЙ СЕССИИ (SAMSUNG GALAXY + WATCH)")
    println("=======================================================")

    val profile = profileRepo.getUserProfile().first()
    println("Пользовательский профиль:")
    println("  - Базовый пульс сна: ${profile.baselineHeartRate} bpm")
    println("  - Базовая вариабельность (IBI): ${profile.baselineIbiVariance} ms")
    println("  - Интенсивность вибрации: ${(profile.preferredHapticIntensity * 100).toInt()}%")
    println("  - Минимальная задержка перед cue: ${profile.earliestCueMinutesAfterOnset} мин (защита N3)")
    println("  - Cooldown между сигналами: ${profile.cooldownMinutes} мин\n")

    val startPayload = coordinator.startNightSession(
        mode = NightMode.TLR,
        audioEnabled = true
    )
    println("✓ Сессия ${startPayload.sessionId} запущена на телефоне и отправлена на Galaxy Watch.")

    val watchSensors = SamsungSensorManager()
    val watchHaptic = WatchHapticEngine()
    val watchService = WatchNightTrackingService(watchSensors, watchHaptic)
    val watchSession = watchService.startSession(startPayload, profile)

    // Bridge callbacks
    watchService.onCueTriggeredCallbacks.add { cuePayload ->
        println("\n>>> [GALAXY WATCH] Сработал ночной триггер! <<<")
        println("    Тип: ${cuePayload.cueType} | Интенсивность: ${(cuePayload.intensity * 100).toInt()}% | REM Confidence: ${String.format("%.2f", cuePayload.confidence)}")
        coordinator.handleLiveCueEvent(cuePayload)
        println("    [SAMSUNG PHONE] Проигран гармонический 432 Гц звуковой сигнал TLR")
    }

    watchService.onWakeSpikeCallbacks.add { spike ->
        println("\n[ВНИМАНИЕ] Зафиксирован Wake Spike (пробуждение после сигнала) на cue: ${spike.cueId}")
        coordinator.handleWakeSpikeEvent(spike)
    }

    val startMs = startPayload.startTimeMs
    println("\nЭмуляция 8 часов сна (с ускоренным прогоном фаз):")

    // Phase 1: 0 - 90 min (Light & Deep N3)
    println("• 0–90 мин: Погружение в сон и глубокий медленноволновый сон (N3). Сигналы заблокированы алгоритмом.")
    feedSensorBatch(watchSensors, startMs, startMs + 90 * 60 * 1000L, hr = 54.0, ibiVar = 25.0, motion = 0.02)
    var win = watchService.processSensorWindow(startMs, startMs + 90 * 60 * 1000L, profile)
    println("  -> Окно 90м: REM Confidence = ${String.format("%.2f", win.confidence)} (подавлен)")

    // Phase 2: 90 - 240 min (Cycle 2, brief REM)
    println("• 90–240 мин: Второй цикл сна, нарастание циркадной готовности.")
    feedSensorBatch(watchSensors, startMs + 90 * 60 * 1000L, startMs + 240 * 60 * 1000L, hr = 56.0, ibiVar = 35.0, motion = 0.03)
    win = watchService.processSensorWindow(startMs + 90 * 60 * 1000L, startMs + 240 * 60 * 1000L, profile)
    println("  -> Окно 240м: REM Confidence = ${String.format("%.2f", win.confidence)}")

    // Phase 3: 240 - 360 min (Peak REM zone 5.5h)
    println("• 240–360 мин: Зона пиковой плотности REM (5.5 часов сна). Высокая атония и вариабельность пульса.")
    feedSensorBatch(watchSensors, startMs + 240 * 60 * 1000L, startMs + 360 * 60 * 1000L, hr = 58.0, ibiVar = 45.0, motion = 0.02)
    win = watchService.processSensorWindow(startMs + 240 * 60 * 1000L, startMs + 360 * 60 * 1000L, profile)
    println("  -> Окно 360м: REM Confidence = ${String.format("%.2f", win.confidence)} (ВЫСОКАЯ ВЕРОЯТНОСТЬ REM)")

    // Finish session in the morning
    println("\n• Утро (480 мин): Завершение сессии и утренний опрос.")
    val morningFeedback = QuickMorningFeedbackPayload(
        sessionId = startPayload.sessionId,
        timestampMs = startMs + 480 * 60 * 1000L,
        hadDream = true,
        hadLucidDream = true,
        noticedSignal = true
    )

    val (finishedSession, calibration) = coordinator.completeMorningSession(
        sessionId = startPayload.sessionId,
        endTimeMs = startMs + 480 * 60 * 1000L,
        morningFeedback = morningFeedback
    )

    println("\n=======================================================")
    println(" 📋 РЕЗУЛЬТАТЫ СЕССИИ И ПОСТФАКТУМ КАЛИБРОВКИ")
    println("=======================================================")
    println("Всего сигналов подано: ${calibration.totalCuesDelivered}")
    println("Сигналов в фазе REM (по Samsung Health): ${calibration.cuesInRemStage}")
    println("Точность попадания в REM (Accuracy Proxy): ${String.format("%.1f", calibration.remAccuracyProxy * 100)}%")
    println("Количество wake spikes: ${calibration.wakeSpikesCount}")
    println("Рекомендации алгоритма:")
    calibration.recommendations.forEach { println("  • $it") }
    println("Калибровочных ночей завершено: ${calibration.adaptedProfile.calibrationNightsCompleted}")
}

fun feedSensorBatch(sensors: SamsungSensorManager, startMs: Long, endMs: Long, hr: Double, ibiVar: Double, motion: Double) {
    val step = ((endMs - startMs) / 30).coerceAtLeast(1000L)
    for (i in 0 until 30) {
        val t = startMs + (i * step)
        sensors.onHeartRateSample(HeartRateReading(t, hr))
        val jitter = if (i % 2 == 0) ibiVar else -ibiVar
        sensors.onIbiSample(IbiReading(t, (60000.0 / hr) + jitter))
        sensors.onMotionSample(MotionReading(t, motion.toFloat(), motion.toFloat(), 9.8f))
    }
}

suspend fun runDreamJournalMenu(journalVm: DreamJournalViewModel, scanner: Scanner) {
    journalVm.loadEntries()
    val state = journalVm.uiState.value
    println("\n=== 📖 ДНЕВНИК СНОВ ===")
    println("Всего записей: ${state.entries.size}")
    state.entries.forEach { entry ->
        println("\n[${entry.dateIso}] \"${entry.title}\" (Осознанность: ${entry.lucidityLevel}/5)")
        println("Текст: ${entry.transcript}")
        println("Теги: ${entry.tags.joinToString()}")
        println("Маркеры сна (Dream Signs): ${entry.dreamSigns.map { "${it.keyword} (${it.category})" }}")
    }

    println("\nДобавить новый сон? (y/n): ")
    if (scanner.hasNextLine() && scanner.nextLine().trim().lowercase() == "y") {
        print("Заголовок: ")
        val title = if (scanner.hasNextLine()) scanner.nextLine() else "Новый сон"
        print("Текст сна: ")
        val text = if (scanner.hasNextLine()) scanner.nextLine() else "Я летал над морем и дышал под водой"
        journalVm.updateDraftTitle(title)
        journalVm.updateDraftTranscript(text)
        journalVm.toggleTag(DreamTag.LUCID)
        journalVm.setLucidityRating(4)
        val saved = journalVm.saveCurrentEntry()
        println("✓ Сон сохранён! Распознано маркеров сна: ${saved.dreamSigns.size} (${saved.dreamSigns.map { it.keyword }})")
    }
}

fun runRealityCheckMenu(realityCheckVm: RealityCheckViewModel, scanner: Scanner) {
    realityCheckVm.loadSchedule()
    val state = realityCheckVm.uiState.value
    println("\n=== 🎯 ПРОВЕРКИ РЕАЛЬНОСТИ (REALITY CHECKS & ADA) ===")
    state.prompts.forEachIndexed { i, p ->
        println("\n${i + 1}. [${p.type}] ${p.title}")
        println("   Инструкция: ${p.instruction}")
        println("   Якорь перспективной памяти: ${p.prospectiveTrigger}")
    }

    println("\nВыполнить проверку реальности сейчас? (y/n): ")
    if (scanner.hasNextLine() && scanner.nextLine().trim().lowercase() == "y") {
        val log = realityCheckVm.logRealityCheck(
            type = RealityCheckType.FINGER_COUNT,
            wasMindful = true,
            doubtReality = true,
            note = "Проверил руки, 5 пальцев, осознанность повышена"
        )
        println("✓ Проверка записана (ID: ${log.id}). Выполнено осознанных проверок за сегодня: ${realityCheckVm.uiState.value.totalMindfulCompletedToday}")
    }
}

fun runProtocolsMenu(scanner: Scanner) {
    println("\n=== 🧠 ПРОТОКОЛЫ ИНДУКЦИИ ===")
    println("1. MILD (Mnemonic Induction of Lucid Dreams) — пошаговый репетиционный гид")
    println("2. SSILD (Senses Initiated Lucid Dream) — сенсорные циклы (Зрение -> Слух -> Осязание)")
    println("3. WBTB (Wake-Back-To-Bed) — расчёт циркадного окна пробуждения")
    print("Выберите протокол: ")

    val choice = if (scanner.hasNextLine()) scanner.nextLine().trim() else "1"
    when (choice) {
        "1" -> {
            val mild = MildProtocolManager()
            val steps = mild.getRehearsalSteps("странные руки")
            println("\n--- ПРОТОКОЛ MILD (вариант Aspy / LaBerge) ---")
            steps.forEach { s ->
                println("\nШаг ${s.stepNumber}: ${s.title} (${s.durationSeconds} сек)")
                println("Описание: ${s.description}")
                if (s.mantraSuggestion != null) {
                    println("Рекомендуемая мантра: \"${s.mantraSuggestion}\"")
                }
            }
        }
        "2" -> {
            val ssild = SsildProtocolManager()
            val routine = ssild.generateRoutine(quickCycleCount = 2, slowCycleCount = 2)
            println("\n--- ПРОТОКОЛ SSILD (Сенсорные циклы) ---")
            println("Всего шагов в последовательности: ${routine.size}")
            routine.take(6).forEach { c ->
                val typeStr = if (c.isFastCycle) "Быстрый (разминка)" else "Медленный (углубление)"
                println("• Цикл ${c.cycleIndex}: ${c.modality.title} [${c.durationSeconds}с] — $typeStr")
                println("  Инструкция: ${c.modality.prompt}")
            }
        }
        "3" -> {
            val wbtb = WbtbScheduler()
            val sched = wbtb.calculateOptimalAlarm(bedtimeMs = System.currentTimeMillis(), preferredSleepHoursBeforeWake = 5.0)
            println("\n--- РАСЧЁТ WBTB ТАЙМИНГА ---")
            println("Целевое время сна до пробуждения: ${sched.sleepDurationHours} ч (после 3-4 циклов NREM/REM)")
            println("Рекомендуемая длительность бодрствования: ${sched.wakefulnessDurationMinutes} мин")
            println("Правила бодрствования:")
            wbtb.getWakeGuidelines().forEach { println("  • $it") }
        }
    }
}

suspend fun runInsightsMenu(insightsVm: InsightsViewModel, profileRepo: UserProfileRepository) {
    insightsVm.loadInsights()
    val state = insightsVm.uiState.value
    val profile = profileRepo.getUserProfile().first()

    println("\n=== 📊 АНАЛИТИКА И ИНСАЙТЫ ===")
    println("Всего ночных сессий: ${state.analytics.totalSessionsCount}")
    println("Процент воспоминания снов (Recall): ${String.format("%.1f", state.analytics.recallPercentage)}%")
    println("Процент осознанных снов: ${String.format("%.1f", state.analytics.lucidPercentage)}%")
    println("Замечено сигналов во сне: ${String.format("%.1f", state.analytics.cueNoticedPercentage)}%")
    println("Пробуждений от сигналов (Wake Spike Rate): ${String.format("%.1f", state.analytics.wakeSpikePercentage)}%")
    println("\nТекущий профиль калибровки:")
    println("  - Базовый пульс: ${String.format("%.1f", profile.baselineHeartRate)} bpm")
    println("  - Интенсивность хаптики: ${(profile.preferredHapticIntensity * 100).toInt()}%")
    println("  - Завершено калибровочных ночей: ${profile.calibrationNightsCompleted}")
}

suspend fun runAutomatedDemo(
    coordinator: PhoneSessionCoordinator,
    sessionRepo: InMemoryNightSessionRepository,
    profileRepo: InMemoryUserProfileRepository,
    healthGateway: MockSamsungHealthDataGateway,
    phoneAudio: TlrAudioEngine,
    journalRepo: InMemoryDreamJournalRepository
) {
    println("\n=======================================================")
    println(" 🚀 ПОЛНЫЙ АВТО-ТЕСТ СИСТЕМЫ (END-TO-END DEMO)")
    println("=======================================================")

    println("[1/4] Проверка журнала снов и маркеров...")
    val signs = journalRepo.extractDreamSigns("Я летал над школой и видел искажённое зеркало")
    println("✓ Распознано маркеров: ${signs.map { "${it.keyword} [${it.category}]" }}")

    println("\n[2/4] Запуск ночной сессии...")
    runInteractiveNightSimulation(coordinator, sessionRepo, profileRepo, healthGateway, phoneAudio)

    println("\n[3/4] Проверка протоколов...")
    val mild = MildProtocolManager()
    println("✓ MILD протокол готов (${mild.getRehearsalSteps().size} шагов).")

    println("\n[4/4] Завершение сквозного тестирования...")
    println("✓ Все модули телефона и часов работают синхронно и без ошибок!")
}
