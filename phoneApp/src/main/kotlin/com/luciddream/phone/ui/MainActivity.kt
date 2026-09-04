package com.luciddream.phone.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.luciddream.data.db.LucidDatabase
import com.luciddream.data.repository.RoomDreamJournalRepository
import com.luciddream.data.repository.RoomNightSessionRepository
import com.luciddream.data.repository.RoomUserProfileRepository
import com.luciddream.data.samsung.MockSamsungHealthDataGateway
import com.luciddream.data.sync.AndroidPhoneWearableTransportGateway
import com.luciddream.phone.audio.AndroidTlrAudioEngine
import com.luciddream.phone.service.PhoneDependencies

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val db = LucidDatabase.getInstance(applicationContext)
        val sessionRepo = RoomNightSessionRepository(db.nightSessionDao())
        val profileRepo = RoomUserProfileRepository(db.userProfileDao())
        val journalRepo = RoomDreamJournalRepository(db.dreamJournalDao())
        val samsungGateway = MockSamsungHealthDataGateway()
        val coordinator = PhoneDependencies.getCoordinator(this)
        val transportGateway = AndroidPhoneWearableTransportGateway(this)
        val audioEngine = AndroidTlrAudioEngine()

        val tonightVm = TonightViewModel(coordinator, sessionRepo, profileRepo)
        val journalVm = DreamJournalViewModel(journalRepo)
        val reviewVm = SleepReviewViewModel(sessionRepo, profileRepo, samsungGateway)

        setContent {
            val darkColors = darkColorScheme(
                primary = Color(0xFF9D84FE),
                onPrimary = Color(0xFF1E1045),
                primaryContainer = Color(0xFF34236A),
                onPrimaryContainer = Color(0xFFEADBFF),
                secondary = Color(0xFF64D2FF),
                background = Color(0xFF0F0E17),
                surface = Color(0xFF1A1829),
                surfaceVariant = Color(0xFF26233B),
                onBackground = Color(0xFFEDEBF5),
                onSurface = Color(0xFFEDEBF5)
            )

            MaterialTheme(colorScheme = darkColors) {
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Bedtime, contentDescription = "Tonight") },
                                label = { Text("Tonight") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.AutoStories, contentDescription = "Journal") },
                                label = { Text("Journal") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Insights, contentDescription = "Review") },
                                label = { Text("Review") }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> TonightScreen(
                            viewModel = tonightVm,
                            audioEngine = audioEngine,
                            transportGateway = transportGateway,
                            modifier = Modifier.padding(innerPadding)
                        )
                        1 -> DreamJournalScreen(
                            viewModel = journalVm,
                            modifier = Modifier.padding(innerPadding)
                        )
                        2 -> SleepReviewScreen(
                            viewModel = reviewVm,
                            sessionRepository = sessionRepo,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
