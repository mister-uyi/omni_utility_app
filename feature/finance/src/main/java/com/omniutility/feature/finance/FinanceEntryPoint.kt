package com.omniutility.feature.finance

import com.omniutility.feature.finance.data.repository.FinanceRepository
import com.omniutility.feature.finance.platform.AICoreManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FinanceEntryPoint {
    fun financeRepository(): FinanceRepository
    fun aiCoreManager(): AICoreManager
}
