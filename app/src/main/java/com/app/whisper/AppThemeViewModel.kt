package com.app.whisper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.whisper.feature.settings.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppThemeViewModel @Inject constructor(
    private val preferences: PreferencesManager
) : ViewModel() {
    private val _isDark = MutableStateFlow(false)
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.darkThemeFlow.collectLatest { value ->
                _isDark.value = value
            }
        }
    }
}

