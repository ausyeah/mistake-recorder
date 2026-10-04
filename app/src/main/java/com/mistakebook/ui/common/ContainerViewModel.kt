package com.mistakebook.ui.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mistakebook.di.AppContainer

// 用容器直接构造 ViewModel，避免引入 DI 框架依赖。
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    container: AppContainer,
    key: String? = null,
    crossinline create: (AppContainer) -> VM
): VM = viewModel(
    key = key,
    factory = viewModelFactory { initializer { create(container) } }
)
