package com.googleedge.shixian

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ShiXianTheme { ShiXianApp(AppStore(this)) } }
    }
}

private val FreshLight = lightColorScheme(
    primary = Color(0xFF276B46), onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F1C8), onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF4F6354), secondaryContainer = Color(0xFFD2E8D5),
    tertiary = Color(0xFF3A656F), tertiaryContainer = Color(0xFFBDEAF5),
    background = Color(0xFFF7FBF5), surface = Color(0xFFF7FBF5),
    surfaceContainer = Color(0xFFEBF0E9), surfaceContainerHigh = Color(0xFFE5EAE3)
)

@Composable internal fun ShiXianTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = FreshLight, typography = Typography(), content = content) }

private enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("推荐", Icons.Rounded.AutoAwesome), FRIDGE("冰箱", Icons.Rounded.Kitchen), SAVED("收藏", Icons.Rounded.Favorite), SETTINGS("设置", Icons.Rounded.Settings)
}

@Composable private fun ShiXianApp(store: AppStore) {
    var tab by remember { mutableStateOf(if (store.apiKey.isBlank()) AppTab.SETTINGS else AppTab.HOME) }
    var ingredients by remember { mutableStateOf(store.ingredients) }
    var favorites by remember { mutableStateOf(store.favorites) }
    var shopping by remember { mutableStateOf(store.shopping) }
    var task by remember { mutableStateOf(store.task) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { while (true) { task = store.task; delay(500) } }
    fun openRecipe(recipe: Recipe) { context.startActivity(Intent(context, RecipeActivity::class.java).putExtra("recipe", recipe.toJson().toString())) }
    Scaffold(bottomBar = { NavigationBar { AppTab.entries.forEach { item -> NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(item.icon, null) }, label = { Text(item.label) }) } } }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                AppTab.HOME -> HomeScreen(store, ingredients, task, { custom, answers -> store.customNeed = custom; RecommendationManager.enqueue(context, custom, answers) }, ::openRecipe) { tab = AppTab.SETTINGS }
                AppTab.FRIDGE -> FridgeScreen(store, ingredients) { ingredients = it; store.ingredients = it }
                AppTab.SAVED -> SavedScreen(favorites, shopping, ::openRecipe, { r -> favorites = favorites - r; store.favorites = favorites }, { s -> shopping = shopping - s; store.shopping = shopping })
                AppTab.SETTINGS -> SettingsScreen(store)
            }
        }
    }
}

@Composable private fun PageHeader(eyebrow: String, title: String, subtitle: String? = null) { Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) { Text(eyebrow.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Text(title, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold); subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp)) } } }

@Composable private fun HomeScreen(store: AppStore, ingredients: List<Ingredient>, task: TaskSnapshot, send: (String, String) -> Unit, open: (Recipe) -> Unit, settings: () -> Unit) {
    var need by remember { mutableStateOf(store.customNeed) }
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)) }
    val canSend = store.apiKey.isNotBlank() && store.model.isNotBlank() && task.status != TaskStatus.RUNNING
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeader(date, "今天，吃点什么？", "结合天气、时令和你的冰箱认真决定") }
        if (store.apiKey.isBlank() || store.model.isBlank()) item { ElevatedCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(18.dp)) { Icon(Icons.Rounded.Key, null); Spacer(Modifier.height(10.dp)); Text("先连接 AI", fontWeight = FontWeight.Bold, fontSize = 20.sp); Text("填写接口并选择模型后，就能开始推荐。", Modifier.padding(vertical = 6.dp)); TextButton(settings) { Text("前往设置") } } } }
        item { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { OutlinedTextField(need, { need = it }, Modifier.fillMaxWidth(), label = { Text("这顿饭有什么想法？（可不填）") }, placeholder = { Text("例如：想吃暖和一点，不要太油") }, minLines = 2, shape = RoundedCornerShape(20.dp)); Spacer(Modifier.height(12.dp)); Button({ send(need, "") }, enabled = canSend, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) { if (task.status == TaskStatus.RUNNING) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(if (task.status == TaskStatus.RUNNING) "后台正在处理…" else "帮我决定", fontWeight = FontWeight.Bold) } } }
        if (task.status == TaskStatus.RUNNING) item { TaskProgressCard(task) }
        if (task.status == TaskStatus.NEEDS_INPUT) item { ClarificationCard(task) { answer -> send(task.input, answer) } }
        if (task.status == TaskStatus.FAILED) item { ElevatedCard(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Column(Modifier.padding(16.dp)) { Text("这次没有完成", fontWeight = FontWeight.Bold); Text(task.error, Modifier.padding(vertical = 6.dp)); TextButton({ send(task.input, task.answers) }) { Text("重新尝试") } } } }
        item { ContextStrip(store.preferences, ingredients) }
        if (task.status == TaskStatus.SUCCESS) { item { Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text("为你准备了 ${task.recipes.size} 道菜", fontWeight = FontWeight.Bold, fontSize = 19.sp); Spacer(Modifier.weight(1f)); TextButton({ send(need, "") }) { Text("再来一组") } } }; items(task.recipes, key = { it.id }) { RecipeCard(it) { open(it) } } }
        else if (task.status == TaskStatus.IDLE) item { Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.RestaurantMenu, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.secondary); Text("一道好菜，从现有食材开始", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
    }
}

