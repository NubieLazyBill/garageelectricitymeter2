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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

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
                    val migrationCompleted by viewModel.migrationCompleted.collectAsState(initial = false)
                    val coroutineScope = rememberCoroutineScope()

                    if (migrationCompleted) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ElectricityMeterApp(viewModel = viewModel)
                        }
                    } else {
                        MigrationScreen(
                            viewModel = viewModel,
                            onMigrationComplete = { }
                        )
                    }
                }
            }
        }
    }
}

class ElectricityViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
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

    val migrationCompleted: Flow<Boolean> = dataStoreManager.migrationCompleted

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
            try {
                val loadedRecords = dataStoreManager.getRecords()
                _records.clear()
                _records.addAll(loadedRecords)
                println("Загружено записей: ${_records.size}")
            } catch (e: Exception) {
                println("Ошибка загрузки записей: ${e.message}")
            }
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

    suspend fun migrateOldData(records: List<ElectricityRecord>) {
        dataStoreManager.migrateRecords(records)
        dataStoreManager.saveMigrationCompleted(true)

        // Немедленно обновляем UI, не дожидаясь асинхронной загрузки
        _records.clear()
        _records.addAll(records)
        if (records.isNotEmpty()) {
            previousReading = records.last().currentReading
        }
    }

    suspend fun skipMigration() {
        dataStoreManager.saveMigrationCompleted(true) // пропускаем миграцию
    }

    // В ElectricityViewModel добавьте:
    suspend fun resetMigration() {
        dataStoreManager.saveMigrationCompleted(false)
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



@Composable
fun MigrationScreen(
    viewModel: ElectricityViewModel,
    onMigrationComplete: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var migrationStatus by remember { mutableStateOf("Готов к миграции") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Миграция старых данных",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(migrationStatus)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    migrationStatus = "Парсим данные..."
                    try {
                        val records = parseOldData()
                        migrationStatus = "Найдено ${records.size} записей. Сохраняем..."
                        viewModel.migrateOldData(records)
                        migrationStatus = "Миграция завершена успешно! Добавлено ${records.size} записей."
                        delay(2000)
                        onMigrationComplete()
                    } catch (e: Exception) {
                        migrationStatus = "Ошибка: ${e.message}"
                    }
                }
            }
        ) {
            Text("Начать миграцию")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    viewModel.skipMigration()
                    onMigrationComplete()
                }
            }
        ) {
            Text("Пропустить миграцию")
        }
    }
}

// Функция для парсинга ваших старых данных
fun parseOldData(): List<ElectricityRecord> {
    val rawData = """
        14.10.23 - 223 1кВт - 4₽
        15.11.23 - 917
        16.12.23- 1875
        15.01.24-2951
        16.02.24-4028
        14.03.24-4860
        15.04.24-5674
        15.05.24-61534
        14.06.24-6403
        16.07.24-6428
        14.08.24-6444
        16.09.24-6576
        15.10.24-7027 1кВт - 5₽
        15.11.24 - 7839
        14.12.24-8818
        15.01.25-9861
        15.02.25-10861
        14.03.25 - 11696
        14.04.25 - 12418
        16.05.25 - 12792
        15.06.25 - 12953
        15.07.25 - 12977
        16.08.25 - 13006
    """.trimIndent()

    val records = mutableListOf<ElectricityRecord>()
    val lines = rawData.split("\n")
    var previousReading = 0.0
    var currentTariff = 4.0 // начальный тариф

    for (line in lines) {
        try {
            // Улучшенный парсинг - обрабатываем разные форматы дат
            val cleanLine = line.replace("кВт", "").replace("₽", "").trim()

            // Разделяем на части, учитывая разные разделители
            val parts = cleanLine.split("-", " ", "–")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (parts.size >= 2) {
                val dateStr = parts[0]
                val currentReading = parts[1].toDouble()

                // Определяем тариф (если указан в строке)
                if (line.contains("1кВт") && parts.size >= 3) {
                    parts[2].toDoubleOrNull()?.let {
                        currentTariff = it
                    }
                }

                val consumption = if (previousReading > 0) currentReading - previousReading else 0.0
                val cost = consumption * currentTariff

                records.add(
                    ElectricityRecord(
                        id = UUID.randomUUID().toString(),
                        date = dateStr,
                        previousReading = previousReading,
                        currentReading = currentReading,
                        consumption = consumption,
                        cost = cost
                    )
                )

                previousReading = currentReading
            }
        } catch (e: Exception) {
            println("Ошибка парсинга строки: $line - ${e.message}")
            continue
        }
    }

    println("Мигрировано записей: ${records.size}")
    records.forEach { println("Запись: ${it.date} - ${it.currentReading}") }

    return records
}