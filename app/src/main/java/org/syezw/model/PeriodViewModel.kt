package org.syezw.model

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.syezw.data.Converters
import org.syezw.data.PeriodDao
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Entity(tableName = "period_records")
@TypeConverters(Converters::class)
data class PeriodRecord(
    @PrimaryKey val startDate: LocalDate,
    val endDate: LocalDate,
    val notes: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val realDuration: Long
        get() = ChronoUnit.DAYS.between(startDate, endDate) + 1

    @Ignore
    @kotlin.jvm.Transient // Gson 导出时将忽略该字段
    var daysSinceLast: Long? = null
}

data class OvulationPrediction(
    val startDate: LocalDate, val endDate: LocalDate, val peakDate: LocalDate
)

class PeriodViewModel(private val periodDao: PeriodDao) : ViewModel() {
    private val gson =
        GsonBuilder().registerTypeAdapter(LocalDate::class.java, object : TypeAdapter<LocalDate>() {
            override fun write(out: JsonWriter, value: LocalDate?) {
                if (value == null) {
                    out.nullValue()
                } else {
                    out.value(value.toString()) // 序列化为 "yyyy-MM-dd" 格式
                }
            }

            override fun read(input: JsonReader): LocalDate? {
                if (input.peek() == com.google.gson.stream.JsonToken.NULL) {
                    input.nextNull()
                    return null
                }
                return LocalDate.parse(input.nextString()) // 反序列化从 "yyyy-MM-dd" 格式
            }
        }).create()

    val periodRecords: StateFlow<List<PeriodRecord>> = periodDao.getAllRecords().map { records ->
        // records are ordered by startDate DESC (newest first)
        records.mapIndexed { index, record ->
            if (index < records.size - 1) {
                val prev = records[index + 1]
                record.apply {
                    daysSinceLast = ChronoUnit.DAYS.between(prev.startDate, startDate)
                }
            } else {
                record
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                    (date.isAfter(it.startDate) && date.isBefore(it.endDate)) || (defaultEndDate.isAfter(
                        it.startDate
                    ) && defaultEndDate.isBefore(it.endDate)) || (date.isBefore(it.startDate) && defaultEndDate.isAfter(
                        it.endDate
                    ))
                }) {
                return@launch
            }
            periodDao.upsert(
                PeriodRecord(
                    startDate = date,
                    endDate = defaultEndDate,
                    notes = notes,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun updatePeriodEndDate(record: PeriodRecord, newEndDate: LocalDate) {
        viewModelScope.launch {
            if (newEndDate.isBefore(record.startDate)) return@launch

            val nextRecord = periodRecords.value.filter { it.startDate.isAfter(record.startDate) }
                .minByOrNull { it.startDate }

            if (nextRecord != null && newEndDate.isAfter(nextRecord.startDate.minusDays(1))) {
                return@launch
            }
            periodDao.upsert(record.copy(endDate = newEndDate, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateRecordNotes(record: PeriodRecord, newNotes: String) {
        viewModelScope.launch {
            periodDao.upsert(record.copy(notes = newNotes, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteRecord(record: PeriodRecord) {
        viewModelScope.launch {
            periodDao.delete(record)
        }
    }

    fun exportData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val recordsToExport = periodDao.getAllRecords().first()
                if (recordsToExport.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "没有记录可导出", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val jsonString = gson.toJson(recordsToExport)

                withContext(Dispatchers.IO) {
                    context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).use { fos ->
                            fos.write(jsonString.toByteArray())
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "导出成功!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun importData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonString = StringBuilder()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                jsonString.append(line)
                            }
                        }
                    }
                }

                if (jsonString.isBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "导入文件为空", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val type: Type = object : TypeToken<List<PeriodRecord>>() {}.type
                val importedRecords: List<PeriodRecord> = gson.fromJson(jsonString.toString(), type)

                if (importedRecords.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "文件中未找到记录", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Merge logic: upsertAll will update existing records with same startDate and insert new ones
                periodDao.upsertAll(importedRecords)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, "成功导入 ${importedRecords.size} 条记录!", Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun calculateStats(records: List<PeriodRecord>) {
        val latestRecord = records.firstOrNull()

        if (latestRecord != null) {
            _lastCycleDuration.value = latestRecord.daysSinceLast
            _daysSinceLastPeriod.value =
                ChronoUnit.DAYS.between(latestRecord.startDate, LocalDate.now())
            _lastPeriodDuration.value = latestRecord.realDuration
        } else {
            _lastCycleDuration.value = null
            _daysSinceLastPeriod.value = null
            _lastPeriodDuration.value = null
            _ovulationPrediction.value = null
            _predictedNextPeriodDate.value = null
            _avgCycleLast3.value = 0
            _avgCycleLast5.value = 0
            _avgPeriodDurationLast3.value = 0
            return
        }

        val cycleLengths = records.mapNotNull { it.daysSinceLast }
        _avgCycleLast3.value = calculateAverage(cycleLengths, 2)
        _avgCycleLast5.value = calculateAverage(cycleLengths, 4)

        val durationLengths = records.map { it.realDuration }
        _avgPeriodDurationLast3.value = calculateAverage(durationLengths, 3)

        // 在计算完平均值后再预测排卵
        predictOvulation(latestRecord.startDate)
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

    private fun calculateAverage(values: List<Long>, count: Int): Int {
        if (values.isEmpty() || count <= 0) return 0
        val sublist = values.take(count)
        return sublist.average().toInt()
    }
}

class PeriodViewModelFactory(private val periodDao: PeriodDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PeriodViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return PeriodViewModel(periodDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class for Period")
    }
}
