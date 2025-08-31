package com.example.garageelectricitymeter2

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    private val viewModel: ElectricityViewModel by viewModels {
        ElectricityViewModelFactory((applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GarageElectricityMeter2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ElectricityMeterApp(viewModel = viewModel)
                }
            }
        }
    }
}

class ElectricityViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ElectricityViewModel::class.java)) {
            return ElectricityViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ElectricityViewModel(context: Context) : ViewModel() {
    private val dataStoreManager = DataStoreManager(context)
    private val _records = mutableStateListOf<ElectricityRecord>()
    val records: List<ElectricityRecord> get() = _records

    var previousReading by mutableStateOf(0.0)
        private set

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Загружаем предыдущие показания
            dataStoreManager.previousReading.collect { reading ->
                previousReading = reading
            }
        }

        viewModelScope.launch {
            // Загружаем записи
            val loadedRecords = dataStoreManager.getRecords()
            _records.clear()
            _records.addAll(loadedRecords)
        }
    }

    suspend fun addRecord(record: ElectricityRecord) {
        _records.add(record)
        previousReading = record.currentReading

        // Сохраняем в DataStore
        dataStoreManager.saveRecord(record, _records.size - 1)
        dataStoreManager.saveRecordsCount(_records.size)
        dataStoreManager.savePreviousReading(previousReading)
    }

    suspend fun removeRecord(id: String) {
        val index = _records.indexOfFirst { it.id == id }
        if (index != -1) {
            _records.removeAt(index)

            // Обновляем в DataStore
            dataStoreManager.removeRecord(index)
            dataStoreManager.saveRecordsCount(_records.size)

            // Пересохраняем оставшиеся записи с правильными индексами
            for (i in index until _records.size) {
                dataStoreManager.saveRecord(_records[i], i)
            }
        }
    }
}

@Composable
fun ElectricityMeterApp(viewModel: ElectricityViewModel) {
    var currentReading by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<ElectricityRecord?>(null) }
    val tariff = 5.0
    val coroutineScope = rememberCoroutineScope()

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

        Text(
            text = "Предыдущие показания: ${"%.2f".format(viewModel.previousReading)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val cleanedInput = currentReading.replace(',', '.')
                val current = cleanedInput.toDoubleOrNull() ?: 0.0
                val consumption = current - viewModel.previousReading
                val cost = consumption * tariff

                if (current > viewModel.previousReading && consumption > 0) {
                    val record = ElectricityRecord(
                        id = UUID.randomUUID().toString(),
                        date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()),
                        previousReading = viewModel.previousReading,
                        currentReading = current,
                        consumption = consumption,
                        cost = cost
                    )

                    coroutineScope.launch {
                        viewModel.addRecord(record)
                    }
                    currentReading = ""
                } else if (current <= viewModel.previousReading) {
                    currentReading = "Ошибка: показания меньше предыдущих!"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить показания")
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                        recordToDelete?.let { record ->
                            coroutineScope.launch {
                                viewModel.removeRecord(record.id)
                            }
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

// RecordCard и GarageElectricityMeter2Theme остаются без изменений

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