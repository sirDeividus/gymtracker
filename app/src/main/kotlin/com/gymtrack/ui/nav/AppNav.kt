package com.gymtrack.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gymtrack.AppContainer
import com.gymtrack.data.db.SessionEntity
import com.gymtrack.domain.Formatting
import com.gymtrack.ui.appViewModel
import com.gymtrack.ui.history.HistoryScreen
import com.gymtrack.ui.history.HistoryViewModel
import com.gymtrack.ui.history.SessionDetailScreen
import com.gymtrack.ui.history.SessionDetailViewModel
import com.gymtrack.ui.home.HomeScreen
import com.gymtrack.ui.home.HomeViewModel
import com.gymtrack.ui.library.LibraryScreen
import com.gymtrack.ui.library.LibraryViewModel
import com.gymtrack.ui.progress.ExerciseEvolutionScreen
import com.gymtrack.ui.progress.ExerciseEvolutionViewModel
import com.gymtrack.ui.progress.ProgressScreen
import com.gymtrack.ui.progress.ProgressViewModel
import com.gymtrack.ui.routines.RoutineEditScreen
import com.gymtrack.ui.routines.RoutineEditViewModel
import com.gymtrack.ui.routines.RoutinesScreen
import com.gymtrack.ui.routines.RoutinesViewModel
import com.gymtrack.ui.settings.SettingsScreen
import com.gymtrack.ui.settings.SettingsViewModel
import com.gymtrack.ui.theme.Brand
import com.gymtrack.ui.workout.SummaryScreen
import com.gymtrack.ui.workout.SummaryViewModel
import com.gymtrack.ui.workout.WorkoutScreen
import com.gymtrack.ui.workout.WorkoutViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

object Routes {
    const val HOME = "home"
    const val ROUTINES = "routines"
    const val HISTORY = "history"
    const val PROGRESS = "progress"
    const val WORKOUT = "workout"
    const val ROUTINE_EDIT = "routine/{id}"
    const val SUMMARY = "summary/{id}"
    const val SESSION = "session/{id}"
    const val EXERCISE = "exercise/{id}"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"

    fun routine(id: String) = "routine/$id"
    fun summary(id: String) = "summary/$id"
    fun session(id: String) = "session/$id"
    fun exercise(id: String) = "exercise/$id"
}

private class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Inicio", Icons.Default.Home),
    Tab(Routes.ROUTINES, "Rutinas", Icons.AutoMirrored.Filled.ViewList),
    Tab(Routes.HISTORY, "Historial", Icons.Default.History),
    Tab(Routes.PROGRESS, "Progreso", Icons.Default.BarChart),
)

class RootViewModel(c: AppContainer) : ViewModel() {
    /** Sesión en curso, para el acceso rápido «Continuar entrenamiento» desde cualquier pestaña. */
    val active: StateFlow<SessionEntity?> = c.workout.activeSession
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavBackStackEntry.id(): String = arguments?.getString("id").orEmpty()

private val idArg = listOf(navArgument("id") { type = NavType.StringType })

@Composable
fun GymTrackRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBottom = tabs.any { it.route == route }
    val root = appViewModel { RootViewModel(it) }
    val active by root.active.collectAsStateWithLifecycle()
    val c = Brand.colors

