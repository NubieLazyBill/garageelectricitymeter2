package com.example.garageelectricitymeter2

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "electricity_data")

class DataStoreManager(private val context: Context) {
    private val dataStore: DataStore<Preferences> = context.dataStore

    // Ключи для хранения данных
    private object PreferencesKeys {
        val PREVIOUS_READING = doublePreferencesKey("previous_reading")
        val RECORDS_COUNT = intPreferencesKey("records_count")
    }

    // Сохраняем предыдущие показания
    suspend fun savePreviousReading(reading: Double) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PREVIOUS_READING] = reading
        }
    }

    // Получаем предыдущие показания
    val previousReading: Flow<Double> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.PREVIOUS_READING] ?: 0.0
        }

    // Сохраняем отдельную запись
    suspend fun saveRecord(record: ElectricityRecord, index: Int) {
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey("record_${index}_id")] = record.id
            preferences[stringPreferencesKey("record_${index}_date")] = record.date
            preferences[doublePreferencesKey("record_${index}_prev")] = record.previousReading
            preferences[doublePreferencesKey("record_${index}_curr")] = record.currentReading
            preferences[doublePreferencesKey("record_${index}_cons")] = record.consumption
            preferences[doublePreferencesKey("record_${index}_cost")] = record.cost
        }
    }

    // Получаем все записи
    suspend fun getRecords(): List<ElectricityRecord> {
        val preferences = context.dataStore.data.first()
        val count = preferences[PreferencesKeys.RECORDS_COUNT] ?: 0
        val records = mutableListOf<ElectricityRecord>()

        for (i in 0 until count) {
            val id = preferences[stringPreferencesKey("record_${i}_id")] ?: continue
            val date = preferences[stringPreferencesKey("record_${i}_date")] ?: continue
            val prev = preferences[doublePreferencesKey("record_${i}_prev")] ?: continue
            val curr = preferences[doublePreferencesKey("record_${i}_curr")] ?: continue
            val cons = preferences[doublePreferencesKey("record_${i}_cons")] ?: continue
            val cost = preferences[doublePreferencesKey("record_${i}_cost")] ?: continue

            records.add(ElectricityRecord(id, date, prev, curr, cons, cost))
        }

        return records
    }

    // Сохраняем количество записей
    suspend fun saveRecordsCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECORDS_COUNT] = count
        }
    }

    // Удаляем запись
    suspend fun removeRecord(index: Int) {
        context.dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey("record_${index}_id"))
            preferences.remove(stringPreferencesKey("record_${index}_date"))
            preferences.remove(doublePreferencesKey("record_${index}_prev"))
            preferences.remove(doublePreferencesKey("record_${index}_curr"))
            preferences.remove(doublePreferencesKey("record_${index}_cons"))
            preferences.remove(doublePreferencesKey("record_${index}_cost"))
        }
    }
}