package com.luciddream.phone

import com.luciddream.data.repository.InMemoryUserProfileRepository
import com.luciddream.model.SafetyScreening
import com.luciddream.model.UserProfile
import com.luciddream.phone.ui.ScreeningQuestion
import com.luciddream.phone.ui.ScreeningViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ScreeningViewModelTest {

    private fun viewModel(profile: UserProfile = UserProfile()): Pair<ScreeningViewModel, InMemoryUserProfileRepository> {
        val repo = InMemoryUserProfileRepository(profile)
        return ScreeningViewModel(repo) to repo
    }

    @Test
    fun `submission is blocked until the disclaimer is acknowledged and an age is given`() = runTest {
        val (vm, _) = viewModel()

        assertFalse(vm.uiState.value.canSubmit, "A blank form must not be submittable")

        vm.setAcknowledged(true)
        assertFalse(vm.uiState.value.canSubmit, "Age is still missing")

        vm.updateAge("30")
        assertTrue(vm.uiState.value.canSubmit)

        vm.setAcknowledged(false)
        assertFalse(vm.uiState.value.canSubmit, "Revoking the acknowledgement must block submission")
    }

    @Test
    fun `age field accepts digits only and flags implausible values`() = runTest {
        val (vm, _) = viewModel()

        vm.updateAge("3o лет")
        assertEquals("3", vm.uiState.value.ageInput)

        vm.updateAge("2")
        assertNotNull(vm.uiState.value.ageError, "An age of 2 should be flagged as implausible")

        vm.updateAge("34")
        assertNull(vm.uiState.value.ageError)
        assertEquals(34, vm.uiState.value.ageYears)
    }

    @Test
    fun `a clear screening enables cue modes`() = runTest {
        val (vm, repo) = viewModel()

        vm.updateAge("34")
        vm.setAcknowledged(true)
        val saved = vm.submit()

        assertNotNull(saved)
        assertTrue(saved!!.isComplete)
        assertTrue(saved.allowsCueModes)
        assertTrue(saved.exclusionReasons.isEmpty())
        assertEquals(saved, repo.getUserProfile().first().screening)
    }

    @Test
    fun `a reported condition is persisted and withholds cue modes`() = runTest {
        val (vm, repo) = viewModel()

        vm.updateAge("41")
        vm.setAcknowledged(true)
        vm.setCondition(ScreeningQuestion.PARASOMNIA, true)

        assertTrue(vm.uiState.value.previewExclusions.isNotEmpty(), "The cost must be visible before submitting")

        val saved = vm.submit()
        assertNotNull(saved)
        assertTrue(saved!!.parasomnia)
        assertFalse(saved.allowsCueModes)

        val stored = repo.getUserProfile().first().screening
        assertTrue(stored.isComplete, "A screening that excludes cues is still a completed screening")
        assertFalse(stored.allowsCueModes)
    }

    @Test
    fun `a minor is excluded even with no reported condition`() = runTest {
        val (vm, _) = viewModel()

        vm.updateAge("16")
        vm.setAcknowledged(true)
        val saved = vm.submit()

        assertNotNull(saved)
        assertFalse(saved!!.allowsCueModes)
    }

    @Test
    fun `submitting an invalid form changes nothing`() = runTest {
        val (vm, repo) = viewModel()

        vm.updateAge("34") // acknowledgement deliberately left off

        assertNull(vm.submit())
        assertFalse(repo.getUserProfile().first().screening.isComplete)
    }

    @Test
    fun `an existing screening is prefilled so it can be retaken`() = runTest {
        val existing = SafetyScreening(
            isComplete = true,
            ageYears = 52,
            chronicInsomnia = true,
            seizureDisorder = true,
            acknowledgedNotMedicalDevice = true
        )
        val (vm, _) = viewModel(UserProfile(screening = existing))

        vm.loadExisting()

        val state = vm.uiState.value
        assertEquals("52", state.ageInput)
        assertTrue(state.acknowledgedNotMedicalDevice)
        assertEquals(
            setOf(ScreeningQuestion.CHRONIC_INSOMNIA, ScreeningQuestion.SEIZURE_DISORDER),
            state.reportedConditions
        )
    }

    @Test
    fun `retaking can clear a previously reported condition`() = runTest {
        val existing = SafetyScreening(
            isComplete = true,
            ageYears = 29,
            sleepApnea = true,
            acknowledgedNotMedicalDevice = true
        )
        val (vm, repo) = viewModel(UserProfile(screening = existing))

        vm.loadExisting()
        vm.setCondition(ScreeningQuestion.SLEEP_APNEA, false)
        val saved = vm.submit()

        assertNotNull(saved)
        assertFalse(saved!!.sleepApnea)
        assertTrue(saved.allowsCueModes)
        assertTrue(repo.getUserProfile().first().screening.allowsCueModes)
    }

    @Test
    fun `an unfinished screening is not prefilled`() = runTest {
        val (vm, _) = viewModel(UserProfile(screening = SafetyScreening(ageYears = 44)))

        vm.loadExisting()

        assertEquals("", vm.uiState.value.ageInput, "A never-submitted screening must not look answered")
    }
}
