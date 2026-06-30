plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
}

val pythonCommand = providers.gradleProperty("python").orElse("python")

tasks.register<Exec>("importEcdict") {
    group = "wordbook"
    description = "Import a target word bank from a local ECDICT CSV/SQLite file."

    val sourcePath = providers.gradleProperty("source").orElse("data/ecdict.csv")
    val targetPath = providers.gradleProperty("target").orElse("app/src/main/assets/wordbooks/zhongkao_words.json")
    val targetWordsPath = providers.gradleProperty("targetWords").orElse("app/src/main/assets/wordbooks/zhongkao_words.json")
    val reportDirPath = providers.gradleProperty("reportDir").orElse("docs/ecdict-reports")
    val limitValue = providers.gradleProperty("limit").orElse("2000")

    commandLine(
        pythonCommand.get(),
        "scripts/import-ecdict.py",
        "--source",
        sourcePath.get(),
        "--target",
        targetPath.get(),
        "--target-words",
        targetWordsPath.get(),
        "--report-dir",
        reportDirPath.get(),
        "--limit",
        limitValue.get()
    )
}

tasks.register<Exec>("testEcdictPipeline") {
    group = "verification"
    description = "Run unit tests for the ECDICT import and POS normalization pipeline."
    commandLine(pythonCommand.get(), "scripts/test_ecdict_pipeline.py")
}

tasks.register<Exec>("buildEcdictSqlite") {
    group = "wordbook"
    description = "Build normalized ecdict.db with wordbook membership tables."

    val sourcePath = providers.gradleProperty("source").orElse("data/ecdict.csv")
    val targetDbPath = providers.gradleProperty("targetDb").orElse("data/build/ecdict.db")
    val currentWordbookJson = providers.gradleProperty("currentWordbookJson").orElse("app/src/main/assets/wordbooks/zhongkao_words.json")
    val currentWordbookId = providers.gradleProperty("currentWordbookId").orElse("zhongkao")
    val currentWordbookTitle = providers.gradleProperty("currentWordbookTitle").orElse("初中中考")

    commandLine(
        pythonCommand.get(),
        "scripts/build-ecdict-sqlite.py",
        "--source",
        sourcePath.get(),
        "--target-db",
        targetDbPath.get(),
        "--current-wordbook-json",
        currentWordbookJson.get(),
        "--current-wordbook-id",
        currentWordbookId.get(),
        "--current-wordbook-title",
        currentWordbookTitle.get()
    )
}

tasks.register<Exec>("importWordbookMemberships") {
    group = "wordbook"
    description = "Import a wordbook membership JSON into an existing normalized ecdict.db."
    val dbPath = providers.gradleProperty("db").orElse("data/build/ecdict.db")
    val wordbookPath = providers.gradleProperty("wordbook").orElse("data/wordbooks/grade8_core.sample.json")
    commandLine(
        pythonCommand.get(),
        "scripts/import-wordbook-memberships.py",
        "--db",
        dbPath.get(),
        "--wordbook",
        wordbookPath.get()
    )
}

tasks.register<Exec>("exportWordbookFromEcdict") {
    group = "wordbook"
    description = "Export an app-compatible wordbook JSON from normalized ecdict.db memberships."
    val dbPath = providers.gradleProperty("db").orElse("data/build/ecdict.db")
    val wordbookId = providers.gradleProperty("wordbookId").orElse("zhongkao")
    val targetPath = providers.gradleProperty("target").orElse("app/src/main/assets/wordbooks/zhongkao_words.json")
    val reportDirPath = providers.gradleProperty("reportDir").orElse("docs/ecdict-reports")
    commandLine(
        pythonCommand.get(),
        "scripts/export-wordbook-from-ecdict.py",
        "--db",
        dbPath.get(),
        "--wordbook-id",
        wordbookId.get(),
        "--target",
        targetPath.get(),
        "--report-dir",
        reportDirPath.get()
    )
}
