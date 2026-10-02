package io.github.radiqalo.picaris.ui

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTransitionCoordinatorTest {
    private data class Route(
        val name: String,
    ) : NavKey

    private val a = Route("A")
    private val b = Route("B")
    private val c = Route("C")

    @Test
    fun appendingToStackReusesBothCommittedPrefixInstances() {
        val coordinator = NavigationTransitionCoordinator(listOf(a))
        coordinator.commit(listOf(a, b)) {}
        val prefix = coordinator.instances.toList()

        coordinator.commit(listOf(a, b, c)) {}

        assertEquals(listOf(a, b, c), coordinator.instances.map { it.destination })
        assertEquals(prefix.map { it.id }, coordinator.instances.take(2).map { it.id })
        assertSame(prefix[0], coordinator.instances[0])
        assertSame(prefix[1], coordinator.instances[1])
        assertFalse(coordinator.instances.last().id in prefix.map { it.id })
        val ids = coordinator.instances.map { it.id }
        assertEquals(3, ids.toSet().size)
        assertEquals(setOf(coordinator.instances.last().id), coordinator.activeEntries)
    }

    @Test
    fun replacingSuffixAllocatesFreshIdsBeyondTheFirstDifferentRoute() {
        val coordinator = NavigationTransitionCoordinator(listOf(a, b, c))
        val original = coordinator.instances.toList()

        coordinator.commit(listOf(a, Route("D"), c)) {}

        assertSame(original[0], coordinator.instances[0])
        assertNotEquals(original[1].id, coordinator.instances[1].id)
        assertNotEquals(original[2].id, coordinator.instances[2].id)
        assertEquals(c, coordinator.instances[2].destination)
        assertFalse(coordinator.instances[1].id in original.map { it.id })
        assertFalse(coordinator.instances[2].id in original.map { it.id })

        coordinator.commit(listOf(a), returning = true) {}

        assertEquals(listOf(original[0]), coordinator.instances)
        assertEquals(setOf(original[0].id), coordinator.activeEntries)
    }

    @Test
    fun settledWaitsForEveryAnimationToFinish() {
        val coordinator = NavigationTransitionCoordinator(listOf(a))
        coordinator.commit(listOf(a, b)) {}
        val id = coordinator.transitionId
        coordinator.animationStarted("artwork", id)
        coordinator.animationStarted("scene", id)

        coordinator.settled(id)
        assertEquals(NavigationTransitionPhase.Entering, coordinator.phase)
        assertTrue(coordinator.hasAnimations)

        coordinator.animationFinished("artwork", id)
        coordinator.settled(id)
        assertEquals(NavigationTransitionPhase.Entering, coordinator.phase)

        coordinator.animationFinished("scene", id)
        coordinator.settled(id)
        assertFalse(coordinator.hasAnimations)
        assertEquals(NavigationTransitionPhase.Stable, coordinator.phase)
    }

    @Test
    fun settledIgnoresCompletionFromAnEarlierTransition() {
        val coordinator = NavigationTransitionCoordinator(listOf(a))
        coordinator.commit(listOf(a, b)) {}
        val obsoleteId = coordinator.transitionId
        coordinator.commit(listOf(a, b, c)) {}
        val currentId = coordinator.transitionId
        assertNotEquals(obsoleteId, currentId)
        assertFalse(coordinator.hasAnimations)

        coordinator.settled(obsoleteId)

        assertEquals(NavigationTransitionPhase.Entering, coordinator.phase)
        assertEquals(currentId, coordinator.transitionId)

        coordinator.settled(currentId)
        assertEquals(NavigationTransitionPhase.Stable, coordinator.phase)
    }

    @Test
    fun settledCannotFinishPreviewButCanFinishCancelledPreviewRestoration() {
        val coordinator = NavigationTransitionCoordinator(listOf(a, b))
        coordinator.beginPreview()
        val previewId = coordinator.transitionId
        assertFalse(coordinator.hasAnimations)

        coordinator.settled(previewId)

        assertEquals(NavigationTransitionPhase.Previewing, coordinator.phase)
        assertEquals(previewId, coordinator.previewTransitionId)

        coordinator.cancelPreview()
        assertEquals(NavigationTransitionPhase.Restoring, coordinator.phase)
        coordinator.settled(previewId)
        assertEquals(NavigationTransitionPhase.Stable, coordinator.phase)
        assertNull(coordinator.previewTransitionId)
        assertNull(coordinator.returningTransitionId)
    }

    @Test
    fun permitsRequiresAnActiveOwnerAndMatchingTabUnlessThoseArgumentsAreAbsent() {
        val coordinator =
            NavigationTransitionCoordinator(
                listOf(a, b),
                restoredIds = listOf(10L, 20L),
                restoredTab = 2,
            )
        val cases =
            listOf(
                Triple(20L, 2, true),
                Triple(20L, null, true),
                Triple(null, 2, true),
                Triple(null, null, true),
                Triple(10L, 2, false),
                Triple(999L, 2, false),
                Triple(20L, 1, false),
                Triple(null, 1, false),
                Triple(10L, 1, false),
            )
        for ((instance, tab, expected) in cases) {
            assertEquals(
                "instance=$instance, tab=$tab",
                expected,
                coordinator.permits(instance, tab),
            )
        }

        coordinator.commit(listOf(a, b)) {}
        assertEquals(NavigationTransitionPhase.Entering, coordinator.phase)
        coordinator.animationStarted("scene", coordinator.transitionId)
        assertTrue(coordinator.permits(20L, 2))
        coordinator.updateSceneOwners(setOf(10L, 20L))
        assertTrue(coordinator.permits(10L, 2))

        coordinator.beginPreview()
        for ((instance, tab) in cases) {
            assertFalse("preview: instance=$instance, tab=$tab", coordinator.permits(instance, tab))
        }

        val disposed =
            NavigationTransitionCoordinator(
                listOf(a, b),
                restoredIds = listOf(10L, 20L),
                restoredTab = 2,
            )
        disposed.dispose()
        assertEquals(NavigationTransitionPhase.Stable, disposed.phase)
        for ((instance, tab) in cases) {
            assertFalse("disposed: instance=$instance, tab=$tab", disposed.permits(instance, tab))
        }
    }
}