@Composable private fun TaskProgressCard(task: TaskSnapshot) { var expanded by remember { mutableStateOf(false) }; ElevatedCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.CloudSync, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(task.phase.ifBlank { "后台任务运行中" }, fontWeight = FontWeight.Bold); Text("切换标签页也会继续 · ${etaText(task.etaSeconds)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text("${task.progress}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }; LinearProgressIndicator({ task.progress / 100f }, Modifier.fillMaxWidth().padding(top = 14.dp)); TextButton({ expanded = !expanded }) { Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null); Spacer(Modifier.width(5.dp)); Text(if (expanded) "收起工作摘要" else "查看工作摘要") }; if (expanded) { Text("这里只展示可审计的处理步骤，不展示模型的隐式思考内容。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); task.workLog.takeLast(8).forEach { Text("• $it", Modifier.padding(top = 5.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } } }
private fun etaText(seconds: Int): String = when { seconds <= 0 -> "即将完成"; seconds < 60 -> "预计还需 ${seconds} 秒"; else -> "预计还需 ${seconds / 60} 分钟" }

@Composable private fun ClarificationCard(task: TaskSnapshot, continueWith: (String) -> Unit) { var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }; val ready = task.questions.all { values[it.id].orEmpty().isNotBlank() }; ElevatedCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) { Column(Modifier.padding(18.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.QuestionAnswer, null); Spacer(Modifier.width(8.dp)); Text("AI 想先确认两件事", fontWeight = FontWeight.Bold, fontSize = 18.sp) }; Text("这样推荐出来的菜更符合你的情况。", Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); task.questions.forEach { question -> Text(question.question, Modifier.padding(top = 8.dp), fontWeight = FontWeight.Medium); Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { question.options.forEach { option -> FilterChip(values[question.id] == option, { values = values + (question.id to option) }, label = { Text(option) }) } }; OutlinedTextField(values[question.id].orEmpty(), { values = values + (question.id to it) }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("也可以自己输入") }, singleLine = true) }; Button({ continueWith(task.questions.joinToString("；") { "${it.question}：${values[it.id]}" }) }, enabled = ready, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Icon(Icons.Rounded.ArrowForward, null); Spacer(Modifier.width(6.dp)); Text("继续生成") } } } }

