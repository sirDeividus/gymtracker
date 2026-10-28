package com.gymtrack.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtrack.AppContainer
import com.gymtrack.data.db.BodyWeightEntity
import com.gymtrack.data.db.ProgressPhotoEntity
import com.gymtrack.data.settings.UserSettings
import com.gymtrack.domain.PhotoPose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

data class PhotoMeta(val pose: PhotoPose, val weightKg: Double?, val note: String)

data class BodyState(
    val loading: Boolean = true,
    val photos: List<ProgressPhotoEntity> = emptyList(),
    val weights: List<BodyWeightEntity> = emptyList(),
    val settings: UserSettings = UserSettings(),
)

class BodyViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<BodyState> = combine(c.body.photos, c.body.weights, c.settings.settings) { p, w, s ->
        BodyState(false, p, w, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyState())

    /** Mensaje de resultado o error para el usuario. */
    val message = MutableStateFlow<String?>(null)

    fun fileOf(p: ProgressPhotoEntity): File = c.body.fileOf(p)

    fun addPhoto(open: () -> InputStream?, meta: PhotoMeta, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            val r = c.body.addPhoto(open, meta.pose, meta.weightKg, meta.note)
            if (r.isFailure) message.value = "No se pudo guardar la foto: ${r.exceptionOrNull()?.message ?: "error desconocido"}"
            onFinished()
        }
    }

    fun deletePhoto(id: String) {
        viewModelScope.launch { c.body.deletePhoto(id) }
    }

    fun addWeight(kg: Double) {
        viewModelScope.launch { if (!c.body.addWeight(kg)) message.value = "El peso debe estar entre 20 y 500 kg" }
    }

    fun deleteWeight(id: String) {
        viewModelScope.launch { c.body.deleteWeight(id) }
    }

    fun setMessage(text: String?) { message.value = text }
}
