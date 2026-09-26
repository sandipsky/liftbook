package com.example.liftbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftbook.domain.model.ThemeMode
import com.example.liftbook.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the whole app needs before it draws: the theme the user picked (FR-6.2). */
@HiltViewModel
class MainViewModel @Inject constructor(settingsRepository: SettingsRepository) : ViewModel() {

    /** Null until the setting has been read, so the first frame is already in the right theme. */
    val themeMode: StateFlow<ThemeMode?> = settingsRepository.userPreferences
        .map { it.themeMode }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
