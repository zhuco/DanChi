package com.danchi.app

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.danchi.app.data.DanChiDatabase
import com.danchi.app.data.DanChiDictionaryRepository
import com.danchi.app.data.DanChiRepository
import com.danchi.app.data.LocalEcdictDataSource
import com.danchi.app.data.RemoteEcdictDataSource

class DanChiApplication : Application() {
    lateinit var repository: DanChiRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(
            this,
            DanChiDatabase::class.java,
            "danchi.db"
        )
            .addMigrations(Migration1To2)
            .addMigrations(Migration2To3)
            .addMigrations(Migration3To4)
            .addMigrations(Migration4To5)
            .addMigrations(Migration5To6)
            .addMigrations(Migration6To7)
            .addMigrations(Migration7To8)
            .addMigrations(Migration8To9)
            .addMigrations(Migration9To10)
            .addMigrations(Migration10To11)
            .addMigrations(Migration11To12)
            .fallbackToDestructiveMigration(false)
            .build()

        val dictionaryRepository = DanChiDictionaryRepository(
            cacheDao = database.dictionaryCacheDao(),
            local = LocalEcdictDataSource(this),
            remote = RemoteEcdictDataSource()
        )

        repository = DanChiRepository(
            context = this,
            wordDao = database.wordDao(),
            wordbookDao = database.wordbookDao(),
            cardDao = database.cardDao(),
            reviewDao = database.reviewDao(),
            noteDao = database.noteDao(),
            userFsrsSettingDao = database.userFsrsSettingDao(),
            learningStateDao = database.learningStateDao(),
            dictionaryRepository = dictionaryRepository
        )
    }

    private object Migration1To2 : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `word_study_records` (
                    `wordId` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `todayRememberCount` INTEGER NOT NULL,
                    `requiredRememberCount` INTEGER NOT NULL,
                    `todayForgetCount` INTEGER NOT NULL,
                    `todayWrongChoiceCount` INTEGER NOT NULL,
                    `previewSeen` INTEGER NOT NULL,
                    `previewSeenAt` INTEGER,
                    `reviewLevel` INTEGER NOT NULL,
                    `intervalDays` INTEGER NOT NULL,
                    `lastShownAt` INTEGER NOT NULL,
                    `nextDueAt` INTEGER NOT NULL,
                    `lastShownCardIndex` INTEGER NOT NULL,
                    `nextDueCardIndex` INTEGER NOT NULL,
                    `firstLearnedAt` INTEGER,
                    `lastReviewedAt` INTEGER,
                    `completedTodayAt` INTEGER,
                    `studyDayEpoch` INTEGER NOT NULL,
                    PRIMARY KEY(`wordId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_study_records_status` ON `word_study_records` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_word_study_records_nextDueAt` ON `word_study_records` (`nextDueAt`)")
        }
    }

    private object Migration2To3 : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `wordbooks` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `sourceFile` TEXT NOT NULL,
                    `assetPath` TEXT NOT NULL,
                    `version` TEXT NOT NULL,
                    `wordCount` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wordbooks_title` ON `wordbooks` (`title`)")
            db.execSQL(
                """
                INSERT OR IGNORE INTO `wordbooks`
                    (`id`, `title`, `description`, `sourceFile`, `assetPath`, `version`, `wordCount`, `sortOrder`, `updatedAt`)
                VALUES
                    ('zhongkao', '初中中考', '中考核心词汇离线词库', '初中中考.txt', 'wordbooks/zhongkao_words.json', '1.1.0', 1974, 0, 0)
                """.trimIndent()
            )
            db.execSQL("ALTER TABLE `words` ADD COLUMN `wordbookId` TEXT NOT NULL DEFAULT 'zhongkao'")
            db.execSQL("DROP INDEX IF EXISTS `index_words_text`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_wordbookId` ON `words` (`wordbookId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_words_wordbookId_text` ON `words` (`wordbookId`, `text`)")
        }
    }

    private object Migration3To4 : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `words` ADD COLUMN `phonetic` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `words` ADD COLUMN `example` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `words` ADD COLUMN `exampleCn` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `words` ADD COLUMN `root` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `words` ADD COLUMN `synonyms` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `words` ADD COLUMN `collocations` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `words` ADD COLUMN `memoryTip` TEXT NOT NULL DEFAULT ''")
        }
    }

    private object Migration4To5 : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `words` ADD COLUMN `pos` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `words` ADD COLUMN `meaningsJson` TEXT NOT NULL DEFAULT ''")
        }
    }

    private object Migration5To6 : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `words` ADD COLUMN `level` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `words` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cards` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `wordId` TEXT NOT NULL,
                    `cardType` TEXT NOT NULL,
                    `state` TEXT NOT NULL,
                    `dueAt` INTEGER NOT NULL,
                    `stability` REAL,
                    `difficulty` REAL,
                    `elapsedDays` INTEGER NOT NULL,
                    `scheduledDays` INTEGER NOT NULL,
                    `reps` INTEGER NOT NULL,
                    `lapses` INTEGER NOT NULL,
                    `fsrsStep` INTEGER,
                    `lastReviewAt` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cards_userId_wordId_cardType` ON `cards` (`userId`, `wordId`, `cardType`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_userId_state_dueAt` ON `cards` (`userId`, `state`, `dueAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_wordId` ON `cards` (`wordId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `user_fsrs_setting` (
                    `userId` TEXT NOT NULL,
                    `desiredRetention` REAL NOT NULL,
                    `maximumInterval` INTEGER NOT NULL,
                    `enableFuzz` INTEGER NOT NULL,
                    `enableShortTerm` INTEGER NOT NULL,
                    `learningSteps` TEXT NOT NULL,
                    `relearningSteps` TEXT NOT NULL,
                    `fsrsParamsJson` TEXT NOT NULL,
                    `dailyMinutes` INTEGER NOT NULL,
                    `maxNewCardsPerDay` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`userId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `user_fsrs_setting`
                    (`userId`, `desiredRetention`, `maximumInterval`, `enableFuzz`, `enableShortTerm`, `learningSteps`, `relearningSteps`, `fsrsParamsJson`, `dailyMinutes`, `maxNewCardsPerDay`, `updatedAt`)
                VALUES
                    ('local', 0.9, 36500, 1, 1, '1m,10m', '10m', '', 10, 20, 0)
                """.trimIndent()
            )
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `userId` TEXT NOT NULL DEFAULT 'local'")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `cardId` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `reviewedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `rating` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `elapsedDays` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `scheduledDays` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `questionType` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `optionCount` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `isCorrect` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `firstAnswerCorrect` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `usedHint` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `answerSnapshot` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `stateBefore` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `stateAfter` TEXT NOT NULL DEFAULT ''")
        }
    }

    private object Migration6To7 : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `dictionary_cache` (
                    `wordKey` TEXT NOT NULL,
                    `word` TEXT NOT NULL,
                    `payloadJson` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `cachedAt` INTEGER NOT NULL,
                    `expiresAt` INTEGER NOT NULL,
                    PRIMARY KEY(`wordKey`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dictionary_cache_expiresAt` ON `dictionary_cache` (`expiresAt`)")
        }
    }

    private object Migration7To8 : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `today_fsrs_session_items` (
                    `userId` TEXT NOT NULL,
                    `wordbookId` TEXT NOT NULL,
                    `studyDayEpoch` INTEGER NOT NULL,
                    `cardId` INTEGER NOT NULL,
                    `wordId` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `planNewLimit` INTEGER NOT NULL,
                    `wordOrder` TEXT NOT NULL,
                    `completedAt` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`userId`, `wordbookId`, `studyDayEpoch`, `cardId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_today_fsrs_session_items_userId_wordbookId_studyDayEpoch_position` " +
                    "ON `today_fsrs_session_items` (`userId`, `wordbookId`, `studyDayEpoch`, `position`)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_today_fsrs_session_items_cardId` ON `today_fsrs_session_items` (`cardId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_today_fsrs_session_items_wordId` ON `today_fsrs_session_items` (`wordId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_today_fsrs_session_items_completedAt` ON `today_fsrs_session_items` (`completedAt`)")
        }
    }

    private object Migration8To9 : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `words` ADD COLUMN `wordKey` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `words` ADD COLUMN `learningWordId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE `words` SET `wordKey` = lower(trim(`text`)) WHERE `wordKey` = ''")
            db.execSQL("UPDATE `words` SET `learningWordId` = `wordbookId` || ':' || `wordKey` WHERE `learningWordId` = ''")

            db.execSQL("ALTER TABLE `cards` ADD COLUMN `wordKey` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `learningWordId` TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """
                UPDATE `cards`
                SET
                    `wordKey` = COALESCE((SELECT `wordKey` FROM `words` WHERE `words`.`id` = `cards`.`wordId`), ''),
                    `learningWordId` = COALESCE((SELECT `learningWordId` FROM `words` WHERE `words`.`id` = `cards`.`wordId`), `wordId`)
                WHERE `wordKey` = '' OR `learningWordId` = ''
                """.trimIndent()
            )

            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `wordKey` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `learningWordId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `wordbookId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `sessionId` TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """
                UPDATE `review_logs`
                SET
                    `wordKey` = COALESCE((SELECT `wordKey` FROM `words` WHERE `words`.`id` = `review_logs`.`wordId`), ''),
                    `learningWordId` = COALESCE((SELECT `learningWordId` FROM `words` WHERE `words`.`id` = `review_logs`.`wordId`), `wordId`),
                    `wordbookId` = COALESCE((SELECT `wordbookId` FROM `words` WHERE `words`.`id` = `review_logs`.`wordId`), '')
                WHERE `wordKey` = '' OR `learningWordId` = '' OR `wordbookId` = ''
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_wordId` ON `review_logs` (`wordId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_wordKey` ON `review_logs` (`wordKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_learningWordId` ON `review_logs` (`learningWordId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_wordbookId` ON `review_logs` (`wordbookId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_sessionId` ON `review_logs` (`sessionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_createdAt` ON `review_logs` (`createdAt`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `local_wordbooks` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `sourceFile` TEXT NOT NULL,
                    `assetPath` TEXT NOT NULL,
                    `version` TEXT NOT NULL,
                    `wordCount` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `packageChecksum` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `downloadedAt` INTEGER NOT NULL,
                    `activatedAt` INTEGER,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_wordbooks_status` ON `local_wordbooks` (`status`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `local_wordbook_words` (
                    `wordbookId` TEXT NOT NULL,
                    `wordKey` TEXT NOT NULL,
                    `word` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    `book` TEXT NOT NULL,
                    `unit` TEXT NOT NULL,
                    `level` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`wordbookId`, `wordKey`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_wordbook_words_wordKey` ON `local_wordbook_words` (`wordKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_wordbook_words_wordbookId_sortOrder` ON `local_wordbook_words` (`wordbookId`, `sortOrder`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `learning_words` (
                    `id` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `wordbookId` TEXT NOT NULL,
                    `wordKey` TEXT NOT NULL,
                    `wordId` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `meaning` TEXT NOT NULL,
                    `pos` TEXT NOT NULL,
                    `meaningsJson` TEXT NOT NULL,
                    `phonetic` TEXT NOT NULL,
                    `example` TEXT NOT NULL,
                    `exampleCn` TEXT NOT NULL,
                    `root` TEXT NOT NULL,
                    `synonyms` TEXT NOT NULL,
                    `collocations` TEXT NOT NULL,
                    `memoryTip` TEXT NOT NULL,
                    `book` TEXT NOT NULL,
                    `unit` TEXT NOT NULL,
                    `level` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `dueAt` INTEGER NOT NULL,
                    `learnedAt` INTEGER,
                    `reviewCount` INTEGER NOT NULL,
                    `lapseCount` INTEGER NOT NULL,
                    `stability` REAL NOT NULL,
                    `difficulty` REAL NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_words_userId_wordbookId` ON `learning_words` (`userId`, `wordbookId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_words_userId_wordKey` ON `learning_words` (`userId`, `wordKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_words_status` ON `learning_words` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_words_dueAt` ON `learning_words` (`dueAt`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `study_plans` (
                    `id` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `wordbookId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `dailyNewWords` INTEGER NOT NULL,
                    `reviewLimit` INTEGER NOT NULL,
                    `wordOrder` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_plans_userId_wordbookId_status` ON `study_plans` (`userId`, `wordbookId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_plans_updatedAt` ON `study_plans` (`updatedAt`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `study_plan_items` (
                    `planId` TEXT NOT NULL,
                    `learningWordId` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `wordbookId` TEXT NOT NULL,
                    `wordKey` TEXT NOT NULL,
                    `wordId` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `assignedAt` INTEGER NOT NULL,
                    `startedAt` INTEGER,
                    `completedAt` INTEGER,
                    `skippedAt` INTEGER,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`planId`, `learningWordId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_plan_items_userId_wordbookId_status` ON `study_plan_items` (`userId`, `wordbookId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_plan_items_userId_wordKey` ON `study_plan_items` (`userId`, `wordKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_plan_items_planId_position` ON `study_plan_items` (`planId`, `position`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `study_sessions` (
                    `id` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `wordbookId` TEXT NOT NULL,
                    `planId` TEXT NOT NULL,
                    `studyDayEpoch` INTEGER NOT NULL,
                    `mode` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `currentPosition` INTEGER NOT NULL,
                    `totalCount` INTEGER NOT NULL,
                    `completedCount` INTEGER NOT NULL,
                    `startedAt` INTEGER NOT NULL,
                    `lastActiveAt` INTEGER NOT NULL,
                    `finishedAt` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_sessions_userId_wordbookId_studyDayEpoch_mode` ON `study_sessions` (`userId`, `wordbookId`, `studyDayEpoch`, `mode`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_sessions_status` ON `study_sessions` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_sessions_lastActiveAt` ON `study_sessions` (`lastActiveAt`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `study_session_items` (
                    `sessionId` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `userId` TEXT NOT NULL,
                    `wordbookId` TEXT NOT NULL,
                    `learningWordId` TEXT NOT NULL,
                    `wordKey` TEXT NOT NULL,
                    `wordId` TEXT NOT NULL,
                    `cardId` INTEGER NOT NULL,
                    `questionType` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `answeredAt` INTEGER,
                    `result` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`sessionId`, `position`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_session_items_userId_wordbookId_status` ON `study_session_items` (`userId`, `wordbookId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_session_items_learningWordId` ON `study_session_items` (`learningWordId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_session_items_wordKey` ON `study_session_items` (`wordKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_session_items_wordId` ON `study_session_items` (`wordId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_session_items_cardId` ON `study_session_items` (`cardId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `user_mastered_words` (
                    `userId` TEXT NOT NULL,
                    `wordKey` TEXT NOT NULL,
                    `word` TEXT NOT NULL,
                    `firstWordbookId` TEXT NOT NULL,
                    `firstLearningWordId` TEXT NOT NULL,
                    `masteredAt` INTEGER NOT NULL,
                    `confidence` REAL NOT NULL,
                    `source` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`userId`, `wordKey`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_mastered_words_wordKey` ON `user_mastered_words` (`wordKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_mastered_words_masteredAt` ON `user_mastered_words` (`masteredAt`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `user_favorite_words` (
                    `userId` TEXT NOT NULL,
                    `wordKey` TEXT NOT NULL,
                    `word` TEXT NOT NULL,
                    `firstWordbookId` TEXT NOT NULL,
                    `firstLearningWordId` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`userId`, `wordKey`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_favorite_words_wordKey` ON `user_favorite_words` (`wordKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_favorite_words_updatedAt` ON `user_favorite_words` (`updatedAt`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `user_word_notes` (
                    `userId` TEXT NOT NULL,
                    `wordKey` TEXT NOT NULL,
                    `word` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `firstWordbookId` TEXT NOT NULL,
                    `firstLearningWordId` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`userId`, `wordKey`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_word_notes_wordKey` ON `user_word_notes` (`wordKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_word_notes_updatedAt` ON `user_word_notes` (`updatedAt`)")

            db.execSQL(
                """
                INSERT OR REPLACE INTO `local_wordbooks`
                    (`id`, `title`, `description`, `sourceFile`, `assetPath`, `version`, `wordCount`, `sortOrder`, `packageChecksum`, `status`, `downloadedAt`, `activatedAt`, `updatedAt`)
                SELECT
                    `id`, `title`, `description`, `sourceFile`, `assetPath`, `version`, `wordCount`, `sortOrder`, '', 'active', `updatedAt`, `updatedAt`, `updatedAt`
                FROM `wordbooks`
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO `local_wordbook_words`
                    (`wordbookId`, `wordKey`, `word`, `sortOrder`, `book`, `unit`, `level`, `source`, `createdAt`, `updatedAt`)
                SELECT
                    `wordbookId`, `wordKey`, `text`, rowid, `book`, `unit`, `level`, `source`, `createdAt`, `updatedAt`
                FROM `words`
                WHERE `wordKey` != ''
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `user_mastered_words`
                    (`userId`, `wordKey`, `word`, `firstWordbookId`, `firstLearningWordId`, `masteredAt`, `confidence`, `source`, `updatedAt`)
                SELECT
                    'local', `wordKey`, `text`, `wordbookId`, `learningWordId`,
                    COALESCE(`learnedAt`, `updatedAt`, 0), 1.0, 'word_status', COALESCE(`updatedAt`, 0)
                FROM `words`
                WHERE `status` = 'Mastered' AND `wordKey` != ''
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `user_favorite_words`
                    (`userId`, `wordKey`, `word`, `firstWordbookId`, `firstLearningWordId`, `createdAt`, `updatedAt`)
                SELECT
                    'local', `wordKey`, `text`, `wordbookId`, `learningWordId`, COALESCE(`updatedAt`, 0), COALESCE(`updatedAt`, 0)
                FROM `words`
                WHERE `isFavorite` = 1 AND `wordKey` != ''
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO `user_word_notes`
                    (`userId`, `wordKey`, `word`, `body`, `firstWordbookId`, `firstLearningWordId`, `createdAt`, `updatedAt`)
                SELECT
                    'local', `words`.`wordKey`, `words`.`text`, `notes`.`body`, `words`.`wordbookId`, `words`.`learningWordId`, `notes`.`updatedAt`, `notes`.`updatedAt`
                FROM `notes`
                INNER JOIN `words` ON `words`.`id` = `notes`.`wordId`
                WHERE length(`notes`.`body`) > 0 AND `words`.`wordKey` != ''
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO `learning_words`
                    (`id`, `userId`, `wordbookId`, `wordKey`, `wordId`, `text`, `meaning`, `pos`, `meaningsJson`, `phonetic`, `example`, `exampleCn`, `root`, `synonyms`, `collocations`, `memoryTip`, `book`, `unit`, `level`, `source`, `status`, `dueAt`, `learnedAt`, `reviewCount`, `lapseCount`, `stability`, `difficulty`, `createdAt`, `updatedAt`)
                SELECT
                    `learningWordId`, 'local', `wordbookId`, `wordKey`, `id`, `text`, `meaning`, `pos`, `meaningsJson`, `phonetic`, `example`, `exampleCn`, `root`, `synonyms`, `collocations`, `memoryTip`, `book`, `unit`, `level`, `source`,
                    `status`, `dueAt`, `learnedAt`, `reviewCount`, `lapseCount`, `stability`, `difficulty`, `createdAt`, `updatedAt`
                FROM `words`
                WHERE `wordKey` != ''
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO `study_plans`
                    (`id`, `userId`, `wordbookId`, `title`, `dailyNewWords`, `reviewLimit`, `wordOrder`, `status`, `createdAt`, `updatedAt`)
                SELECT
                    'local:' || `id` || ':default', 'local', `id`, `title`, 10, 50, 'Alphabetical', 'active', `updatedAt`, `updatedAt`
                FROM `wordbooks`
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO `study_plan_items`
                    (`planId`, `learningWordId`, `userId`, `wordbookId`, `wordKey`, `wordId`, `position`, `status`, `assignedAt`, `startedAt`, `completedAt`, `skippedAt`, `updatedAt`)
                SELECT
                    'local:' || `wordbookId` || ':default',
                    `learningWordId`,
                    'local',
                    `wordbookId`,
                    `wordKey`,
                    `id`,
                    rowid,
                    CASE
                        WHEN `status` = 'Mastered' THEN 'completed'
                        WHEN `status` = 'New' THEN 'pending'
                        ELSE 'active'
                    END,
                    `createdAt`,
                    CASE WHEN `status` != 'New' THEN COALESCE(`learnedAt`, `updatedAt`) ELSE NULL END,
                    CASE WHEN `status` = 'Mastered' THEN COALESCE(`learnedAt`, `updatedAt`) ELSE NULL END,
                    NULL,
                    `updatedAt`
                FROM `words`
                WHERE `wordKey` != ''
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO `study_sessions`
                    (`id`, `userId`, `wordbookId`, `planId`, `studyDayEpoch`, `mode`, `status`, `currentPosition`, `totalCount`, `completedCount`, `startedAt`, `lastActiveAt`, `finishedAt`, `createdAt`, `updatedAt`)
                SELECT
                    `userId` || ':' || `wordbookId` || ':' || `studyDayEpoch` || ':fsrs_today',
                    `userId`,
                    `wordbookId`,
                    `userId` || ':' || `wordbookId` || ':default',
                    `studyDayEpoch`,
                    'fsrs_today',
                    CASE WHEN SUM(CASE WHEN `completedAt` IS NULL THEN 1 ELSE 0 END) = 0 THEN 'completed' ELSE 'active' END,
                    COALESCE(MIN(CASE WHEN `completedAt` IS NULL THEN `position` END), COUNT(*)),
                    COUNT(*),
                    SUM(CASE WHEN `completedAt` IS NOT NULL THEN 1 ELSE 0 END),
                    MIN(`createdAt`),
                    MAX(`updatedAt`),
                    CASE WHEN SUM(CASE WHEN `completedAt` IS NULL THEN 1 ELSE 0 END) = 0 THEN MAX(`completedAt`) ELSE NULL END,
                    MIN(`createdAt`),
                    MAX(`updatedAt`)
                FROM `today_fsrs_session_items`
                GROUP BY `userId`, `wordbookId`, `studyDayEpoch`
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO `study_session_items`
                    (`sessionId`, `position`, `userId`, `wordbookId`, `learningWordId`, `wordKey`, `wordId`, `cardId`, `questionType`, `status`, `answeredAt`, `result`, `createdAt`, `updatedAt`)
                SELECT
                    `today_fsrs_session_items`.`userId` || ':' || `today_fsrs_session_items`.`wordbookId` || ':' || `today_fsrs_session_items`.`studyDayEpoch` || ':fsrs_today',
                    `today_fsrs_session_items`.`position`,
                    `today_fsrs_session_items`.`userId`,
                    `today_fsrs_session_items`.`wordbookId`,
                    COALESCE(`words`.`learningWordId`, `today_fsrs_session_items`.`wordId`),
                    COALESCE(`words`.`wordKey`, ''),
                    `today_fsrs_session_items`.`wordId`,
                    `today_fsrs_session_items`.`cardId`,
                    'recognition',
                    CASE WHEN `today_fsrs_session_items`.`completedAt` IS NULL THEN 'pending' ELSE 'completed' END,
                    `today_fsrs_session_items`.`completedAt`,
                    '',
                    `today_fsrs_session_items`.`createdAt`,
                    `today_fsrs_session_items`.`updatedAt`
                FROM `today_fsrs_session_items`
                LEFT JOIN `words` ON `words`.`id` = `today_fsrs_session_items`.`wordId`
                """.trimIndent()
            )
        }
    }

    private object Migration9To10 : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `study_sessions` ADD COLUMN `settingsFingerprint` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `study_session_items` ADD COLUMN `queueReason` TEXT NOT NULL DEFAULT 'new'")
            db.execSQL("ALTER TABLE `study_session_items` ADD COLUMN `optionsJson` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `study_session_items` ADD COLUMN `correctOptionId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `study_session_items` ADD COLUMN `selectedOptionId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `study_session_items` ADD COLUMN `revealedAt` INTEGER")
            db.execSQL("ALTER TABLE `study_session_items` ADD COLUMN `completedAt` INTEGER")
            db.execSQL("ALTER TABLE `study_session_items` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `study_session_items` ADD COLUMN `cardStateBefore` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `study_session_items` ADD COLUMN `cardStateAfter` TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """
                UPDATE `study_session_items`
                SET `completedAt` = `answeredAt`
                WHERE `status` = 'completed'
                    AND `completedAt` IS NULL
                    AND `answeredAt` IS NOT NULL
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE `study_sessions`
                SET
                    `totalCount` = (
                        SELECT COUNT(*) FROM `study_session_items`
                        WHERE `study_session_items`.`sessionId` = `study_sessions`.`id`
                    ),
                    `completedCount` = (
                        SELECT COUNT(*) FROM `study_session_items`
                        WHERE `study_session_items`.`sessionId` = `study_sessions`.`id`
                            AND `study_session_items`.`status` = 'completed'
                    ),
                    `currentPosition` = COALESCE(
                        (
                            SELECT MIN(`position`) FROM `study_session_items`
                            WHERE `study_session_items`.`sessionId` = `study_sessions`.`id`
                                AND `study_session_items`.`status` != 'completed'
                        ),
                        (
                            SELECT COUNT(*) FROM `study_session_items`
                            WHERE `study_session_items`.`sessionId` = `study_sessions`.`id`
                        )
                    )
                """.trimIndent()
            )
        }
    }

    private object Migration10To11 : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_wordbookId_status_text` ON `words` (`wordbookId`, `status`, `text`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_wordbookId_wordKey` ON `words` (`wordbookId`, `wordKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_wordbookId_pos` ON `words` (`wordbookId`, `pos`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_words_userId_wordbookId_wordKey` ON `learning_words` (`userId`, `wordbookId`, `wordKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_learning_words_userId_wordbookId_status` ON `learning_words` (`userId`, `wordbookId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_session_items_sessionId_status_position` ON `study_session_items` (`sessionId`, `status`, `position`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_session_items_sessionId_wordKey` ON `study_session_items` (`sessionId`, `wordKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_session_items_sessionId_cardId` ON `study_session_items` (`sessionId`, `cardId`)")
        }
    }

    private object Migration11To12 : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `today_fsrs_session_items`")
            db.execSQL("DROP TABLE IF EXISTS `word_study_records`")
        }
    }
}
