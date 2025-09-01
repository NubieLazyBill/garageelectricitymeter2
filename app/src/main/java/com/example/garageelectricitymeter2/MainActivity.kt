package com.example.garageelectricitymeter2

import android.R as AndroidR
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import androidx.compose.ui.viewinterop.AndroidView
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import androidx.compose.ui.res.painterResource
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import java.util.Calendar
import androidx.compose.material3.Text
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Build
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

// Функция для преобразования даты в сортируемый формат (год-месяц)
private fun getSortableDate(month: String, year: String): String {
    val monthNumber = when (month.toLowerCase()) {
        "январь", "jan" -> "01"
        "февраль", "feb" -> "02"
        "март", "mar" -> "03"
        "апрель", "apr" -> "04"
        "май", "may" -> "05"
        "июнь", "jun" -> "06"
        "июль", "jul" -> "07"
        "август", "aug" -> "08"
        "сентябрь", "sep" -> "09"
        "октябрь", "oct" -> "10"
        "ноябрь", "nov" -> "11"
        "декабрь", "dec" -> "12"
        else -> "00"
    }
    return "$year-$monthNumber"
}

@Composable
fun ConsumptionLineChart(chartData: List<ChartData>) {
    val entries = remember(chartData) {
        chartData.mapIndexed { index, data ->
            Entry(index.toFloat(), data.consumption.toFloat())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp) // Фиксированная высота для графика
            .padding(16.dp)
    ) {
        AndroidView(
            factory = { context ->
                LineChart(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    description.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(true)
                    setPinchZoom(true)

                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val index = value.toInt()
                            return if (index in chartData.indices) {
                                val data = chartData[index]
                                // Берем только первые 3 буквы месяца и год
                                "${data.month.take(3)} '${data.year.takeLast(2)}"
                            } else {
                                ""
                            }
                        }
                    }
                    xAxis.granularity = 1f // Чтобы метки не перекрывались
                    xAxis.setAvoidFirstLastClipping(true)

                    axisRight.isEnabled = false

                    // Настройка легенды
                    legend.isEnabled = true

                    // Настройка внешнего вида
                    setDrawBorders(true)
                    setBorderWidth(1f)
                }
            },
            update = { chart ->
                if (entries.isNotEmpty()) {
                    val dataSet = LineDataSet(entries, "Потребление электроэнергии (кВт·ч)").apply {
                        color = Color.BLUE
                        valueTextColor = Color.BLACK
                        lineWidth = 2f
                        setCircleColor(Color.RED)
                        circleRadius = 4f
                        setDrawCircleHole(false)
                        valueTextSize = 10f
                        setDrawValues(true) // Показывать значения на точках
                    }

                    chart.data = LineData(dataSet)

                    // Настройка масштабирования
                    chart.setVisibleXRangeMaximum(6f) // Показывать не более 6 месяцев
                    chart.moveViewToX((entries.size - 1).toFloat()) // Прокрутить к последнему элементу

                    chart.invalidate() // refresh
                }
            }
        )
    }
}


enum class AppScreen {
    MAIN,
    CHART
}

class MainActivity : ComponentActivity() {
    override fun onBackPressed() {
        // Завершаем активность
        finish()
    }
    private val viewModel: ElectricityViewModel by viewModels {
        ElectricityViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupMonthlyReminder()
        setContent {
            GarageElectricityMeter2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val migrationCompleted by viewModel.migrationCompleted.collectAsState(initial = false)
                    var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }

                    if (migrationCompleted) {
                        AppNavigation(
                            currentScreen = currentScreen,
                            onScreenChange = { screen -> currentScreen = screen },
                            viewModel = viewModel
                        )
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
    private fun setupMonthlyReminder() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 14)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)

            // Если сегодня уже прошло 14 число, ставим на следующий месяц
            if (get(Calendar.DAY_OF_MONTH) > 14) {
                add(Calendar.MONTH, 1)
            }
        }

        AlarmManagerCompat.setExactAndAllowWhileIdle(
            alarmManager,
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }
}

