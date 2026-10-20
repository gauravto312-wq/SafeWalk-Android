package com.example.safewalk.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.domain.model.Emergency
import com.example.safewalk.domain.model.User
import com.example.safewalk.domain.repository.AuthRepository
import com.example.safewalk.domain.repository.EmergencyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val emergencyRepository: EmergencyRepository = ServiceLocator.emergencyRepository,
    private val authRepository: AuthRepository = ServiceLocator.authRepository
) : ViewModel() {

    val currentUser: StateFlow<User?> = authRepository.currentUser

    private val _history = MutableStateFlow<List<Emergency>>(emptyList())
    val history: StateFlow<List<Emergency>> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    loadHistory(user.id)
                }
            }
        }
    }

    fun loadHistory(userId: String? = currentUser.value?.id) {
        val targetId = userId ?: return
        viewModelScope.launch {
            emergencyRepository.getEmergencyHistory(targetId).collect { list ->
                _history.value = list
            }
        }
    }
}
