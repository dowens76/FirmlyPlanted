package com.firmlyplanted.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Small helper so each screen can wire its ViewModel straight from AppContainer, no DI framework. */
inline fun <reified VM : ViewModel> simpleFactory(crossinline create: () -> VM) = viewModelFactory {
    initializer { create() }
}
