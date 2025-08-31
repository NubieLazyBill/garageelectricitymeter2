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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

// Модель данных для записи
data class ElectricityRecord(
    val id: String = UUID.randomUUID().toString(), // Уникальный ID для удаления
    val date: String,
    val previousReading: Double,
    val currentReading: Double,
    val consumption: Double,
    val cost: Double
)

// ViewModel для управления данными
class ElectricityViewModel : ViewModel() {
    private val _records = mutableStateListOf<ElectricityRecord>()
    val records: List<ElectricityRecord> get() = _records

    fun addRecord(record: ElectricityRecord) {
        _records.add(record)
    }

    fun removeRecord(id: String) {
        _records.removeAll { it.id == id }
    }

    fun setRecords(newRecords: List<ElectricityRecord>) {
        _records.clear()
        _records.addAll(newRecords)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GarageElectricityMeter2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: ElectricityViewModel = viewModel()
                    ElectricityMeterApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun ElectricityMeterApp(viewModel: ElectricityViewModel) {
    var currentReading by remember { mutableStateOf("") }
    var previousReading by remember { mutableStateOf(0.0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<ElectricityRecord?>(null) }
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
                val filteredValue = newValue.filter { it.isDigit() || it == ',' || it == '.' }
                currentReading = filteredValue.replace(',', '.')
            },
            label = { Text("Текущие показания") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Например: 123,45 или 123.45") }
        )

        // Текущее предыдущее показание
        Text(
            text = "Предыдущие показания: ${"%.2f".format(previousReading)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка сохранения
        Button(
            onClick = {
                val cleanedInput = currentReading.replace(',', '.')
                val current = cleanedInput.toDoubleOrNull() ?: 0.0
                val consumption = current - previousReading
                val cost = consumption * tariff

                if (current > previousReading && consumption > 0) {
                    val record = ElectricityRecord(
                        date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()),
                        previousReading = previousReading,
                        currentReading = current,
                        consumption = consumption,
                        cost = cost
                    )

                    viewModel.addRecord(record)
                    previousReading = current
                    currentReading = ""
                } else if (current <= previousReading) {
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

        if (viewModel.records.isEmpty()) {
            Text(
                "История пуста",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.records.reversed()) { record ->
                    RecordCard(
                        record = record,
                        onDelete = {
                            recordToDelete = record
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    }

    // Диалог подтверждения удаления
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                recordToDelete = null
            },
            title = { Text("Удалить запись?") },
            text = { Text("Вы уверены, что хотите удалить эту запись?") },
            confirmButton = {
                Button(
                    onClick = {
                        recordToDelete?.let {
                            viewModel.removeRecord(it.id)
                        }
                        showDeleteDialog = false
                        recordToDelete = null
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        recordToDelete = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun RecordCard(record: ElectricityRecord, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Дата: ${record.date}",
                style = MaterialTheme.typography.bodySmall
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

            Spacer(modifier = Modifier.height(8.dp))

            // Кнопка удаления
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Удалить")
            }
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