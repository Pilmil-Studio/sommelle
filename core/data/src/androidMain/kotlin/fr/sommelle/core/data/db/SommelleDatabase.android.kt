package fr.sommelle.core.data.db

import android.content.Context
import androidx.room.Room

fun SommelleDatabase.Companion.create(context: Context): SommelleDatabase =
    Room.databaseBuilder<SommelleDatabase>(
        context = context.applicationContext,
        name = context.applicationContext.getDatabasePath("sommelle.db").absolutePath,
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()
