package com.danchi.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danchi.app.domain.Accent
import com.danchi.app.domain.FirmCardKind
import com.danchi.app.domain.FirmModeConfig
import com.danchi.app.domain.FirmStudyStatus
import com.danchi.app.domain.MeaningChoiceOption
import com.danchi.app.domain.ReviewGrade
import com.danchi.app.domain.StudyPlanOptions
import com.danchi.app.domain.StudyWordOrder
import com.danchi.app.domain.Word
import com.danchi.app.domain.WordMeaning
import com.danchi.app.domain.WordbookProgress
import com.danchi.app.domain.WordStudyRecord
import com.danchi.app.domain.buildMeaningChoiceOptions
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
        if (state.ui.activeTab != MainTab.Me) {
            activeMeEntry = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!isStudying && !isMeEntryPage) {
                DanChiBottomBar(
                    active = state.ui.activeTab,
                    onSelect = viewModel::selectTab
                )
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
                        MeScreen(
                            state = state,
                            onOpenEntry = { activeMeEntry = it }
                        )
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
private fun StudyExitConfirmDialog(
    onContinue: () -> Unit,
    onConfirmEnd: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onContinue,
        title = { Text("确认结束学习？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("今日 FSRS 队列还在推进中。")
                Text("继续完成当前题，系统会更准确地安排下次复习。")
                Text("退出后已完成记录会保留，未完成卡片会回到后续队列。")
                Text("确认结束后会返回首页。")
            }
        },
        confirmButton = {
            Button(onClick = onContinue) {
                Text("继续提分")
            }
        },
        dismissButton = {
            TextButton(onClick = onConfirmEnd) {
                Text("确认结束")
            }
        }
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
                    Text("今天不再提示（北京时间23:59:59分前）")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("确认")
            }
        }
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
            Header(title = "DanChi 单词学习", subtitle = "中考词汇 · FSRS 智能提分")
        }
        item {
            CardBlock(container = MaterialTheme.colorScheme.primaryContainer) {
                Text("今日任务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("FSRS 会自动合并到期复习和新词。", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    PlanTile("新词", state.plan.newCount.toString(), "按每日设置", Modifier.weight(1f))
                    PlanTile("到期", state.plan.dueReviewCount.toString(), "按 dueAt 优先", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Text("预计 ${state.plan.estimatedMinutes} 分钟完成", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.startSession(SessionMode.Today) },
                        enabled = !state.ui.busy,
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    ) {
                        Text("开始提分", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
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
            Text("自由练习", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PracticeButton("拼写", Icons.Outlined.Edit, Modifier.weight(1f)) { viewModel.startSession(SessionMode.Spelling) }
                PracticeButton("选义", Icons.Outlined.CheckCircle, Modifier.weight(1f)) { viewModel.startSession(SessionMode.Meaning) }
                PracticeButton("听写", Icons.Outlined.VolumeUp, Modifier.weight(1f)) { viewModel.startSession(SessionMode.Dictation) }
            }
        }
        item {
            CardBlock(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text("本地保存学习记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("词库、进度、收藏和笔记都会保存在本机，退出后再打开可以继续学习。")
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
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            LoadingCard(
                title = state.ui.loadingTitle.ifBlank { "正在准备学习队列" },
                body = state.ui.loadingDetail.ifBlank { "正在读取学习内容。" },
                progress = state.ui.loadingProgress,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }
    if (state.ui.sessionMode == SessionMode.Firm) {
        FirmStudyScreen(state, viewModel, onRequestMarkMastered)
        return
    }
    val word = state.ui.currentWord
    if (word == null) {
        if (state.ui.sessionMode == SessionMode.Today && state.ui.fsrsTailBuilding) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
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
            item {
                Header(
                    title = state.ui.sessionMode.label,
                    subtitle = "暂无学习队列"
                )
            }
            item {
                CardBlock {
                    EmptyState(
                        title = "今天没有待学习内容",
                        body = "FSRS 今日队列完成时会看到这里。可以返回首页，或继续做自由练习。"
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { viewModel.startSession(SessionMode.Today) }) { Text("开始提分") }
                    }
                }
            }
        }
    } else {
        OnePageStudyScreen(state, viewModel, word, onRequestMarkMastered)
    }
}

@Composable
private fun FirmStudyScreen(
    state: DanChiScreenState,
    viewModel: DanChiViewModel,
    onRequestMarkMastered: () -> Unit
) {
    val card = state.ui.firmCard
    val detailWord = state.ui.firmForgetDetailWord
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 700.dp
        val padding = if (compact) 10.dp else 16.dp
        val subtitle = "今日${card.todayNewStartedCount}/${card.todayNewLimit}"
        val topWord = detailWord ?: card.word ?: card.previewWords.firstOrNull()
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            StudyTopBar(
                title = "牢记模式",
                subtitle = subtitle,
                word = topWord,
                viewModel = viewModel,
                canMarkMastered = topWord != null,
                onRequestMarkMastered = onRequestMarkMastered
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    detailWord != null -> FirmDetailCard(
                        word = detailWord,
                        state = state,
                        viewModel = viewModel,
                        record = state.ui.firmDetailRecord ?: card.record,
                        compact = compact
                    )
                    card.kind == FirmCardKind.Preview -> FirmPreviewCard(card, viewModel, compact)
                    card.kind == FirmCardKind.Intro && card.word != null -> FirmChoiceCard(state, viewModel, card.word, compact)
                    card.kind == FirmCardKind.Detail && card.word != null -> FirmDetailCard(
                        word = card.word,
                        state = state,
                        viewModel = viewModel,
                        record = card.record,
                        compact = compact
                    )
                    card.kind == FirmCardKind.Recall && card.word != null -> FirmRecallCard(state, viewModel, card.word, compact)
                    else -> FirmDoneCard(state)
                }
            }
            FirmActionBar(state, viewModel)
        }
    }
}

@Composable
private fun FirmPreviewCard(
    card: com.danchi.app.domain.FirmStudyCard,
    viewModel: DanChiViewModel,
    compact: Boolean
) {
    CardBlock(Modifier.fillMaxSize()) {
        Text("新学单词预览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "今日目标 ${card.todayNewLimit} 个，先看一眼即将学习的新词。",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("预览不计入记忆次数；正式学习还需要完成 3 次记得。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            card.previewWords.forEach { word ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(word.text, fontSize = if (compact) 24.sp else 28.sp, fontWeight = FontWeight.Bold)
                        if (!word.phonetic.isNullOrBlank()) {
                            Text(word.phonetic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        MeaningList(word.displayMeanings, maxItems = 2, maxLines = 1)
                        Text(word.example, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { viewModel.speak(word) }) {
                        Icon(Icons.Outlined.VolumeUp, contentDescription = "播放")
                    }
                }
            }
        }
    }
}

@Composable
private fun FirmChoiceCard(state: DanChiScreenState, viewModel: DanChiViewModel, word: Word, compact: Boolean) {
    val options = meaningOptions(word, state.dictionary)
    val selectedOptionId = state.ui.firmSelectedOptionId

    FirmStudyCardSurface {
        FirmWordHeader(
            word = word,
            record = state.ui.firmCard.record,
            state = state,
            viewModel = viewModel,
            compact = compact
        )
        Spacer(Modifier.weight(1f))
        Text(
            "回想释义，然后选择正确的答案",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(12.dp))
        options.forEach { option ->
            val selected = selectedOptionId == option.id
            val correct = state.ui.firmCorrectOptionId == option.id
            val showFeedback = selectedOptionId != null
            val colors = when {
                showFeedback && correct -> ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFFE3F7E8),
                    contentColor = Color(0xFF166534)
                )
                showFeedback && selected -> ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFFFFE2E2),
                    contentColor = Color(0xFFB42318)
                )
                else -> ButtonDefaults.outlinedButtonColors()
            }
            OutlinedButton(
                onClick = { if (!showFeedback) viewModel.selectFirmChoice(option.id) },
                colors = colors,
                modifier = Modifier.fillMaxWidth().height(if (compact) 58.dp else 70.dp)
            ) {
                MeaningChoiceLabel(option, maxLines = 2)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FirmDetailCard(
    word: Word,
    state: DanChiScreenState,
    viewModel: DanChiViewModel,
    record: WordStudyRecord?,
    compact: Boolean
) {
    var showMore by remember(word.id) { mutableStateOf(false) }
    FirmStudyCardSurface {
        FirmWordHeader(
            word = word,
            record = record,
            state = state,
            viewModel = viewModel,
            compact = compact
        )
        Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
        SectionTitle("释义")
        MeaningList(word.displayMeanings, maxItems = 4, maxLines = 2, showPosName = true)
        Spacer(Modifier.height(16.dp))
        SectionTitle("例句")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(word.example, fontSize = 18.sp)
                Text(word.exampleCn, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (hasWordExtension(word)) {
            OutlinedButton(onClick = { showMore = !showMore }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showMore) "收起扩展内容" else "查看搭配和助记")
            }
            if (showMore) {
                Spacer(Modifier.height(10.dp))
                WordExtensionSection(word)
            }
            Spacer(Modifier.height(12.dp))
        }
        word.note?.takeIf { it.isNotBlank() }?.let {
            SectionTitle("记忆提示")
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun hasWordExtension(word: Word): Boolean {
    return word.collocations.isNotEmpty() ||
        !word.root.isNullOrBlank() ||
        word.synonyms.isNotEmpty() ||
        !word.memoryTip.isNullOrBlank()
}

@Composable
private fun WordExtensionSection(word: Word) {
    if (word.collocations.isNotEmpty()) {
        Text("常见搭配", fontWeight = FontWeight.SemiBold)
        Text(word.collocations.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
    if (!word.root.isNullOrBlank()) {
        Text("构词提示", fontWeight = FontWeight.SemiBold)
        Text(word.root, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
    if (word.synonyms.isNotEmpty()) {
        Text("近义词", fontWeight = FontWeight.SemiBold)
        Text(word.synonyms.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
    if (!word.memoryTip.isNullOrBlank()) {
        Text("助记", fontWeight = FontWeight.SemiBold)
        Text(word.memoryTip, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FirmRecallCard(state: DanChiScreenState, viewModel: DanChiViewModel, word: Word, compact: Boolean) {
    val record = state.ui.firmCard.record
    FirmStudyCardSurface {
        FirmWordHeader(
            word = word,
            record = record,
            state = state,
            viewModel = viewModel,
            compact = compact
        )
        Spacer(Modifier.weight(1f))
        Text(
            "回想单词释义",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1.35f))
    }
}

private fun firmWordFontSize(text: String, compact: Boolean) = when {
    text.length >= 16 -> if (compact) 22.sp else 26.sp
    text.length >= 13 -> if (compact) 24.sp else 28.sp
    text.length >= 10 -> if (compact) 28.sp else 32.sp
    text.length >= 7 -> if (compact) 32.sp else 36.sp
    else -> if (compact) 36.sp else 40.sp
}

@Composable
private fun FirmStudyCardSurface(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
            content = content
        )
    }
}

@Composable
private fun FirmWordHeader(
    word: Word,
    record: WordStudyRecord?,
    state: DanChiScreenState,
    viewModel: DanChiViewModel,
    compact: Boolean
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().height(if (compact) 118.dp else 142.dp)
    ) {
        MemoryDots(
            activeCount = record?.todayRememberCount ?: 0,
            total = record?.requiredRememberCount ?: FirmModeConfig.requiredRememberCount,
            modifier = Modifier.padding(top = if (compact) 17.dp else 21.dp)
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                word.text,
                fontSize = firmWordFontSize(word.text, compact),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            PronunciationControl(word, state, viewModel)
        }
    }
}

@Composable
private fun MemoryDots(activeCount: Int, total: Int, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        repeat(total.coerceAtLeast(1)) { index ->
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        color = if (index < activeCount) Color(0xFF14B8A6) else Color(0xFFD7DEE5),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun PronunciationControl(word: Word, state: DanChiScreenState, viewModel: DanChiViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = viewModel::toggleAccent,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(38.dp)
        ) {
            Text("${accentShortLabel(state.settings.accent)} ↔")
            word.phonetic?.takeIf { it.isNotBlank() }?.let {
                Text(" | $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = { viewModel.speak(word) }, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Outlined.VolumeUp, contentDescription = "播放")
        }
    }
}

private fun accentShortLabel(accent: Accent): String = when (accent) {
    Accent.Us -> "美"
    Accent.Uk -> "英"
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun FirmDoneCard(state: DanChiScreenState) {
    val summary = state.ui.firmSummary
    CardBlock(modifier = Modifier.fillMaxSize(), container = MaterialTheme.colorScheme.primaryContainer) {
        Text("今日完成", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("完成得很稳，之后会按复习间隔再安排旧词。", color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatPill("新学", summary.todayNewWords.toString(), Modifier.weight(1f))
            StatPill("复习", summary.todayReviewWords.toString(), Modifier.weight(1f))
            StatPill("完成", summary.todayCompletedWords.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text("忘记 ${summary.todayForgetCount} 次 · 选义错 ${summary.todayWrongChoiceCount} 次")
        Text("新学预览：${if (summary.previewEnabled) "已开启" else "已关闭"}")
    }
}

@Composable
private fun FirmActionBar(state: DanChiScreenState, viewModel: DanChiViewModel) {
    val card = state.ui.firmCard
    val detailWord = state.ui.firmForgetDetailWord
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
    ) {
        when {
            detailWord != null -> StudyPrimaryAction("下一词", onClick = viewModel::firmDetailNext)
            card.kind == FirmCardKind.Preview -> StudyPrimaryAction("开始学习", onClick = viewModel::completeFirmPreview)
            card.kind == FirmCardKind.Intro -> StudyPrimaryAction(
                text = if (state.ui.firmSelectedOptionId == null) "选择释义后继续" else "下一步",
                enabled = state.ui.firmSelectedOptionId != null,
                onClick = viewModel::continueAfterFirmChoice
            )
            card.kind == FirmCardKind.Detail -> StudyPrimaryAction("下一词", onClick = viewModel::firmDetailNext)
            card.kind == FirmCardKind.Recall -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = viewModel::firmForget,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFF36A),
                        contentColor = Color(0xFF222222)
                    ),
                    modifier = Modifier.weight(1f).height(64.dp)
                ) {
                    Text("忘记", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = viewModel::firmRemember,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC7E8FA),
                        contentColor = Color(0xFF222222)
                    ),
                    modifier = Modifier.weight(1f).height(64.dp)
                ) {
                    Text("记得", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { viewModel.startSession(SessionMode.Review) },
                    modifier = Modifier.weight(1f).height(64.dp)
                ) {
                    Text("复习", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { viewModel.selectTab(MainTab.Home) },
                    modifier = Modifier.weight(1f).height(64.dp)
                ) {
                    Text("首页", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StudyPrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFFD94D),
            contentColor = Color(0xFF222222),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
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
        val totalCount = if (state.ui.sessionMode == SessionMode.Today) {
            state.ui.fsrsTotalCount.takeIf { it > 0 } ?: state.ui.session.size
        } else {
            state.ui.session.size
        }
        val progress = if (totalCount == 0) 0f else {
            ((state.ui.currentIndex + 1).toFloat() / totalCount).coerceIn(0f, 1f)
        }
        val fsrsItem = state.ui.currentFsrsItem

        if (state.ui.sessionMode == SessionMode.Today && fsrsItem != null && !state.ui.fsrsInfoVisible) {
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
                if (state.ui.fsrsTailBuilding) {
                    Text(
                        state.ui.fsrsTailBuildTitle.ifBlank { "正在准备后续题目" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
            if (state.ui.sessionMode == SessionMode.Today && state.ui.fsrsTailBuilding) {
                Text(
                    state.ui.fsrsTailBuildTitle.ifBlank { "正在准备后续题目" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            CompactWordPanel(
                word = word,
                state = state,
                viewModel = viewModel,
                compact = compact,
                modifier = Modifier.weight(1f)
            )
            when (state.ui.sessionMode) {
                SessionMode.Today -> FsrsAnswerBar(state, viewModel)
                SessionMode.Spelling,
                SessionMode.Dictation -> CompactSpellingPanel(state, viewModel, word, compact)
                SessionMode.Meaning -> CompactMeaningPanel(word, state.dictionary.take(8), viewModel, compact)
                SessionMode.Firm,
                SessionMode.NewWords,
                SessionMode.Review -> CompactGradeBar(viewModel)
            }
        }
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.speak(word) },
                enabled = word != null,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Outlined.VolumeUp, contentDescription = "播放单词")
            }
            IconButton(
                onClick = { word?.let(viewModel::toggleFavorite) },
                enabled = word != null,
                modifier = Modifier.size(40.dp)
            ) {
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
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (compact) 14.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = word.text,
                    fontSize = if (compact) 38.sp else 44.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = word.book + " · " + word.unit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!word.phonetic.isNullOrBlank()) {
                        Text(
                            word.phonetic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.speak(word) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.VolumeUp,
                            contentDescription = "重读单词",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("重读", maxLines = 1)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                Text("释义", fontWeight = FontWeight.SemiBold)
                MeaningList(word.displayMeanings, maxItems = if (compact) 2 else 3, maxLines = 1)
            }

            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("例句", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.speak(word, example = true) }) {
                        Icon(Icons.Outlined.VolumeUp, contentDescription = "播放例句")
                    }
                }
                Text(
                    text = word.example,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = word.exampleCn,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!word.memoryTip.isNullOrBlank() && !compact) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = word.memoryTip,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
    val correctAnswerColor = Color(0xFFE4F7EA)
    val wrongAnswerColor = Color(0xFFFFECEB)
    val correctFeedbackColor = Color(0xFF0F8A45)
    val wrongFeedbackColor = Color(0xFFD93025)
    val actionHeight = if (compact) 52.dp else 56.dp
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(if (compact) 14.dp else 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = word.text,
                    fontSize = if (compact) 42.sp else 52.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!word.phonetic.isNullOrBlank()) {
                    Text(word.phonetic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("选择中文释义", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
                    options.forEach { option ->
                        val isSelected = selectedOptionId == option.id
                        val isCorrect = correctOptionId == option.id
                        val isWrongSelected = answered && isSelected && !isCorrect
                        val borderColor = when {
                            answered && isCorrect -> correctFeedbackColor
                            isWrongSelected -> wrongFeedbackColor
                            else -> MaterialTheme.colorScheme.outline
                        }
                        val containerColor = when {
                            answered && isCorrect -> correctAnswerColor
                            isWrongSelected -> wrongAnswerColor
                            else -> Color.Transparent
                        }
                        OutlinedButton(
                            onClick = { if (!answered) onSelect(option.id) },
                            enabled = !answered,
                            border = BorderStroke(1.dp, borderColor),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = containerColor,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContainerColor = containerColor,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth().height(if (compact) 58.dp else 68.dp)
                        ) {
                            MeaningChoiceLabel(
                                option = option,
                                maxLines = 2,
                                modifier = Modifier.weight(1f)
                            )
                            if (answered && isCorrect) {
                                Spacer(Modifier.size(8.dp))
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = "正确答案",
                                    tint = correctFeedbackColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else if (isWrongSelected) {
                                Spacer(Modifier.size(8.dp))
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "错误答案",
                                    tint = wrongFeedbackColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                if (answered) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth().height(actionHeight)
                    ) {
                        Text("继续", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    TextButton(
                        onClick = onReveal,
                        modifier = Modifier.fillMaxWidth().height(actionHeight)
                    ) {
                        Text("查看答案", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FsrsAnswerBar(state: DanChiScreenState, viewModel: DanChiViewModel) {
    val answered = state.ui.fsrsAnswered
    if (answered) {
        Button(
            onClick = viewModel::continueAfterFsrsAnswer,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("下一词", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "先完成选择题，系统会自动安排下次复习。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompactGradeBar(viewModel: DanChiViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ReviewGrade.entries.forEach { grade ->
            OutlinedButton(
                onClick = { viewModel.gradeCurrent(grade) },
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text(grade.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CompactSpellingPanel(
    state: DanChiScreenState,
    viewModel: DanChiViewModel,
    word: Word,
    compact: Boolean
) {
    CardBlock {
        Text(
            if (state.ui.sessionMode == SessionMode.Dictation) "听音拼写" else "中文拼英文",
            fontWeight = FontWeight.SemiBold
        )
        if (!compact) {
            MeaningList(word.displayMeanings, maxItems = 1, maxLines = 1)
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = state.ui.spellingInput,
            onValueChange = viewModel::updateSpellingInput,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("输入英文") }
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = viewModel::submitSpelling) { Text("提交") }
            OutlinedButton(onClick = viewModel::moveNext) { Text("下一词") }
            state.ui.spellingResult?.let {
                Text(
                    if (it) "正确" else "答案：${word.text}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CompactMeaningPanel(
    word: Word,
    candidates: List<Word>,
    viewModel: DanChiViewModel,
    compact: Boolean
) {
    val options = meaningOptions(word, candidates)
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        options.forEach { option ->
            OutlinedButton(
                onClick = { viewModel.answerMeaningPractice(option.isCorrect) },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().height(if (compact) 54.dp else 62.dp)
            ) {
                MeaningChoiceLabel(option, maxLines = 1)
            }
        }
    }
}

@Composable
private fun WordCard(word: Word, state: DanChiScreenState, viewModel: DanChiViewModel) {
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(word.text, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                Text(word.book + " · " + word.unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!word.phonetic.isNullOrBlank()) {
                    Text(word.phonetic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { viewModel.speak(word) }) {
                Icon(Icons.Outlined.VolumeUp, contentDescription = "播放单词")
            }
            IconButton(onClick = { viewModel.toggleFavorite(word) }) {
                Icon(Icons.Outlined.Star, contentDescription = "收藏")
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("释义", fontWeight = FontWeight.SemiBold)
        MeaningList(word.displayMeanings, maxItems = 4, maxLines = 2, showPosName = true)
        Spacer(Modifier.height(14.dp))
        Text("例句", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(word.example)
                Text(word.exampleCn, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { viewModel.speak(word, example = true) }) {
                Icon(Icons.Outlined.VolumeUp, contentDescription = "播放例句")
            }
        }
        Spacer(Modifier.height(14.dp))
        WordExtensionSection(word)
    }
}

@Composable
private fun GradePanel(viewModel: DanChiViewModel) {
    CardBlock {
        Text("掌握反馈", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ReviewGrade.entries.forEach { grade ->
                OutlinedButton(onClick = { viewModel.gradeCurrent(grade) }, modifier = Modifier.weight(1f)) {
                    Text(grade.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SpellingPanel(state: DanChiScreenState, viewModel: DanChiViewModel, word: Word) {
    CardBlock {
        Text(if (state.ui.sessionMode == SessionMode.Dictation) "听音拼写" else "中文拼英文", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        MeaningList(word.displayMeanings, maxItems = 2, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.ui.spellingInput,
            onValueChange = viewModel::updateSpellingInput,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("输入英文") }
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = viewModel::submitSpelling) { Text("提交") }
            state.ui.spellingResult?.let {
                Text(if (it) "正确" else "答案：${word.text}")
            }
        }
    }
}

@Composable
private fun MeaningPanel(word: Word, candidates: List<Word>, viewModel: DanChiViewModel) {
    val options = meaningOptions(word, candidates)
    CardBlock {
        Text("选择释义", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        options.forEach { option ->
            OutlinedButton(
                onClick = { viewModel.answerMeaningPractice(option.isCorrect) },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MeaningChoiceLabel(option, maxLines = 2)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MeaningList(
    meanings: List<WordMeaning>,
    maxItems: Int,
    maxLines: Int,
    showPosName: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        meanings
            .filter { it.meaning.isNotBlank() }
            .take(maxItems)
            .forEach { meaning ->
                MeaningLine(
                    meaning = meaning,
                    maxLines = maxLines,
                    showPosName = showPosName
                )
            }
    }
}

@Composable
private fun MeaningLine(meaning: WordMeaning, maxLines: Int, showPosName: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (meaning.pos.isNotBlank()) {
            PartOfSpeechTag(
                text = if (showPosName && meaning.posName.isNotBlank()) {
                    "${meaning.pos} ${meaning.posName}"
                } else {
                    meaning.pos
                }
            )
        }
        Text(
            text = meaning.meaning,
            modifier = Modifier.weight(1f),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun MeaningChoiceLabel(
    option: MeaningChoiceOption,
    maxLines: Int,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val posText = option.pos.ifBlank { option.posName }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        if (posText.isNotBlank()) {
            Text(
                text = posText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
        }
        Text(
            option.meaning,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            fontSize = 18.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PartOfSpeechTag(text: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun meaningOptions(word: Word, candidates: List<Word>): List<MeaningChoiceOption> {
    return buildMeaningChoiceOptions(word, candidates)
}

@Composable
private fun NotePanel(state: DanChiScreenState, viewModel: DanChiViewModel) {
    CardBlock {
        Text("笔记", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.ui.noteDraft,
            onValueChange = viewModel::updateNoteDraft,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text("记录易错点或助记") }
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = viewModel::saveCurrentNote) { Text("保存笔记") }
    }
}

@Composable
private fun DictionaryScreen(state: DanChiScreenState, viewModel: DanChiViewModel) {
    ScreenColumn {
        item { Header("词典", "本地查询 · 可加入生词本") }
        item {
            OutlinedTextField(
                value = state.ui.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                label = { Text("搜索单词或中文释义") }
            )
        }
        if (state.dictionary.isEmpty()) {
            item {
                CardBlock {
                    EmptyState(
                        title = if (state.ui.query.isBlank()) "正在读取词库" else "没有找到相关单词",
                        body = if (state.ui.query.isBlank()) {
                            "首次进入时需要导入本地词库，请稍等。"
                        } else {
                            "可以换一个英文单词或中文释义试试。"
                        }
                    )
                }
            }
        }
        items(state.dictionary, key = { it.id }) { word ->
            DictionaryItem(word, viewModel)
        }
    }
}

@Composable
private fun DictionaryItem(word: Word, viewModel: DanChiViewModel) {
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(word.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                MeaningList(word.displayMeanings, maxItems = 2, maxLines = 1)
            }
            IconButton(onClick = { viewModel.speak(word) }) {
                Icon(Icons.Outlined.VolumeUp, contentDescription = "播放")
            }
            IconButton(onClick = { viewModel.toggleFavorite(word) }) {
                Icon(Icons.Outlined.Star, contentDescription = "收藏")
            }
        }
    }
}

@Composable
private fun StatsScreen(state: DanChiScreenState) {
    ScreenColumn {
        item { Header("统计", "学习进度与质量") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("总词量", state.stats.total.toString(), Modifier.weight(1f))
                MetricCard("已学", state.stats.learned.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("掌握", state.stats.mastered.toString(), Modifier.weight(1f))
                MetricCard("拼写正确率", "${(state.stats.spellingAccuracy * 100).roundToInt()}%", Modifier.weight(1f))
            }
        }
        item {
            CardBlock {
                Text("掌握度分布", fontWeight = FontWeight.SemiBold)
                ProgressLine("新词", state.stats.newWords, state.stats.total)
                ProgressLine("学习中", state.stats.learning, state.stats.total)
                ProgressLine("复习中", state.stats.review, state.stats.total)
                ProgressLine("已掌握", state.stats.mastered, state.stats.total)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeScreen(
    state: DanChiScreenState,
    onOpenEntry: (MeEntry) -> Unit
) {
    ScreenColumn {
        item { Header("我的", "基本信息") }
        item {
            CardBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(14.dp).size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("DanChi 用户", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            state.selectedWordbook?.wordbook?.title ?: "正在读取本地词库",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ProfileMetricCard(
                    "连续学习",
                    state.profileStats.streakDays.toString(),
                    "天",
                    Modifier.weight(1f)
                )
                ProfileMetricCard(
                    "累计学习",
                    state.profileStats.totalStudyDays.toString(),
                    "天",
                    Modifier.weight(1f)
                )
                ProfileMetricCard(
                    "累计学习",
                    state.profileStats.totalLearnedWords.toString(),
                    "词",
                    Modifier.weight(1f)
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MeEntryCard(
                    title = "学习计划",
                    subtitle = "FSRS 队列、新词上限、发音和专项练习",
                    icon = Icons.Outlined.CheckCircle,
                    selected = false,
                    onClick = { onOpenEntry(MeEntry.StudyPlan) }
                )
                MeEntryCard(
                    title = "我的词库",
                    subtitle = state.selectedWordbook?.wordbook?.title ?: "正在读取本地词库",
                    icon = Icons.Outlined.Book,
                    selected = false,
                    onClick = { onOpenEntry(MeEntry.Wordbook) }
                )
                MeEntryCard(
                    title = "软件设置",
                    subtitle = "应用偏好与本机数据",
                    icon = Icons.Outlined.Settings,
                    selected = false,
                    onClick = { onOpenEntry(MeEntry.AppSettings) }
                )
                MeEntryCard(
                    title = "使用帮助",
                    subtitle = "学习流程与常见问题",
                    icon = Icons.Outlined.Search,
                    selected = false,
                    onClick = { onOpenEntry(MeEntry.Help) }
                )
                MeEntryCard(
                    title = "意见反馈",
                    subtitle = "问题、建议和词库纠错",
                    icon = Icons.Outlined.Edit,
                    selected = false,
                    onClick = { onOpenEntry(MeEntry.Feedback) }
                )
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
                MeEntry.Feedback -> FeedbackEntryContent()
            }
        }
    }
}

private enum class MeEntry {
    StudyPlan,
    Wordbook,
    AppSettings,
    Help,
    Feedback
}

private val MeEntry.title: String
    get() = when (this) {
        MeEntry.StudyPlan -> "学习计划"
        MeEntry.Wordbook -> "我的词库"
        MeEntry.AppSettings -> "软件设置"
        MeEntry.Help -> "使用帮助"
        MeEntry.Feedback -> "意见反馈"
    }

private fun formatSpeechRate(value: Float): String {
    return String.format(Locale.US, "%.2f", value)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeEntryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
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
        DailyNewWordOptionGrid(
            selected = state.settings.dailyNewWords,
            onSelect = viewModel::updateDailyNew
        )
        Spacer(Modifier.height(12.dp))
        WordOrderOptionRow(
            selected = state.settings.wordOrder,
            onSelect = viewModel::updateWordOrder
        )
        Spacer(Modifier.height(12.dp))
        Divider()
        Text("开始提分会先出题，再展示单词详情；信息页本身不写入复习记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Spacer(Modifier.height(8.dp))
        Divider()
        Spacer(Modifier.height(10.dp))
        Text("学习入口", fontWeight = FontWeight.SemiBold)
        ModeInfo("开始提分", "唯一主学习入口，按 FSRS 合并 learning、relearning、review 和新词。")
        ModeInfo("专项练习", "拼写、选义和听写用于自由练习，不替代今日 FSRS 主队列。")
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
            WordbookOption(
                item = selected,
                selected = true,
                onSelect = { viewModel.updateSelectedWordbook(selected.wordbook.id) }
            )
            Text(
                "当前只有初中词库，后续可继续添加高中、四级等词库。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppSettingsEntryContent() {
    CardBlock {
        Text("软件设置", fontWeight = FontWeight.SemiBold)
        Text("词库、学习记录、收藏和笔记保存在本机。")
    }
}

@Composable
private fun HelpEntryContent() {
    CardBlock {
        Text("使用帮助", fontWeight = FontWeight.SemiBold)
        ModeInfo("开始提分", "唯一主学习入口，系统按 FSRS 优先安排到期卡片，再按每日设置补入新词。")
        ModeInfo("答题记录", "只有选择、拼写等回忆行为会写入记录；答题后的详情页不算复习。")
        ModeInfo("专项练习", "拼写、选义和听写用于额外训练，主进度以开始提分队列为准。")
        ModeInfo("词典", "可以搜索单词、收藏和记录笔记。")
    }
}

@Composable
private fun FeedbackEntryContent() {
    CardBlock {
        Text("意见反馈", fontWeight = FontWeight.SemiBold)
        Text("可以反馈软件问题、功能建议和词库释义纠错。")
    }
}

@Composable
private fun ProfileMetricCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.height(96.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModeInfo(title: String, description: String) {
    Text(title, fontWeight = FontWeight.SemiBold)
    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
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
                Text(
                    "${item.learned}/${item.total} 已学 · ${item.wordbook.version}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.wordbook.description.isNotBlank()) {
                    Text(
                        item.wordbook.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
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
private fun LoadingCard(
    title: String,
    body: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
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
        LinearProgressIndicator(
            progress = { safeProgress },
            modifier = Modifier.fillMaxWidth()
        )
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
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                helper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PracticeButton(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(64.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, maxLines = 1)
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
    Divider()
}
