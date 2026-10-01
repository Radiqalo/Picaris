package io.github.pixivnext.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.navigation3.runtime.NavKey

internal data class NavigationInstance(val id: Long, val destination: NavKey) : NavKey
internal enum class NavigationTransitionPhase { Stable, Entering, Previewing, Returning, Restoring }
internal data class NavigationSceneFrame(
    val scale: Float,
    val offset: Int,
    val opacity: Float,
    val rounding: Float,
    val scaleVelocity: Float = 0f,
    val offsetVelocity: Float = 0f,
    val opacityVelocity: Float = 0f,
    val roundingVelocity: Float = 0f,
)

internal class NavigationTransitionCoordinator(
    initialStack: List<NavKey>,
    restoredIds: List<Long> = emptyList(),
    restoredTab: Int = 0,
) {
    private var nextInstanceId = (restoredIds.maxOrNull() ?: -1L) + 1L
    private var disposed = false
    var transitionId by mutableLongStateOf(0L)
        private set
    var interactionEpoch by mutableLongStateOf(0L)
        private set
    var phase by mutableStateOf(NavigationTransitionPhase.Stable)
        private set
    var instances by mutableStateOf(initialStack.mapIndexed { index, route ->
        NavigationInstance(restoredIds.getOrNull(index) ?: nextInstanceId++, route)
    })
        private set
    var activeEntries by mutableStateOf(setOf(instances.last().id))
        private set
    var activeTab by mutableStateOf(restoredTab)
        private set
    var previewTransitionId: Long? = null
        private set
    var returningTransitionId: Long? = null
        private set
    var beforeCommit: (Long, Set<Long>, NavKey?) -> Unit = { _, _, _ -> }
    var clearFocus: () -> Unit = {}
    var beforePreview: () -> Unit = {}
    var incomingFrame: NavigationSceneFrame? = null
        private set
    private val destinations = instances.associate { it.id to it.destination }.toMutableMap()
    private class SceneSample(val entries: Set<Long>, val routes: List<NavKey>,
        val moving: () -> Boolean, val read: () -> NavigationSceneFrame?) {
        var frame: NavigationSceneFrame? = null
        var timeNanos = 0L
    }
    private val scenes = linkedMapOf<Any, SceneSample>()
    private val animations = mutableStateMapOf<Any, Long>()
    val hasAnimations: Boolean get() = animations.isNotEmpty()
    val hasSceneMotion: Boolean get() = scenes.values.any { it.moving() }

    fun animationStarted(key: Any, id: Long) {
        if (!disposed && id == transitionId) animations[key] = id
    }
    fun animationFinished(key: Any, id: Long) {
        if (animations[key] == id) animations.remove(key)
    }

    fun registerScene(key: Any, entries: Set<Long>, moving: () -> Boolean,
        read: () -> NavigationSceneFrame?) {
        if (disposed) return
        scenes[key] = SceneSample(entries, entries.mapNotNull { destinations[it] }, moving, read)
    }

    fun unregisterScene(key: Any) {
        scenes.remove(key)
        val retainedIds = instances.map { it.id }.toSet() + scenes.values.flatMap { it.entries }
        destinations.keys.retainAll(retainedIds)
    }

    fun sampleScene(key: Any, nanos: Long) {
        val sample = scenes[key] ?: return
        val frame = sample.read() ?: return
        val previous = sample.frame
        val seconds = (nanos - sample.timeNanos) / 1_000_000_000f
        sample.frame = if (previous != null && seconds in 0.001f..0.1f) frame.copy(
            scaleVelocity = (frame.scale - previous.scale) / seconds,
            offsetVelocity = (frame.offset - previous.offset) / seconds,
            opacityVelocity = (frame.opacity - previous.opacity) / seconds,
            roundingVelocity = (frame.rounding - previous.rounding) / seconds,
        ) else frame
        sample.timeNanos = nanos
    }

    private fun sameDestination(first: NavKey, second: NavKey): Boolean = when {
        first is Detail && second is Detail -> first.work.type == second.work.type && first.work.id == second.work.id
        first is Author && second is Author -> first.user.id == second.user.id
        first is Reader && second is Reader -> first.work.type == second.work.type && first.work.id == second.work.id
        else -> first == second
    }

    fun commit(stack: List<NavKey>, returning: Boolean = false, destination: NavKey? = null, mutation: () -> Unit) {
        if (disposed) return
        val previous = instances
        val previousOwners = activeEntries
        incomingFrame = if (returning || destination == null) null else scenes.values.lastOrNull { sample ->
            sample.routes.any { sameDestination(it, destination) }
        }?.let { sample ->
            sample.read()?.copy(
                scaleVelocity = sample.frame?.scaleVelocity ?: 0f,
                offsetVelocity = sample.frame?.offsetVelocity ?: 0f,
                opacityVelocity = sample.frame?.opacityVelocity ?: 0f,
                roundingVelocity = sample.frame?.roundingVelocity ?: 0f,
            )
        }
        transitionId++
        interactionEpoch++
        clearFocus()
        beforeCommit(transitionId, previousOwners, if (returning) previous.lastOrNull()?.destination else destination)
        mutation()
        val prefix = previous.indices.takeWhile { index ->
            index < stack.size && previous[index].destination == stack[index]
        }.size
        instances = stack.mapIndexed { index, route ->
            if (index < prefix) previous[index] else NavigationInstance(nextInstanceId++, route)
        }
        instances.forEach { destinations[it.id] = it.destination }
        activeEntries = instances.lastOrNull()?.let { setOf(it.id) }.orEmpty()
        phase = if (returning) NavigationTransitionPhase.Returning else NavigationTransitionPhase.Entering
        returningTransitionId = if (returning && previewTransitionId != null) transitionId else null
        previewTransitionId = null
    }

    fun updateSceneOwners(owners: Set<Long>) {
        if (disposed) return
        if (activeEntries != owners) activeEntries = owners
    }

    fun usesZoom(entries: Set<Long>): Boolean = entries.any {
        destinations[it] is Detail || destinations[it] is Reader
    }

    fun beginPreview() {
        if (disposed || phase == NavigationTransitionPhase.Previewing) return
        beforePreview()
        transitionId++
        interactionEpoch++
        previewTransitionId = transitionId
        returningTransitionId = null
        phase = NavigationTransitionPhase.Previewing
        clearFocus()
    }

    fun cancelPreview() {
        if (phase != NavigationTransitionPhase.Previewing) return
        phase = NavigationTransitionPhase.Restoring
        interactionEpoch++
    }

    fun selectTab(tab: Int, change: () -> Unit) {
        if (disposed) return
        if (tab == activeTab) {
            change()
            return
        }
        transitionId++
        interactionEpoch++
        clearFocus()
        beforeCommit(transitionId, activeEntries, null)
        activeTab = tab
        incomingFrame = null
        phase = NavigationTransitionPhase.Entering
        previewTransitionId = null
        returningTransitionId = null
        change()
    }

    fun settled(id: Long) {
        if (disposed || id != transitionId || phase == NavigationTransitionPhase.Previewing || hasAnimations) return
        phase = NavigationTransitionPhase.Stable
        previewTransitionId = null
        returningTransitionId = null
    }

    fun permits(instanceId: Long?, tab: Int? = null): Boolean =
        !disposed && phase != NavigationTransitionPhase.Previewing &&
            (instanceId == null || instanceId in activeEntries) && (tab == null || tab == activeTab)

    fun dispose() {
        disposed = true
        interactionEpoch++
        activeEntries = emptySet()
        scenes.clear()
        destinations.clear()
        animations.clear()
        beforeCommit = { _, _, _ -> }
        beforePreview = {}
        clearFocus = {}
    }
}

