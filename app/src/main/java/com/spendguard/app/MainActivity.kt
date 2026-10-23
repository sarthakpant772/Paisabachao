package com.spendguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { getSharedPreferences("budget", MODE_PRIVATE) }
            var amount by remember { mutableStateOf("") }
            var day by remember { mutableStateOf(LocalDate.now().toString()) }
            var spent by remember { mutableDoubleStateOf(prefs.getFloat(day, 0f).toDouble()) }
            val remaining = 950.0 - spent
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("Paisabachao", style = MaterialTheme.typography.headlineLarge)
                    Text("Daily limit ₹950 • No rollover")
                    Spacer(Modifier.height(24.dp))
                    Text("₹%.2f remaining today".format(remaining.coerceAtLeast(0.0)), style = MaterialTheme.typography.headlineMedium)
                    Text("Spent ₹%.2f".format(spent))
                    if (remaining < 0) Text("Over budget by ₹%.2f".format(-remaining), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Log expense (₹)") })
                    Button(onClick = {
                        val today = LocalDate.now().toString()
                        if (today != day) { day = today; spent = prefs.getFloat(today, 0f).toDouble() }
                        val value = amount.toDoubleOrNull()
                        if (value != null && value > 0) {
                            spent += value
                            prefs.edit().putFloat(today, spent.toFloat()).apply()
                            amount = ""
                        }
                    }) { Text("Add expense") }
                    Spacer(Modifier.height(16.dp))
                    Text("Test version: manual expense logging. Gmail sync and OAuth not enabled yet.")
                }
            }
        }
    }
}
