package com.omniutility.feature.finance.di

import android.content.Context
import androidx.room.Room
import com.omniutility.feature.finance.data.db.*
import com.omniutility.feature.finance.security.KeystoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSupportOpenHelperFactory(keystoreManager: KeystoreManager): SupportOpenHelperFactory {
        val passphraseBytes = keystoreManager.getOrCreatePassphrase()
        return SupportOpenHelperFactory(passphraseBytes)
    }

    @Provides
    @Singleton
    fun provideFinanceDatabase(
        @ApplicationContext context: Context,
        supportOpenHelperFactory: SupportOpenHelperFactory
    ): FinanceDatabase {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
        return Room.databaseBuilder(
            context,
            FinanceDatabase::class.java,
            "omni_finance_secure.db"
        )
            .openHelperFactory(supportOpenHelperFactory)
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    fun provideAccountContainerDao(db: FinanceDatabase): AccountContainerDao {
        return db.accountContainerDao()
    }

    @Provides
    fun provideTransactionRecordDao(db: FinanceDatabase): TransactionRecordDao {
        return db.transactionRecordDao()
    }

    @Provides
    fun provideMemoryLookupDao(db: FinanceDatabase): MemoryLookupDao {
        return db.memoryLookupDao()
    }

    @Provides
    fun provideFinancialCompassGoalDao(db: FinanceDatabase): FinancialCompassGoalDao {
        return db.financialCompassGoalDao()
    }
}
