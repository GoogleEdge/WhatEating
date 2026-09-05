package com.googleedge.shixian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

class RecipeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val recipe = runCatching { Recipe.fromJson(JSONObject(intent.getStringExtra("recipe").orEmpty())) }.getOrNull()
        if (recipe == null) { finish(); return }
        setContent { ShiXianTheme { RecipeDetailPage(recipe, AppStore(this), ::finish) } }
    }
}

@Composable internal fun RecipeDetailPage(recipe: Recipe, store: AppStore, close: () -> Unit) {
    var favorite by remember { mutableStateOf(store.favorites.any { it.id == recipe.id }) }
    var cooking by remember { mutableStateOf(false) }
    if (cooking) { CookingSteps(recipe, { cooking = false }); return }
    Scaffold(topBar = { TopAppBar(title = { Text("菜谱详情") }, navigationIcon = { IconButton(close) { Icon(Icons.Rounded.ArrowBack, null) } }, actions = { IconButton({ favorite = !favorite; store.favorites = if (favorite) store.favorites + recipe else store.favorites.filterNot { it.id == recipe.id } }) { Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = MaterialTheme.colorScheme.primary) } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            item { Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) { Text(recipe.name, fontSize = 32.sp, fontWeight = FontWeight.Bold); Text(recipe.reason, Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SuggestionChip({}, { Text("${recipe.totalMinutes} 分钟") }, icon = { Icon(Icons.Rounded.Timer, null, Modifier.size(18.dp)) }); SuggestionChip({}, { Text(recipe.difficulty) }); SuggestionChip({}, { Text(recipe.estimatedCost) }) }; Text("${recipe.weather}\n${recipe.season}", Modifier.padding(vertical = 14.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary) } }
            item { DetailTitle("所需食材") }
            items(recipe.ingredients) { ingredient -> ListItem(headlineContent = { Text(ingredient.name) }, supportingContent = { Text(ingredient.amount) }, trailingContent = { if (ingredient.status == "缺少") TextButton({ val item = "${ingredient.name} ${ingredient.amount}"; if (item !in store.shopping) store.shopping = store.shopping + item }) { Text("加入清单") } else StatusBadge(ingredient.status) }) }
            item { DetailTitle("做法") }
            items(recipe.steps.withIndex().toList()) { (index, step) -> ListItem(headlineContent = { Text("${index + 1}. ${step.title}", fontWeight = FontWeight.Bold) }, supportingContent = { Text("${step.detail}\n判断：${step.check}") }, leadingContent = { Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) { Text("${step.minutes}′", Modifier.padding(9.dp), fontSize = 12.sp) } }) }
            if (recipe.safety.isNotEmpty()) item { ElevatedCard(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) { Column(Modifier.padding(16.dp)) { Text("安全提醒", fontWeight = FontWeight.Bold); recipe.safety.forEach { Text("• $it", Modifier.padding(top = 6.dp)) } } } }
            item { Button({ cooking = true }, Modifier.fillMaxWidth().padding(16.dp).height(54.dp), shape = RoundedCornerShape(17.dp)) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("开始烹饪") } }
        }
    }
}

@Composable private fun DetailTitle(s: String) { Text(s, Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold) }
@Composable internal fun StatusBadge(s: String) { Surface(shape = CircleShape, color = if (s == "已有") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) { Text(s, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 12.sp) } }

@Composable private fun CookingSteps(recipe: Recipe, back: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }; val step = recipe.steps.getOrNull(index)
    Column(Modifier.fillMaxSize().padding(20.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.Rounded.ArrowBack, null) }; Text("烹饪模式", fontWeight = FontWeight.Bold) }; LinearProgressIndicator({ if (recipe.steps.isEmpty()) 0f else (index + 1f) / recipe.steps.size }, Modifier.fillMaxWidth().padding(vertical = 18.dp)); Text("步骤 ${index + 1} / ${recipe.steps.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Spacer(Modifier.height(14.dp)); Text(step?.title.orEmpty(), fontSize = 32.sp, fontWeight = FontWeight.Bold); Text(step?.detail.orEmpty(), Modifier.padding(vertical = 22.dp), fontSize = 19.sp, lineHeight = 29.sp); ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(16.dp)) { Text("完成判断", fontWeight = FontWeight.Bold); Text(step?.check.orEmpty(), Modifier.padding(top = 5.dp)) } }; Spacer(Modifier.weight(1f)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton({ if (index > 0) index-- }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("上一步") }; Button({ if (index < recipe.steps.lastIndex) index++ else back() }, Modifier.weight(1f)) { Text(if (index == recipe.steps.lastIndex) "完成" else "下一步") } } }
}
