package com.example.liftbook.di

import android.content.Context
import androidx.room.Room
import com.example.liftbook.data.local.LiftBookDatabase
import com.example.liftbook.data.local.dao.BodyWeightDao
import com.example.liftbook.data.local.dao.ExerciseDao
import com.example.liftbook.data.local.dao.ProgressDao
import com.example.liftbook.data.local.dao.RoutineDao
import com.example.liftbook.data.local.dao.SetDao
import com.example.liftbook.data.local.dao.WorkoutDao
import com.example.liftbook.data.local.seed.ExerciseSeedCallback
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, clock: Clock): LiftBookDatabase =
        Room.databaseBuilder(context, LiftBookDatabase::class.java, LiftBookDatabase.NAME)
            .addCallback(ExerciseSeedCallback(clock))
            .build()

    @Provides
    fun provideExerciseDao(database: LiftBookDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    fun provideRoutineDao(database: LiftBookDatabase): RoutineDao = database.routineDao()

    @Provides
    fun provideWorkoutDao(database: LiftBookDatabase): WorkoutDao = database.workoutDao()

    @Provides
    fun provideSetDao(database: LiftBookDatabase): SetDao = database.setDao()

    @Provides
    fun provideProgressDao(database: LiftBookDatabase): ProgressDao = database.progressDao()

    @Provides
    fun provideBodyWeightDao(database: LiftBookDatabase): BodyWeightDao = database.bodyWeightDao()
}
