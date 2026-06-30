package com.danchi.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danchi.app.domain.Accent
import com.danchi.app.domain.MeaningChoiceOption
import com.danchi.app.domain.StudyPlanOptions
import com.danchi.app.domain.StudyWordOrder
import com.danchi.app.domain.Word
import com.danchi.app.domain.WordMeaning
import com.danchi.app.domain.WordbookProgress
import com.danchi.app.domain.displayMeanings
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DanChiApp(viewModel: DanChiViewModel) {
    val state by viewModel.screenState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isStudying = state.ui.activeTab == MainTab.Study && state.ui.currentWord != null
    var activeMeEntry by remember { mutableStateOf<MeEntry?>(null) }
    val isMeEntryPage = state.ui.activeTab == MainTab.Me && activeMeEntry != null
    var showExitConfirm by remember { mutableStateOf(false) }
    var showMasteryConfirm by remember { mutableStateOf(false) }
    var muteMasteryConfirmToday by remember { mutableStateOf(false) }

    LaunchedEffect(state.ui.message) {
        val message = state.ui.message
        if (!message.isNullOrBlank()) snackbarHostState.showSnackbar(message)
    }

    BackHandler(enabled = isStudying) {
        showExitConfirm = true
    }

    BackHandler(enabled = isMeEntryPage) {
        activeMeEntry = null
    }

    LaunchedEffect(state.ui.activeTab) {
        if (state.ui.activeTab != MainTab.Me) activeMeEntry = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!isStudying && !isMeEntryPage) {
                DanChiBottomBar(active = state.ui.activeTab, onSelect = viewModel::selectTab)
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state.ui.activeTab) {
                MainTab.Home -> HomeScreen(state, viewModel)
                MainTab.Study -> StudyScreen(
                    state = state,
                    viewModel = viewModel,
                    onRequestMarkMastered = {
                        if (state.ui.currentWord != null) {
                            if (state.settings.masteryConfirmMutedUntilMillis > System.currentTimeMillis()) {
                                viewModel.markCurrentMastered()
                            } else {
                                muteMasteryConfirmToday = false
                                showMasteryConfirm = true
                            }
                        }
                    }
                )
                MainTab.Dictionary -> DictionaryScreen(state, viewModel)
                MainTab.Stats -> StatsScreen(state)
                MainTab.Me -> {
                    val entry = activeMeEntry
                    if (entry == null) {
                        MeScreen(state = state, onOpenEntry = { activeMeEntry = it })
                    } else {
                        MeEntryDetailScreen(
                            entry = entry,
                            state = state,
                            viewModel = viewModel,
                            onBack = { activeMeEntry = null }
                        )
                    }
                }
            }
        }
    }

    if (showExitConfirm) {
        StudyExitConfirmDialog(
            onContinue = { showExitConfirm = false },
            onConfirmEnd = {
                showExitConfirm = false
                viewModel.selectTab(MainTab.Home)
            }
        )
    }

    if (showMasteryConfirm) {
        MasteryConfirmDialog(
            muteToday = muteMasteryConfirmToday,
            onMuteTodayChange = { muteMasteryConfirmToday = it },
            onCancel = { showMasteryConfirm = false },
            onConfirm = {
                showMasteryConfirm = false
                viewModel.markCurrentMastered(muteConfirmToday = muteMasteryConfirmToday)
            }
        )
    }
}

@Composable
private fun StudyExitConfirmDialog(onContinue: () -> Unit, onConfirmEnd: () -> Unit) {
    AlertDialog(
        onDismissRequest = onContinue,
        title = { Text("确认结束学习？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("今日 FSRS 队列还在推进中。")
                Text("退出后已完成记录会保留，未完成卡片会在今日清单中继续。")
                Text("确认结束后会返回首页。")
            }
        },
        confirmButton = { Button(onClick = onContinue) { Text("继续提分") } },
        dismissButton = { TextButton(onClick = onConfirmEnd) { Text("确认结束") } }
    )
}

@Composable
private fun MasteryConfirmDialog(
    muteToday: Boolean,
    onMuteTodayChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("确认标记熟练？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("标记熟练后将不再安排学习与复习，是否确认标记？")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = muteToday, onCheckedChange = onMuteTodayChange)
                    Text("今天不再提示（北京时间23:59:59前）")
                }
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
        confirmButton = { Button(onClick = onConfirm) { Text("确认") } }
    )
}

