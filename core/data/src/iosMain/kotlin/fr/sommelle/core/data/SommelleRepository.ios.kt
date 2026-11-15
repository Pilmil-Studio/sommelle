package fr.sommelle.core.data

import fr.sommelle.core.data.db.SommelleDatabase
import fr.sommelle.core.data.db.create

fun SommelleRepository.Companion.create(): SommelleRepository =
    SommelleRepository(SommelleDatabase.create().dao())
