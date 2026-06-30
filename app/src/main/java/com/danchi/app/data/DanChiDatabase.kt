package com.danchi.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DictionaryCacheEntity::class,
        WordEntity::class,
        WordbookEntity::class,
        CardEntity::class,
        ReviewLogEntity::class,
        NoteEntity::class,
        UserFsrsSettingEntity::class,
        LocalWordbookEntity::class,
        LocalWordbookWordEntity::class,
        LearningWordEntity::class,
        StudyPlanEntity::class,
        StudyPlanItemEntity::class,
        StudySessionEntity::class,
        StudySessionItemEntity::class,
        UserMasteredWordEntity::class,
        UserFavoriteWordEntity::class,
        UserWordNoteEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class DanChiDatabase : RoomDatabase() {
    abstract fun dictionaryCacheDao(): DictionaryCacheDao
    abstract fun wordDao(): WordDao
    abstract fun wordbookDao(): WordbookDao
    abstract fun cardDao(): CardDao
    abstract fun reviewDao(): ReviewDao
    abstract fun noteDao(): NoteDao
    abstract fun userFsrsSettingDao(): UserFsrsSettingDao
    abstract fun learningStateDao(): LearningStateDao
}
