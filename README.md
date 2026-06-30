# DanChi

DanChi 是一个 Android 中考英语背单词 App 工程，当前交付为可继续商业化打磨的本地离线版本。

## 已实现

- Kotlin + Jetpack Compose + Material 3 Android 工程。
- Room 本地数据库：词条、FSRS 卡片、答题记录、笔记。
- 首次启动按 `app/src/main/assets/wordbooks/manifest.json` 导入本地词库，兼容 `初中中考.txt` 转换后的 `zhongkao_words.json`。
- 首页今日计划、当前词库进度、唯一主学习入口“开始提分”。
- 接入开源 `java-fsrs`，用统一 FSRS 队列整合新词、learning/relearning 和 review 到期卡片。
- 第一版主流程实现 recognition card：英文单词 -> 4 选 1 中文释义；后续预留 recall、audio、sentence card。
- Android TextToSpeech 兜底发音，支持美式/英式和语速设置。
- 本地词典搜索、收藏、生词本入口、笔记。
- 统计页：总词量、已学、掌握、收藏和笔记。
- DataStore 保存学习设置、当前词库和发音设置；Room 保存 `cards`、`review_logs`、`study_sessions`、`study_session_items`、`user_fsrs_setting`。
- 纯 domain / scheduler 单元测试覆盖 FSRS 调度、词义归一化、实体映射和干扰项。

## 当前学习架构

当前主学习路径只有一个入口：**开始提分**。

进入后 App 会生成一条 FSRS 今日队列：

1. 优先安排 `learning` / `relearning` 且 `due_at <= now` 的卡片。
2. 其次安排 `review` 且 `due_at <= now` 的卡片。
3. 最后按每日新词设置补入新词 recognition card。

为了避免低端手机等待过久，点击“开始提分”后先固定首批 5 题并进入学习页，后台继续把今日完整清单补齐。用户中途退出时，已完成、未完成和当前位置保存在 `study_sessions` / `study_session_items`，再次点击“开始提分”会恢复同一份今日清单，不重新生成。

新词第一次出现时先测试，不先展示信息页。答题后根据正确性、耗时、提示使用情况映射为 FSRS 四档 rating：

- `Again = 1`
- `Hard = 2`
- `Good = 3`
- `Easy = 4`

调度结果由开源 `io.github.open-spaced-repetition:fsrs` 生成，写回 `cards.state`、`dueAt`、`stability`、`difficulty`、`reps`、`lapses`，并写入 `review_logs`。答题后的单词详情页只用于讲解，不写入复习记录。

当前版本不再保留自由练习或牢记模式入口。主进度和每日任务全部以“开始提分”的 FSRS 队列为准。

更详细的入口和数据结构说明见：`docs/FSRS调度与入口说明.md`。

## 构建

本机 SDK 已在 `local.properties` 指向：

```properties
sdk.dir=C\:\\Users\\37768\\AppData\\Local\\Android\\Sdk
```

如当前终端 PATH 未包含 JDK，可临时执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

然后运行：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

当前工程实际编译配置为 `compileSdk = 36`、`targetSdk = 36`。本机原有 `android-37.0` 是 minor API 目录，AGP 按整数 API 查找会失败，因此已通过 `sdkmanager` 安装标准 `platforms;android-36`。

本次验证使用已解压的 Gradle 9.2.1 直接执行：

```powershell
%TEMP%\gradle-9.2.1\bin\gradle.bat --no-daemon testDebugUnitTest assembleDebug
```

## 词库生成

源文件是根目录的 `初中中考.txt`，转换脚本为：

```powershell
.\tools\ConvertWordbook.ps1
```

如果使用 Windows PowerShell 5.1 遇到 UTF-8 脚本解析问题，可用 Python 内联转换或 PowerShell 7 执行。当前已生成 1974 个唯一词条。

词库入口文件是：

```text
app/src/main/assets/wordbooks/manifest.json
```

当前默认词库条目指向：

```text
app/src/main/assets/wordbooks/zhongkao_words.json
```

## ECDICT 词典补齐管道

当前 App 运行时使用 Room 本地数据库，首次启动或词库版本变化时由 `app/src/main/assets/wordbooks/manifest.json` 指向的 asset JSON 写入/刷新 `words` 表。学习进度保存在 `cards`、`review_logs`、`study_plans`、`study_plan_items`、`study_sessions`、`study_session_items`、`user_mastered_words`、`notes` 等表中；词库刷新只更新 `meaning`、`pos`、`meaningsJson`、`phonetic`、例句和助记等词条文本字段，不替换已有学习状态。

新增的 ECDICT 管道使用本地 CSV/SQLite 文件作为输入，不在代码里硬编码下载地址，也不让 AI 批量生成主词库。完整架构是“双库 + 词典层”：

```text
danchi.db
  words / cards / review_logs / study_sessions / study_session_items / notes
  负责学习、复习、FSRS、收藏、笔记和进度

ecdict.db
  ecdict_entries / wordbooks / wordbook_words
  负责全量词典、词库归属、查词和补齐

dictionary_cache
  缓存本地或远程 ECDICT 查询结果
```

App 侧新增 `DictionaryRepository`，包含本地 ECDICT SQLite 查询、远程 ECDICT API 预留和本地缓存。正式学习流程不依赖网络；进入学习表的词条仍然写入本地 Room。

