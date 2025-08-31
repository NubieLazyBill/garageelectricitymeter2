package com.example.garageelectricitymeter2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

// Модель данных для записи
data class ElectricityRecord(
    val date: String,
    val previousReading: Double,
    val currentReading: Double,
    val consumption: Double,
    val cost: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GarageElectricityMeter2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ElectricityMeterApp()
                }
            }
        }
    }
}

@Composable
fun ElectricityMeterApp() {
    var currentReading by remember { mutableStateOf("") }
    var previousReading by remember { mutableStateOf(0.0) }
    val records = remember { mutableStateListOf<ElectricityRecord>() }
    val tariff = 5.0 // стоимость 1 кВт*ч

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Счётчик электроэнергии",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Поле ввода текущих показаний
        OutlinedTextField(
            value = currentReading,
            onValueChange = { newValue ->
                // Фильтруем ввод: разрешаем только цифры, запятую и точку
                val filteredValue = newValue.filter { it.isDigit() || it == ',' || it == '.' }

                // Заменяем запятую на точку для корректного преобразования
                currentReading = filteredValue.replace(',', '.')
            },
            label = { Text("Текущие показания") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка сохранения
        Button(
            onClick = {
                // Преобразуем строку в число, заменяя запятую на точку если нужно
                val cleanedInput = currentReading.replace(',', '.')
                val current = cleanedInput.toDoubleOrNull() ?: 0.0
                val consumption = current - previousReading
                val cost = consumption * tariff

                if (current > previousReading && consumption > 0) {
                    val record = ElectricityRecord(
                        date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()),
                        previousReading = previousReading,
                        currentReading = current,
                        consumption = consumption,
                        cost = cost
                    )

                    records.add(record)
                    previousReading = current
                    currentReading = ""
                } else if (current <= previousReading) {
                    // Можно показать сообщение об ошибке
                    currentReading = "Ошибка: показания меньше предыдущих!"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить показания")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // История записей
        Text(
            text = "История показаний:",
            style = MaterialTheme.typography.headlineSmall
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(records.reversed()) { record ->
                RecordCard(record = record)
            }
        }
    }
}

@Composable
fun RecordCard(record: ElectricityRecord) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Дата: ${record.date}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Показания: ${"%.1f".format(record.previousReading)} → ${"%.1f".format(record.currentReading)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Расход: ${"%.2f".format(record.consumption)} кВт*ч",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Стоимость: ${"%.2f".format(record.cost)} руб.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun GarageElectricityMeter2Theme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        content = content
    )
}