package fr.sommelle.core.data

import android.content.Context
import fr.sommelle.core.data.db.SommelleDatabase
import fr.sommelle.core.data.db.create

fun SommelleRepository.Companion.create(context: Context): SommelleRepository =
    SommelleRepository(SommelleDatabase.create(context).dao())
