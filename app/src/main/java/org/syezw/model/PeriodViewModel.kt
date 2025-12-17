package org.syezw.model

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.syezw.data.Converters
import org.syezw.data.PeriodDao
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Entity(tableName = "period_records")
@TypeConverters(Converters::class)
data class PeriodRecord(
    @PrimaryKey
    val startDate: LocalDate,
    val endDate: LocalDate,
    val notes: String? = null
) {
    val realDuration: Long
        get() = ChronoUnit.DAYS.between(startDate, endDate) + 1
}

data class OvulationPrediction(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val peakDate: LocalDate
)

class PeriodViewModel(private val periodDao: PeriodDao) : ViewModel() {

    val periodRecords: StateFlow<List<PeriodRecord>> = periodDao.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _avgCycleLast3 = MutableStateFlow(0)
    val avgCycleLast3: StateFlow<Int> = _avgCycleLast3.asStateFlow()

    private val _avgCycleLast5 = MutableStateFlow(0)
    val avgCycleLast5: StateFlow<Int> = _avgCycleLast5.asStateFlow()

    private val _daysSinceLastPeriod = MutableStateFlow<Long?>(null)
    val daysSinceLastPeriod: StateFlow<Long?> = _daysSinceLastPeriod.asStateFlow()

    private val _lastCycleDuration = MutableStateFlow<Long?>(null)
    val lastCycleDuration: StateFlow<Long?> = _lastCycleDuration.asStateFlow()

    private val _ovulationPrediction = MutableStateFlow<OvulationPrediction?>(null)
    val ovulationPrediction: StateFlow<OvulationPrediction?> = _ovulationPrediction.asStateFlow()

    private val _predictedNextPeriodDate = MutableStateFlow<LocalDate?>(null)
    val predictedNextPeriodDate: StateFlow<LocalDate?> = _predictedNextPeriodDate.asStateFlow()

    private val _lastPeriodDuration = MutableStateFlow<Long?>(null)
    val lastPeriodDuration: StateFlow<Long?> = _lastPeriodDuration.asStateFlow()

    private val _avgPeriodDurationLast3 = MutableStateFlow(0)
    val avgPeriodDurationLast3: StateFlow<Int> = _avgPeriodDurationLast3.asStateFlow()

    private val defaultDurationDays: Long = 7

    init {
        viewModelScope.launch {
            periodRecords.collect { records ->
                calculateStats(records)
            }
        }
    }

    fun addPeriodStartDate(date: LocalDate, notes: String? = null) {
        viewModelScope.launch {
            val defaultEndDate = date.plusDays(defaultDurationDays - 1)
            if (periodRecords.value.any {
                    (date.isAfter(it.startDate) && date.isBefore(it.endDate)) ||
                            (defaultEndDate.isAfter(it.startDate) && defaultEndDate.isBefore(it.endDate)) ||
                            (date.isBefore(it.startDate) && defaultEndDate.isAfter(it.endDate))
                }) {
                return@launch
            }
            periodDao.upsert(
                PeriodRecord(
                    startDate = date,
                    endDate = defaultEndDate,
                    notes = notes
                )
            )
        }
    }

    fun updatePeriodEndDate(record: PeriodRecord, newEndDate: LocalDate) {
        viewModelScope.launch {
            if (newEndDate.isBefore(record.startDate)) return@launch

            val nextRecord = periodRecords.value
                .filter { it.startDate.isAfter(record.startDate) }
                .minByOrNull { it.startDate }

            if (nextRecord != null && newEndDate.isAfter(nextRecord.startDate.minusDays(1))) {
                return@launch
            }
            periodDao.upsert(record.copy(endDate = newEndDate))
        }
    }

    fun updateRecordNotes(record: PeriodRecord, newNotes: String) {
        viewModelScope.launch {
            periodDao.upsert(record.copy(notes = newNotes))
        }
    }

    fun deleteRecord(record: PeriodRecord) {
        viewModelScope.launch {
            periodDao.delete(record)
        }
    }

