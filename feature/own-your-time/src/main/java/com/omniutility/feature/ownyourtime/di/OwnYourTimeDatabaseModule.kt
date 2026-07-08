package com.omniutility.feature.ownyourtime.di

import android.content.Context
import androidx.room.Room
import com.omniutility.feature.ownyourtime.data.db.OwnYourTimeDatabase
import com.omniutility.feature.ownyourtime.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OwnYourTimeDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OwnYourTimeDatabase =
        Room.databaseBuilder(context, OwnYourTimeDatabase::class.java, "own_your_time.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideTaskDao(db: OwnYourTimeDatabase): TaskDao = db.taskDao()
    @Provides fun provideSessionDao(db: OwnYourTimeDatabase): SessionDao = db.sessionDao()
    @Provides fun provideAppConfigDao(db: OwnYourTimeDatabase): AppConfigDao = db.appConfigDao()
    @Provides fun provideUserConfigDao(db: OwnYourTimeDatabase): UserConfigDao = db.userConfigDao()
    @Provides fun provideUserInterestDao(db: OwnYourTimeDatabase): UserInterestDao = db.userInterestDao()
    @Provides fun providePassiveBudgetDao(db: OwnYourTimeDatabase): PassiveBudgetDao = db.passiveBudgetDao()
}
