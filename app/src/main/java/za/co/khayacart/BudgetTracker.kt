package za.co.khayacart

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

private data class Expense(val id: String, val cents: Long, val note: String, val at: Long)
private val zaLocale = Locale("en", "ZA")
private fun rand(cents: Long): String = String.format(zaLocale, "R %,.2f", cents / 100.0)
private fun parseRand(text: String): Long? = try {
    val value = BigDecimal(text.trim().replace(',', '.'))
    if (value.scale() > 2 || value <= BigDecimal.ZERO || value > BigDecimal("1000000")) null
    else value.movePointRight(2).longValueExact()
} catch (_: Exception) { null }
private fun loadExpenses(context: Context): List<Expense> {
    val raw = context.getSharedPreferences("khaya_budget_v1", Context.MODE_PRIVATE).getString("expenses", "[]") ?: "[]"
    return try {
        val a = JSONArray(raw)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Expense(o.getString("id"), o.getLong("cents"), o.optString("note", ""), o.getLong("at"))
        }
    } catch (_: Exception) { emptyList() }
}
private fun saveExpenses(context: Context, expenses: List<Expense>) {
    val array = JSONArray()
    expenses.forEach { array.put(JSONObject().put("id", it.id).put("cents", it.cents).put("note", it.note).put("at", it.at)) }
    context.getSharedPreferences("khaya_budget_v1", Context.MODE_PRIVATE).edit().putString("expenses", array.toString()).apply()
}
private fun cycle(monthly: Boolean): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (monthly) set(Calendar.DAY_OF_MONTH, 1) else {
            val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
            add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
        }
    }
    val end = (start.clone() as Calendar).apply { if (monthly) add(Calendar.MONTH, 1) else add(Calendar.DAY_OF_MONTH, 7) }
    return start.timeInMillis to end.timeInMillis
}