@Composable private fun ContextStrip(p: Preferences, items: List<Ingredient>) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { MiniStat(Icons.Rounded.LocationOn, p.city.ifBlank { "未设城市" }, Modifier.weight(1f)); MiniStat(Icons.Rounded.Kitchen, "${items.size} 种食材", Modifier.weight(1f)); MiniStat(Icons.Rounded.Timer, "${p.minutes} 分钟", Modifier.weight(1f)) } }
@Composable private fun MiniStat(icon: ImageVector, text: String, modifier: Modifier = Modifier) { Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary); Text(text, Modifier.padding(top = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) } } }
@Composable private fun RecipeCard(r: Recipe, open: () -> Unit) { ElevatedCard(onClick = open, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(18.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) { Icon(Icons.Rounded.SoupKitchen, null, Modifier.padding(11.dp)) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(r.name, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("${r.totalMinutes} 分钟 · ${r.difficulty} · ${r.estimatedCost}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Rounded.ChevronRight, null) }; Text(r.reason, Modifier.padding(top = 14.dp), maxLines = 3, overflow = TextOverflow.Ellipsis); Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) { AssistChip({}, { Text(r.weather.take(12).ifBlank { "天气未知" }) }, leadingIcon = { Icon(Icons.Rounded.Cloud, null, Modifier.size(17.dp)) }); AssistChip({}, { Text(r.season.take(10).ifBlank { "当季" }) }) } } } }

@Composable private fun FridgeScreen(store: AppStore, current: List<Ingredient>, save: (List<Ingredient>) -> Unit) { val scope = rememberCoroutineScope(); var add by remember { mutableStateOf(false) }; var scanning by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var detected by remember { mutableStateOf<List<Ingredient>?>(null) }; fun scan(bitmap: Bitmap) { scanning = true; error = null; scope.launch { runCatching { withContext(Dispatchers.IO) { OpenAiService(store).inspect(bitmap) } }.onSuccess { detected = it }.onFailure { error = it.message }; scanning = false } }; val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { it?.let(::scan) }; val context = LocalContext.current; val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { runCatching { ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it)) }.onSuccess(::scan).onFailure { e -> error = e.message } } }; Scaffold(floatingActionButton = { ExtendedFloatingActionButton(onClick = { add = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("添加食材") }) }) { inner -> LazyColumn(Modifier.padding(inner).fillMaxSize(), contentPadding = PaddingValues(bottom = 92.dp)) { item { PageHeader("我的食材", "冰箱", "先确认食材，再让 AI 安排这顿饭") }; item { Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton({ camera.launch(null) }, enabled = !scanning, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("拍照识别") }; OutlinedButton({ pick.launch("image/*") }, enabled = !scanning, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("相册导入") } } }; if (scanning) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)); Text("正在识别，请稍候…", Modifier.padding(horizontal = 20.dp)) }; error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }; if (current.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(52.dp), contentAlignment = Alignment.Center) { Text("冰箱还是空的\n拍张照片或手动添加吧", color = MaterialTheme.colorScheme.onSurfaceVariant) } }; current.groupBy { it.category }.forEach { (category, list) -> item { Text(category, Modifier.padding(start = 20.dp, top = 24.dp, bottom = 6.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }; items(list) { ing -> ListItem(headlineContent = { Text(ing.name, fontWeight = FontWeight.Medium) }, supportingContent = { Text(ing.amount) }, leadingContent = { Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Text(ing.name.take(1), Modifier.padding(12.dp), fontWeight = FontWeight.Bold) } }, trailingContent = { IconButton({ save(current - ing) }) { Icon(Icons.Rounded.Delete, null) } }) } } } }; if (add) IngredientDialog({ add = false }) { save(current + it); add = false }; detected?.let { found -> ConfirmIngredientsDialog(found, { detected = null }) { chosen -> save((current + chosen).distinctBy { it.name }); detected = null } } }

@Composable private fun IngredientDialog(close: () -> Unit, done: (Ingredient) -> Unit) { var name by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var category by remember { mutableStateOf("蔬菜") }; AlertDialog(close, title = { Text("添加食材") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name, { name = it }, label = { Text("名称") }); OutlinedTextField(amount, { amount = it }, label = { Text("数量，例如 2 个") }); OutlinedTextField(category, { category = it }, label = { Text("分类") }) } }, confirmButton = { Button({ done(Ingredient(name.trim(), amount.ifBlank { "适量" }, category.ifBlank { "其他" })) }, enabled = name.isNotBlank()) { Text("添加") } }, dismissButton = { TextButton(close) { Text("取消") } }) }
@Composable private fun ConfirmIngredientsDialog(found: List<Ingredient>, close: () -> Unit, done: (List<Ingredient>) -> Unit) { var selected by remember { mutableStateOf(found.map { it.name }.toSet()) }; AlertDialog(close, title = { Text("确认识别结果") }, text = { LazyColumn(Modifier.heightIn(max = 420.dp)) { item { Text("AI 可能认错，请勾选照片中确实存在的食材。", color = MaterialTheme.colorScheme.onSurfaceVariant) }; items(found) { i -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(i.name in selected, { selected = if (it) selected + i.name else selected - i.name }); Column { Text(i.name); Text("${i.amount} · ${i.category}", fontSize = 12.sp) } } } } }, confirmButton = { Button({ done(found.filter { it.name in selected }) }) { Text("加入冰箱") } }, dismissButton = { TextButton(close) { Text("取消") } }) }

