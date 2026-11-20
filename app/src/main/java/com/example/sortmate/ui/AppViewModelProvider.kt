package com.example.sortmate.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.sortmate.SortMateApplication
import com.example.sortmate.di.AppContainer

/**
 * Builds a factory whose initializer is registered for the concrete ViewModel
 * type, which is what ViewModelProvider looks up. Keep the reified type or the
 * factory will only register `ViewModel` and throw "No initializer set".
 */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val application = this[APPLICATION_KEY] as SortMateApplication
        create(application.container)
    }
}