数据库 v12 已移除旧 `word_study_records` 和 `today_fsrs_session_items` 表。旧版本升级时会先把历史今日 FSRS 清单迁移到 `study_sessions` / `study_session_items`，再在 v12 migration 中删除旧表。

把 ECDICT 文件放到本地未提交目录即可，例如：

```text
data/ecdict.csv
```

或：

```text
data/ecdict.sqlite
```

### 直接从 ECDICT 生成当前词库 JSON

推荐直接把当前词库 JSON 作为目标词表，这样可以保留现有 `word.id`：

```powershell
.\gradlew.bat importEcdict `
  -Psource=data/ecdict.csv `
  -Ptarget=app/src/main/assets/wordbooks/zhongkao_words.json `
  -PtargetWords=app/src/main/assets/wordbooks/zhongkao_words.json `
  -PreportDir=docs/ecdict-reports
```

也可以使用文本目标词表：

```powershell
python scripts/import-ecdict.py --source data/ecdict.csv --target app/src/main/assets/wordbooks/zhongkao_words.json --target-words data/target-words.sample.txt --report-dir docs/ecdict-reports
```

管道会输出标准 JSON，保留旧字段 `meaning`、`translation`、`example`，并新增/补齐 `meanings[].pos`、`meanings[].posName`、`meanings[].meaning`。词性只来自 ECDICT 独立 `pos` 字段或 `translation` 开头的明确词性；无法确认时 `pos` 留空并写入报告。

旧的 `scripts/fill_missing_pos_from_dictionary.py` 会使用启发式推断，现已默认禁止直接写主词库；只有显式传入 `--allow-heuristic-write` 才会执行旧兼容流程。新的正式流程应使用 ECDICT 导入报告，再由人工或审核脚本合并补丁。

### 构建全量 ECDICT SQLite 和多词库归属

构建统一 `ecdict.db`：

```powershell
.\gradlew.bat buildEcdictSqlite `
  -Psource=data/ecdict.csv `
  -PtargetDb=data/build/ecdict.db `
  -PcurrentWordbookJson=app/src/main/assets/wordbooks/zhongkao_words.json `
  -PcurrentWordbookId=zhongkao `
  -PcurrentWordbookTitle=初中中考
```

词库归属配置示例：

```text
data/wordbooks/grade8_core.sample.json
```

追加一个词库归属：

```powershell
.\gradlew.bat importWordbookMemberships `
  -Pdb=data/build/ecdict.db `
  -Pwordbook=data/wordbooks/grade8_core.sample.json
```

从 `ecdict.db` 导出当前 App 兼容词库 JSON：

```powershell
.\gradlew.bat exportWordbookFromEcdict `
  -Pdb=data/build/ecdict.db `
  -PwordbookId=zhongkao `
  -Ptarget=app/src/main/assets/wordbooks/zhongkao_words.json `
  -PreportDir=docs/ecdict-reports
```

同一个 `word_key` 可以属于多个词库，例如 `apply` 同时属于 `zhongkao` 和 `grade8_core`。词典释义只保存在 `ecdict_entries` 一份；词库归属保存在 `wordbook_words` 多份。当前 App 导出的学习 JSON 仍保留每个词库自己的稳定 `id`，不会修改已有学习进度依赖的 `word.id`。

### 服务器化预留

后期可以把 `ecdict.db` 或同构数据放到服务器，App 通过远程 ECDICT API 按需查词。远程返回结果进入 `dictionary_cache`，再生成 `WordPatch`，不能直接覆盖学习主库。离线学习依然依赖本地 `words` 表，不依赖服务器实时可用。

报告文件固定为：

```text
docs/ecdict-reports/missing-word-report.json
docs/ecdict-reports/missing-pos-report.json
docs/ecdict-reports/conflict-pos-report.json
docs/ecdict-reports/low-quality-meaning-report.json
docs/ecdict-reports/import-summary.json
```

ECDICT 主项目和常见镜像标注为 MIT License。商业发布时需要在 App 或随包文档中保留 ECDICT 来源和 MIT 许可证声明；AI 只能基于报告文件生成人工审核补丁，不能直接覆盖主词库。

新增第二个词库时，在 `app/src/main/assets/wordbooks/` 放入新的词条 JSON，并在 `manifest.json` 增加一项。词条 JSON 仍使用数组格式，至少包含 `id`、`word`、`meaning`，建议继续包含 `book`、`unit`、`source`、`sourceLine`。`id` 需要全局稳定且唯一，建议给每个词库使用独立前缀。

转换第二个词库时可复用脚本并指定参数，例如：

```powershell
.\tools\ConvertWordbook.ps1 -SourcePath ".\高中词库.txt" -OutputPath "app/src/main/assets/wordbooks/gaokao_words.json" -BookName "高中词库" -IdPrefix "gk"
```

## 商业化待补

- 释义和例句需要人工审核并补充来源登记。
- 当前例句为程序兜底模板，商业发布前应替换为自研审核例句。
- 当前发音使用系统 TTS，商业发布前可接入授权音频或云 TTS 缓存。
- 账号、云同步、会员、支付、远程配置、后台和监控已在文档中定义，但此工程当前只完成离线本地闭环。