internal val LocalNavigationCoordinator = staticCompositionLocalOf<NavigationTransitionCoordinator?> { null }
internal val LocalNavigationInstance = staticCompositionLocalOf<Long?> { null }
internal val LocalNavigationTab = staticCompositionLocalOf<Int?> { null }

@Composable
internal fun navigationPermission(): () -> Boolean {
    val coordinator = LocalNavigationCoordinator.current
    val instance = LocalNavigationInstance.current
    val tab = LocalNavigationTab.current
    return { coordinator?.permits(instance, tab) != false }
}

@Composable
internal fun guardedNavigation(navigate: (NavKey) -> Unit): (NavKey) -> Unit {
    val permitted = navigationPermission()
    return { if (permitted()) navigate(it) }
}

@Composable
internal fun guardedBack(back: () -> Unit): () -> Unit {
    val permitted = navigationPermission()
    return { if (permitted()) back() }
}

@Composable
internal fun Modifier.navigationInteractionGate(permitted: () -> Boolean): Modifier {
    val permission = rememberUpdatedState(permitted)
    val coordinator = LocalNavigationCoordinator.current
    return then(NavigationInteractionElement(coordinator, permission))
}

private data class NavigationInteractionElement(
    val coordinator: NavigationTransitionCoordinator?,
    val permission: State<() -> Boolean>,
) : ModifierNodeElement<NavigationInteractionNode>() {
    override fun create() = NavigationInteractionNode(coordinator, permission)
    override fun update(node: NavigationInteractionNode) {
        node.coordinator = coordinator
        node.permission = permission
    }
    override fun InspectorInfo.inspectableProperties() { name = "navigationInteractionGate" }
}

private class NavigationInteractionNode(
    var coordinator: NavigationTransitionCoordinator?,
    var permission: State<() -> Boolean>,
) : Modifier.Node(), PointerInputModifierNode {
    private data class Claim(val epoch: Long?, val accepted: Boolean)
    private val claims = mutableMapOf<PointerId, Claim>()

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Initial) return
        pointerEvent.changes.forEach { change ->
            if (change.pressed && !change.previousPressed)
                claims[change.id] = Claim(coordinator?.interactionEpoch, permission.value())
            val claim = claims[change.id]
            if (!permission.value() || coordinator?.phase == NavigationTransitionPhase.Previewing ||
                (claim != null &&
                    (!claim.accepted || claim.epoch != coordinator?.interactionEpoch))
            ) change.consume()
            if (!change.pressed) claims.remove(change.id)
        }
    }

    override fun onCancelPointerInput() { claims.clear() }
}
