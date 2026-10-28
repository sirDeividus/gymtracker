package com.gymtrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gymtrack.AppContainer
import com.gymtrack.GymTrackApp

/** Crea ViewModels con el contenedor de dependencias de la aplicación. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val app = LocalContext.current.applicationContext as GymTrackApp
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app.container) } })
}