@Composable
fun AppNavigation(
    currentScreen: AppScreen,
    onScreenChange: (AppScreen) -> Unit,
    viewModel: ElectricityViewModel
) {
    when (currentScreen) {
        AppScreen.MAIN -> {
            ElectricityMeterApp(
                viewModel = viewModel,
                onShowChart = { onScreenChange(AppScreen.CHART) }
            )
        }
        AppScreen.CHART -> {
            ConsumptionChartScreen(
                viewModel = viewModel,
                onBack = { onScreenChange(AppScreen.MAIN) }
            )
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

    fun getNextReminderDate(): String {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentYear = calendar.get(Calendar.YEAR)

        return if (currentDay < 14) {
            "14.$currentMonth.$currentYear"
        } else {
            val nextMonth = if (currentMonth == 12) 1 else currentMonth + 1
            val nextYear = if (currentMonth == 12) currentYear + 1 else currentYear
            "14.$nextMonth.$nextYear"
        }
    }
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
            // Загружаем записи и пересчитываем стоимость
            try {
                val loadedRecords = dataStoreManager.getRecords()
                _records.clear()

                // Пересчитываем стоимость для каждой записи при загрузке
                val recordsWithRecalculatedCost = recalculateCostsForRecords(loadedRecords)
                _records.addAll(recordsWithRecalculatedCost)

                println("Загружено записей: ${_records.size}")
            } catch (e: Exception) {
                println("Ошибка загрузки записей: ${e.message}")
            }
        }
    }

    // Функция для пересчета стоимости при загрузке записей
    private fun recalculateCostsForRecords(records: List<ElectricityRecord>): List<ElectricityRecord> {
        var currentTariff = 4.0
        val updatedRecords = mutableListOf<ElectricityRecord>()

        for (record in records) {
            // Определяем тариф по дате
            if (isDateAfter(record.date, "14.10.24")) { // После 14.10.24 - 5 рублей
                currentTariff = 5.0
            }

            // Пересчитываем стоимость
            val newCost = record.consumption * currentTariff

            // Создаем обновленную запись
            val updatedRecord = record.copy(cost = newCost)
            updatedRecords.add(updatedRecord)
        }

        return updatedRecords
    }

    suspend fun addRecord(record: ElectricityRecord) {
        // Определяем тариф для новой записи
        val tariff = if (isDateAfter(record.date, "14.10.24")) 5.0 else 4.0
        val recordWithCorrectCost = record.copy(cost = record.consumption * tariff)

        _records.add(recordWithCorrectCost)
        previousReading = recordWithCorrectCost.currentReading

        // Сохраняем в DataStore
        dataStoreManager.saveRecord(recordWithCorrectCost, _records.size - 1)
        dataStoreManager.saveRecordsCount(_records.size)
        dataStoreManager.savePreviousReading(previousReading)
    }

    // Остальные методы остаются без изменений...
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
        println("Начало миграции: ${records.size} записей")

        // Пересчитываем стоимости перед сохранением
        val recordsWithCost = recalculateCostsForRecords(records)

        dataStoreManager.migrateRecords(recordsWithCost)
        dataStoreManager.saveMigrationCompleted(true)

        // Немедленно обновляем UI
        _records.clear()
        _records.addAll(recordsWithCost)
        if (recordsWithCost.isNotEmpty()) {
            previousReading = recordsWithCost.last().currentReading
        }

        println("Миграция завершена успешно!")
    }

    // Вспомогательная функция для сравнения дат
    private fun isDateAfter(dateStr: String, compareDateStr: String): Boolean {
        try {
            val dateParts = dateStr.split(".").map { it.toInt() }
            val compareParts = compareDateStr.split(".").map { it.toInt() }

            // Форматируем даты для сравнения (день.месяц.год)
            val date = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, dateParts[0])
                set(Calendar.MONTH, dateParts[1] - 1)
                set(Calendar.YEAR, if (dateParts[2] < 100) 2000 + dateParts[2] else dateParts[2])
            }

            val compareDate = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, compareParts[0])
                set(Calendar.MONTH, compareParts[1] - 1)
                set(Calendar.YEAR, if (compareParts[2] < 100) 2000 + compareParts[2] else compareParts[2])
            }

            return date.after(compareDate)
        } catch (e: Exception) {
            return false
        }
    }

    suspend fun skipMigration() {
        dataStoreManager.saveMigrationCompleted(true)
    }

    suspend fun resetMigration() {
        dataStoreManager.saveMigrationCompleted(false)
    }
}