@Composable
private fun DanChiBottomBar(active: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar {
        MainTab.entries.forEach { tab ->
            val icon = when (tab) {
                MainTab.Home -> Icons.Outlined.Home
                MainTab.Study -> Icons.Outlined.Book
                MainTab.Dictionary -> Icons.Outlined.Search
                MainTab.Stats -> Icons.Outlined.AutoGraph
                MainTab.Me -> Icons.Outlined.Person
            }
            NavigationBarItem(
                selected = tab == active,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, contentDescription = tab.label) },
                label = { Text(tab.label) }
            )
        }
    }
}

@Composable
private fun ScreenColumn(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun HomeScreen(state: DanChiScreenState, viewModel: DanChiViewModel) {
    ScreenColumn {
        item {
            Header(title = "DanChi 单词学习", subtitle = "唯一入口 · FSRS 智能提分")
        }
        item {
            CardBlock(container = MaterialTheme.colorScheme.primaryContainer) {
                Text("今日任务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("系统按 FSRS 合并到期复习和新词，不再保留自由练习或牢记模式。", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    PlanTile("新词", state.plan.newCount.toString(), "按每日设置", Modifier.weight(1f))
                    PlanTile("到期", state.plan.dueReviewCount.toString(), "按 FSRS 优先", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Text("预计 ${state.plan.estimatedMinutes} 分钟完成", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { viewModel.startSession() },
                    enabled = !state.ui.busy,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Text("开始提分", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            CardBlock {
                Text("学习进度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                state.selectedWordbook?.let { selected ->
                    Spacer(Modifier.height(4.dp))
                    Text("当前词库：${selected.wordbook.title}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatPill("总词量", state.stats.total.toString(), Modifier.weight(1f))
                    StatPill("已学", state.stats.learned.toString(), Modifier.weight(1f))
                    StatPill("收藏", state.stats.favorite.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(progress = { state.stats.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("${(state.stats.progress * 100).roundToInt()}% 已进入学习记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            CardBlock(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text("本地保存学习记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("词库、FSRS 卡片、今日清单、收藏和笔记都会保存在本机。")
            }
        }
    }
}

@Composable
private fun StudyScreen(
    state: DanChiScreenState,
    viewModel: DanChiViewModel,
    onRequestMarkMastered: () -> Unit
) {
    if (state.ui.busy) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            LoadingCard(
                title = state.ui.loadingTitle.ifBlank { "正在准备今日提分" },
                body = state.ui.loadingDetail.ifBlank { "正在读取 FSRS 清单。" },
                progress = state.ui.loadingProgress,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    val word = state.ui.currentWord
    if (word == null) {
        if (state.ui.fsrsTailBuilding) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                LoadingCard(
                    title = state.ui.fsrsTailBuildTitle.ifBlank { "正在准备下一题" },
                    body = "后续题目正在后台补齐，请稍候。",
                    progress = 0.75f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            return
        }
        ScreenColumn {
            item { Header(title = state.ui.sessionMode.label, subtitle = "暂无学习队列") }
            item {
                CardBlock {
                    EmptyState(
                        title = "今天没有待学习内容",
                        body = "FSRS 今日队列完成时会看到这里。可以返回首页，或重新检查今日任务。"
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { viewModel.startSession() }) { Text("开始提分") }
                }
            }
        }
    } else {
        OnePageStudyScreen(state, viewModel, word, onRequestMarkMastered)
    }
}

@Composable
private fun OnePageStudyScreen(
    state: DanChiScreenState,
    viewModel: DanChiViewModel,
    word: Word,
    onRequestMarkMastered: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 700.dp
        val gap = if (compact) 8.dp else 12.dp
        val padding = if (compact) 10.dp else 16.dp
        val totalCount = state.ui.fsrsTotalCount.takeIf { it > 0 } ?: state.ui.session.size
        val progress = if (totalCount == 0) 0f else {
            ((state.ui.currentIndex + 1).toFloat() / totalCount).coerceIn(0f, 1f)
        }
        val fsrsItem = state.ui.currentFsrsItem

        if (fsrsItem != null && !state.ui.fsrsInfoVisible) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                StudyTopBar(
                    title = state.ui.sessionMode.label,
                    subtitle = "${state.ui.currentIndex + 1} / $totalCount",
                    word = fsrsItem.word,
                    viewModel = viewModel,
                    canMarkMastered = true,
                    onRequestMarkMastered = onRequestMarkMastered
                )
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                TailBuildText(state)
                RecognitionQuestionPanel(
                    word = fsrsItem.word,
                    options = fsrsItem.options,
                    selectedOptionId = state.ui.fsrsSelectedOptionId,
                    correctOptionId = state.ui.fsrsCorrectOptionId,
                    answered = state.ui.fsrsAnswered,
                    compact = compact,
                    onSelect = viewModel::answerCurrentRecognition,
                    onReveal = viewModel::revealCurrentRecognitionAnswer,
                    onContinue = viewModel::continueAfterFsrsAnswer,
                    modifier = Modifier.weight(1f)
                )
            }
            return@BoxWithConstraints
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            StudyTopBar(
                title = state.ui.sessionMode.label,
                subtitle = "${state.ui.currentIndex + 1} / $totalCount",
                word = word,
                viewModel = viewModel,
                canMarkMastered = true,
                onRequestMarkMastered = onRequestMarkMastered
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            TailBuildText(state)
            CompactWordPanel(
                word = word,
                state = state,
                viewModel = viewModel,
                compact = compact,
                modifier = Modifier.weight(1f)
            )
            FsrsAnswerBar(state, viewModel)
        }
    }
}

@Composable
private fun TailBuildText(state: DanChiScreenState) {
    if (state.ui.fsrsTailBuilding) {
        Text(
            state.ui.fsrsTailBuildTitle.ifBlank { "正在准备后续题目" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StudyTopBar(
    title: String,
    subtitle: String,
    word: Word?,
    viewModel: DanChiViewModel,
    canMarkMastered: Boolean,
    onRequestMarkMastered: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { viewModel.speak(word) }, enabled = word != null, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "播放单词")
        }
        IconButton(onClick = { word?.let(viewModel::toggleFavorite) }, enabled = word != null, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.Star, contentDescription = "加入生词本")
        }
        Button(
            onClick = onRequestMarkMastered,
            enabled = word != null && canMarkMastered,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text("熟", maxLines = 1)
        }
    }
}

@Composable
private fun RecognitionQuestionPanel(
    word: Word,
    options: List<MeaningChoiceOption>,
    selectedOptionId: String?,
    correctOptionId: String?,
    answered: Boolean,
    compact: Boolean,
    onSelect: (String) -> Unit,
    onReveal: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    CardBlock(modifier) {
        Text("选择正确释义", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
        Text(word.text, fontSize = if (compact) 42.sp else 48.sp, fontWeight = FontWeight.Bold)
        if (!word.phonetic.isNullOrBlank()) {
            Text(word.phonetic.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(if (compact) 14.dp else 18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
            options.forEach { option ->
                val isSelected = selectedOptionId == option.id
                val isCorrect = correctOptionId == option.id
                val label = buildString {
                    append(option.posName.ifBlank { option.pos })
                    if (isNotBlank()) append("  ")
                    append(option.meaning)
                }
                val prefix = when {
                    answered && isCorrect -> "正确 · "
                    answered && isSelected -> "选择 · "
                    else -> ""
                }
                OutlinedButton(
                    onClick = { if (!answered) onSelect(option.id) },
                    enabled = !answered,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        prefix + label,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (answered) {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("下一步")
            }
        } else {
            OutlinedButton(onClick = onReveal, modifier = Modifier.fillMaxWidth()) {
                Text("查看答案")
            }
        }
    }
}

@Composable
private fun CompactWordPanel(
    word: Word,
    state: DanChiScreenState,
    viewModel: DanChiViewModel,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(if (compact) 14.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
        ) {
            item {
                Text(
                    text = word.text,
                    fontSize = if (compact) 38.sp else 44.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!word.phonetic.isNullOrBlank()) {
                    Text(word.phonetic.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.speak(word) }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "播放")
                }
            }
            item { MeaningList(word.displayMeanings, maxItems = 6, maxLines = 2) }
            if (word.example.isNotBlank()) {
                item {
                    Text("例句", fontWeight = FontWeight.SemiBold)
                    Text(word.example)
                    if (word.exampleCn.isNotBlank()) {
                        Text(word.exampleCn, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!word.memoryTip.isNullOrBlank()) {
                item {
                    Text("记忆提示", fontWeight = FontWeight.SemiBold)
                    Text(word.memoryTip.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                OutlinedTextField(
                    value = state.ui.noteDraft,
                    onValueChange = viewModel::updateNoteDraft,
                    label = { Text("笔记") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = viewModel::saveCurrentNote) { Text("保存笔记") }
            }
        }
    }
}

@Composable
private fun MeaningList(meanings: List<WordMeaning>, maxItems: Int = 4, maxLines: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        meanings.take(maxItems).forEach { meaning ->
            Text(
                "${meaning.posName.ifBlank { meaning.pos }} ${meaning.meaning}".trim(),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FsrsAnswerBar(state: DanChiScreenState, viewModel: DanChiViewModel) {
    Button(onClick = viewModel::moveNext, modifier = Modifier.fillMaxWidth().height(54.dp)) {
        Text(if (state.ui.currentIndex + 1 >= state.ui.fsrsTotalCount) "完成" else "下一词")
    }
}

@Composable
private fun DictionaryScreen(state: DanChiScreenState, viewModel: DanChiViewModel) {
    ScreenColumn {
        item { Header("词典", "本地词库与 ECDICT 释义补全") }
        item {
            OutlinedTextField(
                value = state.ui.query,
                onValueChange = viewModel::updateQuery,
                label = { Text("搜索单词或释义") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        items(state.dictionary, key = { it.id }) { word ->
            DictionaryWordCard(word = word, viewModel = viewModel)
        }
    }
}

@Composable
private fun DictionaryWordCard(word: Word, viewModel: DanChiViewModel) {
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(word.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (!word.phonetic.isNullOrBlank()) Text(word.phonetic.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { viewModel.speak(word) }) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = "播放")
            }
            IconButton(onClick = { viewModel.toggleFavorite(word) }) {
                Icon(Icons.Outlined.Star, contentDescription = "收藏")
            }
        }
        MeaningList(word.displayMeanings, maxItems = 3, maxLines = 1)
        if (word.example.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(word.example, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatsScreen(state: DanChiScreenState) {
    ScreenColumn {
        item { Header("统计", "FSRS 学习进度") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("总词量", state.stats.total.toString(), Modifier.weight(1f))
                MetricCard("已学", state.stats.learned.toString(), Modifier.weight(1f))
            }
        }
        item {
            CardBlock {
                Text("词库进度", fontWeight = FontWeight.SemiBold)
                ProgressLine("新词", state.stats.newWords, state.stats.total)
                ProgressLine("学习中", state.stats.learning, state.stats.total)
                ProgressLine("复习中", state.stats.review, state.stats.total)
                ProgressLine("已掌握", state.stats.mastered, state.stats.total)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("连续学习", "${state.profileStats.streakDays} 天", Modifier.weight(1f))
                MetricCard("累计学习", "${state.profileStats.totalStudyDays} 天", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MeScreen(state: DanChiScreenState, onOpenEntry: (MeEntry) -> Unit) {
    ScreenColumn {
        item { Header("我的", "设置与词库") }
        item {
            CardBlock {
                Text("DanChi 用户", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(state.selectedWordbook?.wordbook?.title ?: "正在读取本地词库", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MeEntryCard("学习计划", "每日新词、顺序、发音和语速", Icons.Outlined.CheckCircle) {
                    onOpenEntry(MeEntry.StudyPlan)
                }
                MeEntryCard("我的词库", state.selectedWordbook?.wordbook?.title ?: "正在读取本地词库", Icons.Outlined.Book) {
                    onOpenEntry(MeEntry.Wordbook)
                }
                MeEntryCard("软件设置", "本机数据与产品说明", Icons.Outlined.Settings) {
                    onOpenEntry(MeEntry.AppSettings)
                }
                MeEntryCard("使用帮助", "唯一入口与 FSRS 规则", Icons.Outlined.Search) {
                    onOpenEntry(MeEntry.Help)
                }
            }
        }
    }
}

@Composable
private fun MeEntryDetailScreen(
    entry: MeEntry,
    state: DanChiScreenState,
    viewModel: DanChiViewModel,
    onBack: () -> Unit
) {
    ScreenColumn {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text(entry.title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("我的", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            when (entry) {
                MeEntry.StudyPlan -> StudyPlanEntryContent(state, viewModel)
                MeEntry.Wordbook -> WordbookEntryContent(state, viewModel)
                MeEntry.AppSettings -> AppSettingsEntryContent()
                MeEntry.Help -> HelpEntryContent()
            }
        }
    }
}

private enum class MeEntry {
    StudyPlan,
    Wordbook,
    AppSettings,
    Help
}

private val MeEntry.title: String
    get() = when (this) {
        MeEntry.StudyPlan -> "学习计划"
        MeEntry.Wordbook -> "我的词库"
        MeEntry.AppSettings -> "软件设置"
        MeEntry.Help -> "使用帮助"
    }

@Composable
private fun StudyPlanEntryContent(state: DanChiScreenState, viewModel: DanChiViewModel) {
    CardBlock {
        Text("学习计划", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PlanTile("时长", state.settings.dailyMinutes.toString(), "每日分钟", Modifier.weight(1f))
            PlanTile("新词", state.settings.dailyNewWords.toString(), "每日设置", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text("每日新词上限", fontWeight = FontWeight.SemiBold)
        DailyNewWordOptionGrid(selected = state.settings.dailyNewWords, onSelect = viewModel::updateDailyNew)
        Spacer(Modifier.height(12.dp))
        WordOrderOptionRow(selected = state.settings.wordOrder, onSelect = viewModel::updateWordOrder)
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Text("开始提分是唯一学习入口；系统先生成 5 题秒进，再后台补齐完整 FSRS 清单。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SettingSwitch("自动播放单词", state.settings.autoPlayWord, viewModel::updateAutoPlayWord)
        SettingSwitch("自动播放例句", state.settings.autoPlayExample, viewModel::updateAutoPlayExample)
        Spacer(Modifier.height(8.dp))
        Text("自动播放次数：${state.settings.autoPlayRepeatCount} 次", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudyPlanOptions.AutoPlayRepeatOptions.forEach { count ->
                FilterChip(
                    selected = state.settings.autoPlayRepeatCount == count,
                    onClick = { viewModel.updateAutoPlayRepeatCount(count) },
                    label = { Text("${count}次") }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("发音", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Accent.entries.forEach { accent ->
                FilterChip(
                    selected = state.settings.accent == accent,
                    onClick = { viewModel.updateAccent(accent) },
                    label = { Text(accent.label) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("语速：${formatSpeechRate(state.settings.speechRate)}")
        Slider(
            value = state.settings.speechRate,
            onValueChange = viewModel::updateSpeechRate,
            valueRange = 0.65f..1.25f,
            steps = 59
        )
    }
}

@Composable
private fun WordbookEntryContent(state: DanChiScreenState, viewModel: DanChiViewModel) {
    CardBlock {
        Text("我的词库", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        val selected = state.selectedWordbook
        if (selected == null) {
            Text("正在读取本地词库", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            WordbookOption(item = selected, selected = true) {
                viewModel.updateSelectedWordbook(selected.wordbook.id)
            }
            Text("当前只有初中词库，后续可继续添加高中、四级等词库。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppSettingsEntryContent() {
    CardBlock {
        Text("软件设置", fontWeight = FontWeight.SemiBold)
        Text("词库、FSRS 卡片、今日清单、收藏和笔记保存在本机。")
    }
}

@Composable
private fun HelpEntryContent() {
    CardBlock {
        Text("使用帮助", fontWeight = FontWeight.SemiBold)
        ModeInfo("唯一入口", "首页只保留开始提分，所有学习行为进入同一套 FSRS 队列。")
        ModeInfo("今日清单", "首次进入先生成 5 题，后台补齐完整清单；再次进入直接恢复。")
        ModeInfo("答题记录", "查看答案会按提示处理，选择答案会按正确率和耗时进入 FSRS 排期。")
        ModeInfo("词典", "可以搜索单词、收藏和记录笔记。")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeEntryCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun formatSpeechRate(value: Float): String {
    return String.format(Locale.US, "%.2f", value)
}

@Composable
private fun WordOrderOptionRow(selected: StudyWordOrder, onSelect: (StudyWordOrder) -> Unit) {
    Text("背单词顺序", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StudyWordOrder.entries.forEach { order ->
            FilterChip(
                selected = selected == order,
                onClick = { onSelect(order) },
                label = { Text(order.label, maxLines = 1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WordbookOption(item: WordbookProgress, selected: Boolean, onSelect: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onSelect,
        label = {
            Column(Modifier.fillMaxWidth()) {
                Text(item.wordbook.title, fontWeight = FontWeight.SemiBold)
                Text("${item.learned}/${item.total} 已学 · ${item.wordbook.version}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DailyNewWordOptionGrid(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StudyPlanOptions.DailyNewWordOptions.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { value ->
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        label = { Text(value.toString(), maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingCard(title: String, body: String, progress: Float, modifier: Modifier = Modifier) {
    val safeProgress = progress.coerceIn(0f, 1f)
    CardBlock(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(progress = { safeProgress }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            "${(safeProgress * 100).roundToInt()}%",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CardBlock(
    modifier: Modifier = Modifier,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun PlanTile(label: String, value: String, helper: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.height(132.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(helper, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    CardBlock(modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProgressLine(label: String, value: Int, total: Int) {
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(0.28f))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else value.toFloat() / total },
            modifier = Modifier.weight(0.52f)
        )
        Text(value.toString(), modifier = Modifier.weight(0.2f).padding(start = 10.dp))
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
    HorizontalDivider()
}

@Composable
private fun ModeInfo(title: String, description: String) {
    Text(title, fontWeight = FontWeight.SemiBold)
    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}