    Scaffold(
        containerColor = c.background,
        bottomBar = {
            if (showBottom) {
                Column {
                    active?.let { ActiveBanner(it) { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } } }
                    HorizontalDivider(color = c.outline, thickness = 1.dp)
                    NavigationBar(containerColor = c.background, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { if (route != tab.route) nav.navigateTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label.uppercase(), style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = c.primary, selectedTextColor = c.primary,
                                    indicatorColor = c.primary.copy(alpha = 0.14f),
                                    unselectedIconColor = c.textSecondary, unselectedTextColor = c.textSecondary,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable(Routes.HOME) {
                val vm = appViewModel { HomeViewModel(it) }
                HomeScreen(
                    vm, inner,
                    onOpenWorkout = { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } },
                    onOpenSession = { nav.navigate(Routes.session(it)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenRoutines = { nav.navigateTab(Routes.ROUTINES) },
                )
            }
            composable(Routes.ROUTINES) {
                val vm = appViewModel { RoutinesViewModel(it) }
                RoutinesScreen(
                    vm, inner,
                    onOpen = { nav.navigate(Routes.routine(it)) },
                    onOpenWorkout = { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } },
                )
            }
            composable(Routes.HISTORY) {
                val vm = appViewModel { HistoryViewModel(it) }
                HistoryScreen(vm, inner, onOpenSession = { nav.navigate(Routes.session(it)) })
            }
            composable(Routes.PROGRESS) {
                val vm = appViewModel { ProgressViewModel(it) }
                ProgressScreen(vm, inner, onOpenExercise = { nav.navigate(Routes.exercise(it)) })
            }
            composable(Routes.ROUTINE_EDIT, arguments = idArg) { e ->
                val id = e.id()
                val vm = appViewModel(key = "routine-$id") { RoutineEditViewModel(it, id) }
                val lib = appViewModel { LibraryViewModel(it) }
                RoutineEditScreen(
                    vm, lib, inner, onBack = { nav.popBackStack() },
                    onOpenWorkout = { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } },
                )
            }
            composable(Routes.WORKOUT) {
                val vm = appViewModel { WorkoutViewModel(it) }
                val lib = appViewModel { LibraryViewModel(it) }
                WorkoutScreen(
                    vm, lib, inner,
                    onFinished = { id ->
                        nav.navigate(Routes.summary(id)) { popUpTo(Routes.WORKOUT) { inclusive = true } }
                    },
                    // Seguro frente a carreras: solo sale si seguimos en la pantalla de entrenamiento.
                    onLeave = { if (nav.currentDestination?.route == Routes.WORKOUT) nav.popBackStack() },
                )
            }
            composable(Routes.SUMMARY, arguments = idArg) { e ->
                val id = e.id()
                val vm = appViewModel(key = "summary-$id") { SummaryViewModel(it, id) }
                SummaryScreen(
                    vm, inner,
                    onDone = { nav.popBackStack(Routes.HOME, false) },
                    onDetail = { nav.navigate(Routes.session(it)) },
                )
            }
            composable(Routes.SESSION, arguments = idArg) { e ->
                val id = e.id()
                val vm = appViewModel(key = "session-$id") { SessionDetailViewModel(it, id) }
                SessionDetailScreen(vm, inner, onBack = { nav.popBackStack() }, onOpenExercise = { nav.navigate(Routes.exercise(it)) })
            }
            composable(Routes.EXERCISE, arguments = idArg) { e ->
                val id = e.id()
                val vm = appViewModel(key = "exercise-$id") { ExerciseEvolutionViewModel(it, id) }
                ExerciseEvolutionScreen(vm, inner, onBack = { nav.popBackStack() })
            }
            composable(Routes.LIBRARY) {
                val vm = appViewModel { LibraryViewModel(it) }
                LibraryScreen(vm, onBack = { nav.popBackStack() }, onEvolution = { nav.navigate(Routes.exercise(it)) }, contentPadding = inner)
            }
            composable(Routes.SETTINGS) {
                val vm = appViewModel { SettingsViewModel(it) }
                SettingsScreen(vm, inner, onBack = { nav.popBackStack() }, onOpenLibrary = { nav.navigate(Routes.LIBRARY) })
            }
        }
    }
}

/** Acceso permanente para volver a un entrenamiento sin finalizar (no se pierde nada de lo registrado). */
@Composable
private fun ActiveBanner(session: SessionEntity, onClick: () -> Unit) {
    val c = Brand.colors
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = Formatting.duration(((now - session.startedAt) / 1000).coerceAtLeast(0).toInt())
    Row(
        Modifier.fillMaxWidth().background(c.primary).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = "Entrenamiento en curso, ${session.routineName}, $elapsed. Toca para continuar" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(22.dp))
        Text(
            "  ${session.routineName} · $elapsed", style = MaterialTheme.typography.titleMedium, color = c.onPrimary,
            modifier = Modifier.weight(1f),
        )
        Text("CONTINUAR", style = MaterialTheme.typography.labelLarge, color = c.onPrimary)
    }
}