    fun exportData(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 从数据库获取当前所有记录
                val recordsToExport = periodRecords.first() // .first() gets the current value from the Flow
                // 2. 使用 Gson 将列表转换为 JSON 字符串
                val gson = Gson()
                val jsonString = gson.toJson(recordsToExport)

                // 3. 将 JSON 字符串写入用户选择的文件
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
            } catch (_: Exception) {
                // Handle exceptions, e.g., show a toast
            }
        }
    }

    fun importData(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 从用户选择的文件中读取 JSON 字符串
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.readText()
                    }
                } ?: return@launch

                // 2. 使用 Gson 将 JSON 字符串解析回 PeriodRecord 列表
                val gson = Gson()
                val type = object : TypeToken<List<PeriodRecord>>() {}.type
                val importedRecords: List<PeriodRecord> = gson.fromJson(jsonString, type)

                // 3. 清空现有数据，并插入新数据
                if (importedRecords.isNotEmpty()) {
                    periodDao.clearAndInsert(importedRecords)
                }

            } catch (_: Exception) {
                // Handle exceptions, e.g., show a toast for invalid file format
            }
        }
    }

    private fun calculateStats(records: List<PeriodRecord>) {
        val ascendingRecords = records.sortedBy { it.startDate }

        _lastCycleDuration.value = if (ascendingRecords.size >= 2) {
            ChronoUnit.DAYS.between(
                ascendingRecords[ascendingRecords.size - 2].startDate,
                ascendingRecords.last().startDate
            )
        } else {
            null
        }

        val cycleLengths = if (ascendingRecords.size >= 2) {
            ascendingRecords.zipWithNext { a, b ->
                ChronoUnit.DAYS.between(a.startDate, b.startDate)
            }
        } else {
            emptyList()
        }
        _avgCycleLast3.value = calculateAverageOfLongs(cycleLengths, 2)
        _avgCycleLast5.value = calculateAverageOfLongs(cycleLengths, 4)

        val durationLengths = ascendingRecords.map { it.realDuration }
        _lastPeriodDuration.value = durationLengths.lastOrNull()
        _avgPeriodDurationLast3.value = calculateAverageOfLongs(durationLengths, 3)

        val latestRecord = ascendingRecords.lastOrNull()
        if (latestRecord != null) {
            _daysSinceLastPeriod.value =
                ChronoUnit.DAYS.between(latestRecord.startDate, LocalDate.now())
            predictOvulation(latestRecord.startDate)
        } else {
            _daysSinceLastPeriod.value = null
            _ovulationPrediction.value = null
            _predictedNextPeriodDate.value = null
            _lastPeriodDuration.value = null
            _avgPeriodDurationLast3.value = 0
            _lastCycleDuration.value = null
            _avgCycleLast3.value = 0
            _avgCycleLast5.value = 0
        }
    }

    private fun predictOvulation(lastPeriodDate: LocalDate) {
        val cycleToUse = when {
            _avgCycleLast3.value > 0 -> _avgCycleLast3.value
            _avgCycleLast5.value > 0 -> _avgCycleLast5.value
            else -> 0
        }

        if (cycleToUse > 0) {
            val nextPeriodDate = lastPeriodDate.plusDays(cycleToUse.toLong())
            _predictedNextPeriodDate.value = nextPeriodDate
            val peakDate = nextPeriodDate.minusDays(14)
            _ovulationPrediction.value =
                OvulationPrediction(peakDate.minusDays(3), peakDate.plusDays(1), peakDate)
        } else {
            _ovulationPrediction.value = null
            _predictedNextPeriodDate.value = null
        }
    }

    private fun calculateAverageOfLongs(lengths: List<Long>, takeLast: Int): Int {
        if (lengths.isEmpty() || takeLast <= 0) return 0
        val sublist = if (lengths.size < takeLast) lengths else lengths.takeLast(takeLast)
        return sublist.average().toInt()
    }
}

class PeriodViewModelFactory(private val periodDao: PeriodDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PeriodViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PeriodViewModel(periodDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class for Period")
    }
}