@Composable
fun ElectricityMeterApp(
    viewModel: ElectricityViewModel,
    onShowChart: () -> Unit
) {
    var currentReading by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<ElectricityRecord?>(null) }
    var showExportDialog by remember { mutableStateOf(false) } // Добавляем состояние для экспорта
    var showImportDialog by remember { mutableStateOf(false) } // Добавляем состояние для импорта
    val tariff = 5.0
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Кнопка экспорта данных
            TextButton(
                onClick = {
                    showExportDialog = true // Показываем диалог экспорта
                },
                modifier = Modifier.padding(4.dp)
            ) {
                Text("📤 Экспорт")
            }

            // Кнопка теста уведомления
            TextButton(
                onClick = {
                    showTestNotification(context)
                },
                modifier = Modifier.padding(4.dp)
            ) {
                Text("🔔 Тест")
            }

            TextButton(
                onClick = onShowChart,
                modifier = Modifier.padding(4.dp)
            ) {
                Text("📊 График")
            }

            // Кнопка импорта данных
            TextButton(
                onClick = {
                    showImportDialog = true // Показываем диалог импорта
                },
                modifier = Modifier.padding(4.dp)
            ) {
                Text("📥 Импорт")
            }
        }

        // Диалог экспорта
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("Экспорт данных") },
                text = { Text("Экспортировать ${viewModel.records.size} записей в файл?") },
                confirmButton = {
                    Button(
                        onClick = {
                            exportDataToFile(context, viewModel.records)
                            showExportDialog = false
                        }
                    ) {
                        Text("Экспортировать")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showExportDialog = false }
                    ) {
                        Text("Отмена")
                    }
                }
            )
        }

        // Диалог импорта
        if (showImportDialog) {
            val file = remember { File(context.getExternalFilesDir(null), "electricity_backup.txt") }

            if (!file.exists()) {
                LaunchedEffect(Unit) {
                    Toast.makeText(context, "Файл backup не найден", Toast.LENGTH_SHORT).show()
                    showImportDialog = false
                }
            } else {
                AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    title = { Text("Импорт данных") },
                    text = { Text("Внимание! Это перезапишет текущие данные. Продолжить?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                importDataFromFile(context, viewModel)
                                showImportDialog = false
                            }
                        ) {
                            Text("Импортировать")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showImportDialog = false }
                        ) {
                            Text("Отмена")
                        }
                    }
                )
            }
        }

        // Статус напоминания
        ReminderStatus(viewModel)

        Text(
            text = "Счётчик электроэнергии",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Отображение текущего тарифа
        Text(
            text = "Текущий тариф: ${"%.0f".format(tariff)} руб./кВт*ч",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
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
                items(viewModel.records.sortedByDescending {
                    // Сортируем по дате (новые сверху)
                    try {
                        val dateParts = it.date.split(".").map { part -> part.toInt() }
                        // Создаем сортируемую дату в формате ГГГГММДД
                        val year = if (dateParts[2] < 100) 2000 + dateParts[2] else dateParts[2]
                        year * 10000 + dateParts[1] * 100 + dateParts[0]
                    } catch (e: Exception) {
                        0
                    }
                }) { record ->
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

// Функция для тестового уведомления
private fun showTestNotification(context: Context) {
    val notificationManager = NotificationManagerCompat.from(context)

    // Создаем канал если нужно
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "electricity_reminder_channel",
            "Напоминания об оплате",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Напоминания об оплате электроэнергии"
        }
        notificationManager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, "electricity_reminder_channel")
        .setSmallIcon(AndroidR.drawable.ic_dialog_info)
        .setContentTitle("🔔 динь-дилинь!")
        .setContentText("Оплата электроэнергии в гараже!")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(999, notification) // ID 999 для тестовых
}

// Функция экспорта данных с подтверждением
/*@Composable
fun ExportDataWithConfirmation(context: Context, records: List<ElectricityRecord>) {
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Экспорт данных") },
            text = { Text("Экспортировать ${records.size} записей в файл?") },
            confirmButton = {
                Button(
                    onClick = {
                        exportDataToFile(context, records)
                        showDialog = false
                    }
                ) {
                    Text("Экспортировать")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun ImportDataWithConfirmation(context: Context, viewModel: ElectricityViewModel) {
    var showDialog by remember { mutableStateOf(true) }
    val file = remember { File(context.getExternalFilesDir(null), "electricity_backup.txt") }

    if (showDialog) {
        if (!file.exists()) {
            // Use side effect to show toast
            LaunchedEffect(Unit) {
                Toast.makeText(context, "Файл backup не найден", Toast.LENGTH_SHORT).show()
                showDialog = false
            }
            return
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Импорт данных") },
            text = { Text("Внимание! Это перезапишет текущие данные. Продолжить?") },
            confirmButton = {
                Button(
                    onClick = {
                        importDataFromFile(context, viewModel)
                        showDialog = false
                    }
                ) {
                    Text("Импортировать")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}*/



// Парсер backup файла
private fun parseBackupFile(content: String): List<ElectricityRecord> {
    val records = mutableListOf<ElectricityRecord>()
    var previousReading = 0.0

    content.lines().forEach { line ->
        val cleanLine = line.trim()
        if (cleanLine.isNotEmpty() && !cleanLine.startsWith("#")) {
            try {
                val parts = cleanLine.split("-", "–").map { it.trim() }
                if (parts.size >= 2) {
                    val dateStr = parts[0]
                    val currentReading = parts[1].toDouble()
                    val consumption = if (previousReading > 0) currentReading - previousReading else 0.0

                    // Определяем тариф по дате
                    val tariff = if (isDateAfter(dateStr, "14.10.24")) 5.0 else 4.0
                    val cost = consumption * tariff

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
                // Пропускаем некорректные строки
                println("Ошибка парсинга строки: '$line'")
            }
        }
    }

    return records
}

// Функция экспорта данных в файл
private fun exportDataToFile(context: Context, records: List<ElectricityRecord>) {
    try {
        val content = buildString {
            appendln("# Формат: дата - показания")
            appendln("# Пример: 14.10.23 - 223")
            appendln()

            records.sortedBy { it.date }.forEach { record ->
                val datePart = record.date.split(" ")[0] // Берем только дату без времени
                appendln("$datePart - ${record.currentReading.toInt()}")
            }
        }

        // Сохраняем во внешнее хранилище
        val file = File(context.getExternalFilesDir(null), "electricity_backup.txt")
        file.writeText(content)

        // Показываем уведомление об успехе
        Toast.makeText(context, "Данные экспортированы в: ${file.absolutePath}", Toast.LENGTH_LONG).show()

    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка экспорта: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// Функция импорта данных из файла
private fun importDataFromFile(context: Context, viewModel: ElectricityViewModel) {
    try {
        val file = File(context.getExternalFilesDir(null), "electricity_backup.txt")
        if (!file.exists()) {
            Toast.makeText(context, "Файл backup.txt не найден", Toast.LENGTH_SHORT).show()
            return
        }

        val content = file.readText()
        val records = parseBackupFile(content)

        if (records.isNotEmpty()) {
            // Запускаем миграцию
            CoroutineScope(Dispatchers.Main).launch {
                viewModel.migrateOldData(records)
                Toast.makeText(context, "Импортировано ${records.size} записей", Toast.LENGTH_LONG).show()
            }
        }

    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка импорта: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// Функция показа текстовых данных для копирования
@Composable
private fun ShowTextExportDialog(context: Context, records: List<ElectricityRecord>) {
    // Note: You can't use AlertDialog.Builder in Compose, use the Compose AlertDialog instead
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Данные для копирования") },
            text = {
                val content = buildString {
                    appendln("# Backup данных электросчетчика")
                    appendln("# Формат: дата - показания")
                    appendln("# Пример: 14.10.23 - 223")
                    appendln()
                    records.sortedBy { it.date }.forEach { record ->
                        val datePart = record.date.split(" ")[0]
                        appendln("$datePart - ${record.currentReading.toInt()}")
                    }
                }
                Text(content)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val content = buildString {
                            appendln("# Backup данных электросчетчика")
                            appendln("# Формат: дата - показания")
                            appendln("# Пример: 14.10.23 - 223")
                            appendln()
                            records.sortedBy { it.date }.forEach { record ->
                                val datePart = record.date.split(" ")[0]
                                appendln("$datePart - ${record.currentReading.toInt()}")
                            }
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Electricity Backup", content)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Данные скопированы в буфер", Toast.LENGTH_SHORT).show()
                        showDialog = false
                    }
                ) {
                    Text("Копировать")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text("Закрыть")
                }
            }
        )
    }
}

// Компонент статуса напоминания
@Composable
fun ReminderStatus(viewModel: ElectricityViewModel) {
    val context = LocalContext.current
    val nextReminderDate = remember { viewModel.getNextReminderDate() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "📅 Следующее напоминание:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Маленькая кнопка теста
                IconButton(
                    onClick = {
                        showTestNotification(context)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painterResource(AndroidR.drawable.ic_menu_help),
                        contentDescription = "Тест уведомления",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = nextReminderDate,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun ChartDataItem(data: ChartData, maxConsumption: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "${data.month} ${data.year}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Визуализация потребления
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Потребление:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${"%.2f".format(data.consumption)} кВт·ч",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Визуализация стоимости
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Стоимость:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${"%.2f".format(data.cost)} руб.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Простая визуализация в виде полосы
            LinearProgressIndicator(
                progress = (data.consumption / maxConsumption).toFloat().coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )

            // Подпись под прогресс-баром
            Text(
                text = "Относительное потребление",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

private fun getMaxConsumption(chartData: List<ChartData>): Double {
    return chartData.maxOfOrNull { it.consumption } ?: 1.0
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumptionChartScreen(
    viewModel: ElectricityViewModel,
    onBack: () -> Unit
) {
    val records = viewModel.records
    val chartData = remember { prepareChartData(records) }
    val totalConsumption = remember { chartData.sumOf { it.consumption } }
    val totalCost = remember { chartData.sumOf { it.cost } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("График потребления") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (chartData.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Недостаточно данных для построения графика")
                }
            } else {
                // Общая статистика
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Общая статистика",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Всего потреблено: ${"%.2f".format(totalConsumption)} кВт·ч",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Text(
                            text = "Общая стоимость: ${"%.2f".format(totalCost)} руб.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // График
                Text(
                    text = "График потребления по месяцам:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                ConsumptionLineChart(chartData = chartData)

                Spacer(modifier = Modifier.height(16.dp)) // Добавьте отступ после графика

                // Список данных по месяцам (можно прокручивать)
                Text(
                    text = "Детализация по месяцам:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    items(chartData) { data ->
                        ChartDataItem(data = data, maxConsumption = getMaxConsumption(chartData))
                    }
                }
            }
        }
    }
}

@Composable
fun RecordCard(record: ElectricityRecord, onDelete: () -> Unit) {
    // Определяем тариф для этой записи
    val tariff = if (record.date == "15.10.24" || isDateAfter(record.date, "15.10.24")) {
        5.0
    } else {
        4.0
    }

    // Проверяем, является ли это первой записью (нулевое предыдущее показание)
    val isFirstRecord = record.previousReading == 0.0

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

            if (isFirstRecord) {
                Text(
                    text = "Начальные показания счетчика",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Text(
                    text = "Расход: ${"%.2f".format(record.consumption)} кВт*ч",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Тариф: ${"%.0f".format(tariff)} руб./кВт*ч",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Стоимость: ${"%.2f".format(record.cost)} руб.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Кнопка удаления (скрываем для первой записи)
            if (!isFirstRecord) {
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
}

// Добавим вспомогательную функцию для сравнения дат (вне composable)
// Замените существующую функцию на эту улучшенную версию
// Замените существующую функцию на эту
private fun isDateAfter(dateStr: String, compareDateStr: String): Boolean {
    try {
        // Берем только дату (без времени если есть)
        val cleanDateStr = dateStr.split(" ")[0]
        val cleanCompareDateStr = compareDateStr.split(" ")[0]

        val dateParts = cleanDateStr.split(".").map { it.toInt() }
        val compareParts = cleanCompareDateStr.split(".").map { it.toInt() }

        // Корректируем год (23 -> 2023)
        val dateYear = if (dateParts[2] < 100) 2000 + dateParts[2] else dateParts[2]
        val compareYear = if (compareParts[2] < 100) 2000 + compareParts[2] else compareParts[2]

        // Сравниваем даты
        return when {
            dateYear > compareYear -> true
            dateYear < compareYear -> false
            dateParts[1] > compareParts[1] -> true
            dateParts[1] < compareParts[1] -> false
            else -> dateParts[0] > compareParts[0]
        }
    } catch (e: Exception) {
        println("Ошибка сравнения дат: $dateStr и $compareDateStr - ${e.message}")
        return false
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
        14.10.23 - 223
15.11.23 - 917
16.12.23- 1875
15.01.24-2951
16.02.24-4028
14.03.24-4860
15.04.24-5674
15.05.24-6153
14.06.24-6403
16.07.24-6428
14.08.24-6444
16.09.24-6576
15.10.24-7027
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

    for (line in lines) {
        try {
            val cleanLine = line.replace("-", " ").replace("  ", " ").trim()
            val parts = cleanLine.split(" ").filter { it.isNotEmpty() }

            if (parts.size >= 2) {
                val dateStr = parts[0]
                val currentReading = parts[1].toDouble()
                val consumption = if (previousReading > 0) currentReading - previousReading else 0.0

                // Добавляем год для полноты даты
                val fullDateStr = if (dateStr.length == 8) dateStr else dateStr

                // Определяем тариф по дате
                val tariff = if (isDateAfter(dateStr, "14.10.24")) 5.0 else 4.0
                val cost = consumption * tariff

                records.add(
                    ElectricityRecord(
                        id = UUID.randomUUID().toString(),
                        date = fullDateStr,
                        previousReading = previousReading,
                        currentReading = currentReading,
                        consumption = consumption,
                        cost = cost
                    )
                )

                previousReading = currentReading
            }
        } catch (e: Exception) {
            println("Ошибка парсинга строки: '$line' - ${e.message}")
            continue
        }
    }

    return records
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var reminderEnabled by remember { mutableStateOf(true) }
    var reminderDay by remember { mutableStateOf(14) }
    var reminderTime by remember { mutableStateOf("12:00") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки напоминаний") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Switch(
                checked = reminderEnabled,
                onCheckedChange = { reminderEnabled = it },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Включить напоминания")

            Spacer(modifier = Modifier.height(16.dp))

            if (reminderEnabled) {
                Text("День месяца:")
                Slider(
                    value = reminderDay.toFloat(),
                    onValueChange = { reminderDay = it.toInt() },
                    valueRange = 1f..28f,
                    steps = 27
                )
                Text("$reminderDay число")

                Spacer(modifier = Modifier.height(16.dp))

                Text("Время напоминания:")
                // Здесь можно добавить выбор времени
            }
        }
    }
}