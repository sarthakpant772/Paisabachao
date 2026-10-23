package com.spendguard.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private const val DAILY = 950.0
private const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
private val ink = Color(0xFF172C28)
private val mint = Color(0xFFBDF4CE)
private val canvas = Color(0xFFF5F7F2)

class MainActivity : ComponentActivity() {
    private val india = ZoneId.of("Asia/Kolkata")
    private val store by lazy { getSharedPreferences("paisabachao", MODE_PRIVATE) }
    private fun today() = LocalDate.now(india).toString()
    private fun manual(day: String) = store.getFloat("manual_$day", 0f).toDouble()
    private fun auto(day: String) = store.getFloat("auto_$day", 0f).toDouble()
    private fun money(n: Double) = "₹" + String.format(Locale.US, "%,.0f", n)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("budget", "Budget updates", NotificationManager.IMPORTANCE_DEFAULT))
        setContent {
            var day by remember { mutableStateOf(today()) }
            var version by remember { mutableIntStateOf(0) }
            var amount by remember { mutableStateOf("") }
            var account by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(this)) }
            var busy by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("Sign in to connect Gmail alerts") }
            val gso = remember { GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail().requestScopes(Scope(GMAIL_SCOPE)).build() }
            val client = remember { GoogleSignIn.getClient(this, gso) }
            val signin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                try {
                    account = GoogleSignIn.getSignedInAccountFromIntent(result.data).result
                    status = "Connected as ${account?.email}. Tap Sync transactions."
                } catch (e: Exception) {
                    status = "Google sign-in failed: ${e.localizedMessage ?: "check OAuth setup"}"
                }
            }
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            // Refresh on activity resume as well, so a new calendar day never rolls over.
            DisposableEffect(Unit) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { day = today(); version++ }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            val spent = remember(day, version) { manual(day) + auto(day) }
            val left = DAILY - spent
            MaterialTheme(colorScheme = lightColorScheme(primary = ink, background = canvas, surface = Color.White)) {
                Column(Modifier.fillMaxSize().background(canvas).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("paisabachao.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = ink)
                            Text("Spend smarter. Save more.", color = Color.Gray)
                        }
                        Surface(color = mint, shape = RoundedCornerShape(50.dp)) {
                            Text("₹950 / day", Modifier.padding(horizontal = 14.dp, vertical = 9.dp), fontWeight = FontWeight.Bold, color = ink)
                        }
                    }
                    Surface(color = ink, shape = RoundedCornerShape(26.dp)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("AVAILABLE TODAY", color = mint, style = MaterialTheme.typography.labelLarge)
                            Text(money(left.coerceAtLeast(0.0)), color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                            LinearProgressIndicator(progress = { (spent / DAILY).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(9.dp), color = mint, trackColor = Color(0xFF43534F))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Spent ${money(spent)}", color = Color.White)
                                Text(if (left < 0) "Over by ${money(-left)}" else "Resets midnight IST", color = mint)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Metric("Manual", money(manual(day)), Modifier.weight(1f))
                        Metric("Gmail detected", money(auto(day)), Modifier.weight(1f))
                    }
                    Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Add an expense", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount in rupees") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                val value = amount.toDoubleOrNull()
                                if (value != null && value > 0 && value < 1000000) {
                                    day = today()
                                    store.edit().putFloat("manual_${today()}", (manual(today()) + value).toFloat()).apply()
                                    version++; amount = ""
                                    notifyBalance()
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Add expense") }
                        }
                    }
                    Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Connect your bank alerts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Google sign-in + read-only Gmail access. HDFC and Axis debit purchase alerts only.", color = Color.DarkGray)
                            if (account == null) {
                                OutlinedButton(onClick = { signin.launch(client.signInIntent) }, modifier = Modifier.fillMaxWidth()) { Text("G  Continue with Google") }
                            } else {
                                Text(account?.email ?: "Google connected", fontWeight = FontWeight.SemiBold)
                                Button(enabled = !busy, onClick = {
                                    val selected = account
                                    if (selected != null) {
                                        busy = true; status = "Reading recent bank alerts..."
                                        Thread {
                                            val message = try { syncGmail(selected) } catch (e: Exception) {
                                                "Sync failed: ${e.localizedMessage ?: "OAuth/Gmail API configuration required"}"
                                            }
                                            runOnUiThread { busy = false; status = message; day = today(); version++; notifyBalance() }
                                        }.start()
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Syncing..." else "Sync Gmail transactions") }
                                TextButton(onClick = { client.signOut().addOnCompleteListener { account = null; status = "Signed out" } }) { Text("Sign out") }
                            }
                            Text(status, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Text("Only clearly identified purchases are counted. Review your bank statement for accuracy; transfers, investments and card bill payments are excluded by keyword rules.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    Text("Personal prototype • Data stays on this phone • No automatic background Gmail polling yet", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Metric(title: String, value: String, modifier: Modifier = Modifier) {
        Surface(modifier = modifier, color = Color.White, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(17.dp)) {
                Text(title, color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = ink)
            }
        }
    }

    private fun api(url: String, token: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        return try {
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("Gmail HTTP $code: ${text.take(160)}")
            JSONObject(text)
        } finally { connection.disconnect() }
    }

    private fun syncGmail(user: GoogleSignInAccount): String {
        val googleAccount = user.account ?: return "Google account unavailable; sign in again."
        val token = GoogleAuthUtil.getToken(this, googleAccount, "oauth2:$GMAIL_SCOPE")
        val query = java.net.URLEncoder.encode("newer_than:3d {HDFC Axis}", "UTF-8")
        val list = api("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=75&q=$query", token)
        val items = list.optJSONArray("messages") ?: return "No matching bank alerts in the past 3 days."
        var added = 0
        var skipped = 0
        for (i in 0 until items.length()) {
            val id = items.getJSONObject(i).optString("id")
            if (id.isBlank() || store.getBoolean("seen_$id", false)) continue
            val msg = api("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=metadata", token)
            val headers = msg.optJSONObject("payload")?.optJSONArray("headers")
            var sender = ""; var subject = ""
            if (headers != null) for (j in 0 until headers.length()) {
                val h = headers.getJSONObject(j)
                when (h.optString("name").lowercase()) {
                    "from" -> sender = h.optString("value")
                    "subject" -> subject = h.optString("value")
                }
            }
            val body = "$subject ${msg.optString("snippet")}"
            val senderText = "$sender $subject".lowercase()
            val isBank = listOf("hdfc", "axisbank", "axis bank").any { senderText.contains(it) }
            val excluded = Regex("(?i)transfer|neft|imps|rtgs|upi collect|sip|mutual fund|investment|credit card bill|card payment|bill payment|statement|refund|reversal|credited|withdrawal|atm|loan|emi").containsMatchIn(body)
            val purchase = Regex("(?i)debited|spent|purchase|paid|transaction of").containsMatchIn(body)
            val amount = Regex("(?i)(?:INR|Rs\\.?|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)").find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            val timestamp = msg.optString("internalDate").toLongOrNull()
            val date = timestamp?.let { Instant.ofEpochMilli(it).atZone(india).toLocalDate().toString() }
            if (isBank && !excluded && purchase && amount != null && amount > 0 && amount < 1000000 && date != null) {
                store.edit().putFloat("auto_$date", (auto(date) + amount).toFloat()).putBoolean("seen_$id", true).apply()
                added++
            } else {
                skipped++
                // Uncertain alerts remain uncounted, not permanently discarded.
            }
        }
        return "Imported $added purchase alert(s). Skipped $skipped uncertain/non-purchase emails. Check totals against your bank."
    }

    private fun notifyBalance() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val left = DAILY - manual(today()) - auto(today())
        val notification = android.app.Notification.Builder(this, "budget")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Paisabachao • Today's balance")
            .setContentText("${money(left.coerceAtLeast(0.0))} remaining from ₹950")
            .setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(950, notification)
    }
}