@Composable private fun SavedScreen(favorites: List<Recipe>, shopping: List<String>, open: (Recipe) -> Unit, remove: (Recipe) -> Unit, removeShop: (String) -> Unit) { var section by remember { mutableIntStateOf(0) }; Column(Modifier.fillMaxSize()) { PageHeader("稍后再做", "收藏与清单"); TabRow(section) { Tab(section == 0, { section = 0 }, text = { Text("菜谱 ${favorites.size}") }); Tab(section == 1, { section = 1 }, text = { Text("购物 ${shopping.size}") }) }; LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp)) { if (section == 0) { items(favorites, key = { it.id }) { r -> ListItem(headlineContent = { Text(r.name, fontWeight = FontWeight.Bold) }, supportingContent = { Text("${r.totalMinutes} 分钟 · ${r.difficulty}") }, leadingContent = { Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { IconButton({ remove(r) }) { Icon(Icons.Rounded.Delete, null) } }, modifier = Modifier.padding(horizontal = 8.dp)); HorizontalDivider() }; if (favorites.isEmpty()) item { EmptyText("还没有收藏的菜谱") } } else { items(shopping) { s -> ListItem(headlineContent = { Text(s) }, leadingContent = { Icon(Icons.Rounded.ShoppingCart, null) }, trailingContent = { IconButton({ removeShop(s) }) { Icon(Icons.Rounded.Check, null) } }) }; if (shopping.isEmpty()) item { EmptyText("缺少的食材可以从菜谱加入这里") } } } } }
@Composable private fun EmptyText(s: String) { Box(Modifier.fillMaxWidth().padding(56.dp), contentAlignment = Alignment.Center) { Text(s, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable private fun SettingsScreen(store: AppStore) { val context = LocalContext.current; val scope = rememberCoroutineScope(); var base by remember { mutableStateOf(store.baseUrl) }; var key by remember { mutableStateOf(store.apiKey) }; var model by remember { mutableStateOf(store.model) }; var models by remember { mutableStateOf<List<String>>(emptyList()) }; var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }; var expanded by remember { mutableStateOf(false) }; var p by remember { mutableStateOf(store.preferences) }; fun persist() { store.baseUrl = base; store.apiKey = key; store.model = model; store.preferences = p }; LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) { item { PageHeader("个性化", "设置", "数据保存在本机，API Key 已加密") }; item { SectionTitle("AI 接口") }; item { Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(base, { base = it }, Modifier.fillMaxWidth(), label = { Text("Base URL（HTTPS）") }, singleLine = true); OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API Key") }, singleLine = true); Box { OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("模型") }, trailingIcon = { IconButton({ expanded = true }) { Icon(Icons.Rounded.ArrowDropDown, null) } }, singleLine = true); DropdownMenu(expanded, { expanded = false }) { models.forEach { DropdownMenuItem({ Text(it) }, { model = it; expanded = false }) } } }; Button({ persist(); busy = true; message = null; scope.launch { runCatching { withContext(Dispatchers.IO) { OpenAiService(store).models() } }.onSuccess { models = it; message = "已获取 ${it.size} 个模型"; if (model.isBlank() && it.isNotEmpty()) model = it.first() }.onFailure { message = it.message }; busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("保存并获取模型") }; message?.let { Text(it, color = if (models.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } } }; item { SectionTitle("饮食偏好") }; item { Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(p.city, { p = p.copy(city = it) }, Modifier.fillMaxWidth(), label = { Text("城市（用于天气和时令）") }); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(p.budget, { p = p.copy(budget = it) }, Modifier.weight(1f), label = { Text("预算") }); OutlinedTextField(p.taste, { p = p.copy(taste = it) }, Modifier.weight(1f), label = { Text("口味") }) }; OutlinedTextField(p.allergies, { p = p.copy(allergies = it) }, Modifier.fillMaxWidth(), label = { Text("过敏食材") }); OutlinedTextField(p.avoid, { p = p.copy(avoid = it) }, Modifier.fillMaxWidth(), label = { Text("忌口") }); OutlinedTextField(p.goal, { p = p.copy(goal = it) }, Modifier.fillMaxWidth(), label = { Text("饮食目标") }); Text("用餐人数：${p.people} 人"); Slider(p.people.toFloat(), { p = p.copy(people = it.toInt()) }, valueRange = 1f..8f, steps = 6); Text("最多烹饪时间：${p.minutes} 分钟"); Slider(p.minutes.toFloat(), { p = p.copy(minutes = (it / 5).toInt() * 5) }, valueRange = 10f..120f, steps = 21); Button({ persist(); message = "设置已保存" }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("保存全部设置") } } }; item { OutlinedButton({ persist(); context.startActivity(Intent(context, LogActivity::class.java)) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Icon(Icons.Rounded.ReceiptLong, null); Spacer(Modifier.width(8.dp)); Text("查看运行日志") } }; item { Text("接口需要支持 OpenAI Responses API。若自建服务不支持 web_search，应用仍会生成菜谱并明确标注天气信息不可用。", Modifier.padding(20.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun SectionTitle(s: String) { Text(s, Modifier.padding(start = 20.dp, top = 16.dp, bottom = 10.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