@Composable
fun OfflineBudgetTracker(weeklyBudget: Double, onWeeklyBudget: (Double) -> Unit, pendingEstimate: Double, unpricedCount: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("khaya_budget_v1", Context.MODE_PRIVATE) }
    var monthly by remember { mutableStateOf(false) }
    var monthlyLimit by remember { mutableLongStateOf(prefs.getLong("monthly_limit_cents", 0L)) }
    var expenses by remember { mutableStateOf(loadExpenses(context)) }
    var showLimit by remember { mutableStateOf(false) }
    var showSpend by remember { mutableStateOf(false) }
    var showAllExpenses by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Expense?>(null) }
    val (start, end) = cycle(monthly)
    val current = expenses.filter { it.at >= start && it.at < end }.sortedByDescending { it.at }
    val spent = current.sumOf { it.cents }
    val limit = if (monthly) monthlyLimit else (weeklyBudget * 100).roundToLong().coerceAtLeast(0L)
    val planned = (pendingEstimate.coerceAtLeast(0.0) * 100).roundToLong()
    val date = remember { SimpleDateFormat("dd/MM/yyyy", zaLocale) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Budget & spending", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Saved on this phone • no internet needed", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!monthly) Button(onClick = { monthly = false }) { Text("Weekly") } else OutlinedButton(onClick = { monthly = false }) { Text("Weekly") }
                if (monthly) Button(onClick = { monthly = true }) { Text("Monthly") } else OutlinedButton(onClick = { monthly = true }) { Text("Monthly") }
            }
            Text("${date.format(start)} – ${date.format(end - 1)}", style = MaterialTheme.typography.bodySmall)
            Text("Limit: ${if (limit > 0) rand(limit) else "Not set"}")
            Text("Actual spending: ${rand(spent)}", fontWeight = FontWeight.SemiBold)
            if (limit > 0) {
                val left = limit - spent
                Text(if (left >= 0) "Remaining: ${rand(left)}" else "Over budget by ${rand(-left)}", color = if (left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text("Unchecked list estimate: ${rand(planned)}", style = MaterialTheme.typography.bodyMedium)
            if (limit > 0) {
                val after = limit - spent - planned
                Text(if (after >= 0) "After estimated list: ${rand(after)} left" else "After estimated list: ${rand(-after)} over", style = MaterialTheme.typography.bodyMedium)
            }
            if (unpricedCount > 0) Text("$unpricedCount unchecked item(s) have no price, so the estimate is incomplete.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text("Record your actual shop below. List prices are estimates, not spending. Weeks run Monday–Sunday; spending also counts in its calendar month.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showLimit = true }) { Text("Set limit") }
                Button(onClick = { showSpend = true }) { Text("Add spending") }
            }
            if (current.isNotEmpty()) {
                Text("Spending this ${if (monthly) "month" else "week"}", fontWeight = FontWeight.SemiBold)
                (if (showAllExpenses) current else current.take(10)).forEach { expense ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("${rand(expense.cents)}  ${expense.note.ifBlank { "Shopping" }}")
                            Text(date.format(expense.at), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { deleting = expense }) { Text("Remove") }
                    }
                }
            if (current.size > 10) TextButton(onClick = { showAllExpenses = !showAllExpenses }) { Text(if (showAllExpenses) "Show recent entries" else "Show all ${current.size} entries") }
            }
        }
    }
    if (showLimit) {
        var text by remember { mutableStateOf(if (limit > 0) String.format(Locale.US, "%.2f", limit / 100.0) else "") }
        var error by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { showLimit = false }, title = { Text("Set ${if (monthly) "monthly" else "weekly"} limit") }, text = {
            Column { OutlinedTextField(value = text, onValueChange = { text = it; error = false }, label = { Text("Amount in rand") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = error); if (error) Text("Enter an amount from R 0.01 to R 1,000,000 with at most 2 decimal places.", color = MaterialTheme.colorScheme.error) }
        }, confirmButton = { TextButton(onClick = {
            val value = parseRand(text)
            if (value == null) error = true else { if (monthly) { monthlyLimit = value; prefs.edit().putLong("monthly_limit_cents", value).apply() } else onWeeklyBudget(value / 100.0); showLimit = false }
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showLimit = false }) { Text("Cancel") } })
    }
    if (showSpend) {
        var text by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; var spendingDate by remember { mutableStateOf(date.format(java.util.Date())) }; var dateError by remember { mutableStateOf(false) }; var error by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { showSpend = false }, title = { Text("Add actual spending") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the total you paid and the date of the purchase.")
                OutlinedTextField(value = text, onValueChange = { text = it; error = false }, label = { Text("Amount in rand") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = error)
                OutlinedTextField(value = note, onValueChange = { note = it.take(80) }, label = { Text("Store or note (optional)") }, singleLine = true)
                OutlinedTextField(value = spendingDate, onValueChange = { spendingDate = it; dateError = false }, label = { Text("Date (DD/MM/YYYY)") }, singleLine = true, isError = dateError)
                if (dateError) Text("Enter a valid date in DD/MM/YYYY format.", color = MaterialTheme.colorScheme.error)
                if (error) Text("Enter an amount from R 0.01 to R 1,000,000 with at most 2 decimal places.", color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TextButton(onClick = {
            val value = parseRand(text)
            val format = SimpleDateFormat("dd/MM/yyyy", zaLocale).apply { isLenient = false }
            val position = java.text.ParsePosition(0)
            val parsedDate = format.parse(spendingDate, position)
            val validDate = if (spendingDate.matches(Regex("""\d{2}/\d{2}/\d{4}""")) && position.index == spendingDate.length) parsedDate else null
            error = value == null; dateError = validDate == null
            if (value != null && validDate != null) { expenses = expenses + Expense(UUID.randomUUID().toString(), value, note.trim(), validDate.time); saveExpenses(context, expenses); showSpend = false }
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = { showSpend = false }) { Text("Cancel") } })
    }
    deleting?.let { expense ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Remove spending entry?") }, text = { Text("Remove ${rand(expense.cents)} recorded on ${date.format(expense.at)}? This changes your budget totals.") }, confirmButton = { TextButton(onClick = { expenses = expenses.filterNot { it.id == expense.id }; saveExpenses(context, expenses); deleting = null }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}
