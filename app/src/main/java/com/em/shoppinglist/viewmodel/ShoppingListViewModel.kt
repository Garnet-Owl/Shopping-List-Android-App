// File: app/src/main/java/com/em/shoppinglist/viewmodel/ShoppingListViewModel.kt

package com.em.shoppinglist.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class ShoppingListViewModel : ViewModel() {
    private val _shoppingItems = MutableStateFlow<List<ShoppingItem>>(emptyList())
    val shoppingItems = _shoppingItems.asStateFlow()

    private val _savedLists = MutableStateFlow<List<ShoppingListInfo>>(emptyList())
    val savedLists = _savedLists.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.LAST_MODIFIED)
    val sortOrder = _sortOrder.asStateFlow()

    private var currentListId: String? = null
    private var autoSaveJob: Job? = null
    private lateinit var appContext: Context

    init {
        viewModelScope.launch {
            // Initial setup if needed
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        loadListsFromStorage(context)
    }

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (true) {
                delay(5000) // Auto-save every 5 seconds
                saveCurrentList()
            }
        }
    }

    private fun stopAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    private fun getDefaultListName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        return "Shopping_List_${dateFormat.format(Date())}"
    }

    private fun loadListsFromStorage(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val listsDir = File(context.getExternalFilesDir(null), "Shopping Lists")
                if (!listsDir.exists()) {
                    listsDir.mkdirs()
                    return@launch
                }

                val lists = listsDir.listFiles()?.mapNotNull { file ->
                    try {
                        when (file.extension.lowercase()) {
                            "txt" -> readTxtFile(file)
                            "csv" -> readCsvFile(file)
                            "xlsx" -> readExcelFile(file)
                            else -> null
                        }?.let { items ->
                            ShoppingListInfo(
                                name = file.nameWithoutExtension,
                                createdDate = file.lastModified(),
                                lastModified = file.lastModified(),
                                totalAmount = items.sumOf { it.price * it.quantity },
                                filePath = file.absolutePath
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    _savedLists.value = lists
                    sortLists()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadList(id: String) {
        viewModelScope.launch {
            val list = _savedLists.value.find { it.id == id }
            list?.let {
                currentListId = id
                val items = readListItems(File(it.filePath))
                _shoppingItems.value = items ?: emptyList()
                startAutoSave()
            }
        }
    }

    fun loadListFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val items = when {
                        uri.path?.endsWith(".txt", true) == true -> readTxtFromStream(inputStream)
                        uri.path?.endsWith(".csv", true) == true -> readCsvFromStream(inputStream)
                        uri.path?.endsWith(".xlsx", true) == true -> readExcelFromStream(inputStream)
                        else -> null
                    }
                    withContext(Dispatchers.Main) {
                        items?.let {
                            _shoppingItems.value = it
                            createNewList(uri.lastPathSegment ?: getDefaultListName())
                            startAutoSave()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createNewList(name: String = getDefaultListName()) {
        viewModelScope.launch {
            val listDir = File(appContext.getExternalFilesDir(null), "Shopping Lists")
            if (!listDir.exists()) listDir.mkdirs()

            val fileName = "${name}_${System.currentTimeMillis()}"
            val filePath = File(listDir, "$fileName.txt").absolutePath

            val newList = ShoppingListInfo(
                name = name,
                totalAmount = 0.0,
                filePath = filePath
            )

            _savedLists.value += newList
            _shoppingItems.value = emptyList()
            currentListId = newList.id
            startAutoSave()
            saveCurrentList()
        }
    }

    fun deleteList(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _savedLists.value.find { it.id == id }
            list?.let {
                try {
                    File(it.filePath).delete()
                    withContext(Dispatchers.Main) {
                        _savedLists.value = _savedLists.value.filter { it.id != id }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun addItem(name: String, quantity: Int, price: Double) {
        viewModelScope.launch {
            val newItem = ShoppingItem(
                id = (_shoppingItems.value.maxOfOrNull { it.id } ?: 0) + 1,
                name = name,
                quantity = quantity,
                price = price
            )
            _shoppingItems.value += newItem
            saveCurrentList()
        }
    }

    fun removeItem(id: Int) {
        viewModelScope.launch {
            _shoppingItems.value = _shoppingItems.value.filter { it.id != id }
            saveCurrentList()
        }
    }

    fun updateItem(item: ShoppingItem) {
        viewModelScope.launch {
            _shoppingItems.value = _shoppingItems.value.map {
                if (it.id == item.id) item else it
            }
            saveCurrentList()
        }
    }

    fun saveCurrentList() {
        viewModelScope.launch(Dispatchers.IO) {
            currentListId?.let { id ->
                val list = _savedLists.value.find { it.id == id }
                list?.let { it ->
                    try {
                        val file = File(it.filePath)
                        file.parentFile?.mkdirs()

                        file.bufferedWriter().use { writer ->
                            writer.write("Shopping List\n")
                            writer.write("-----------------------------\n")
                            writer.write("Item: Price: Quantity\n")
                            writer.write("-----------------------------\n")
                            _shoppingItems.value.forEach { item ->
                                writer.write("${item.name}: ${item.price}: ${item.quantity}\n")
                            }
                            writer.write("-----------------------------\n")
                            val total = _shoppingItems.value.sumOf { it.price * it.quantity }
                            writer.write("Total: Kshs. $total\n")
                            writer.write("\nLast edited: ${formatDate(System.currentTimeMillis())}\n")
                        }

                        val updatedList = list.copy(
                            lastModified = System.currentTimeMillis(),
                            totalAmount = _shoppingItems.value.sumOf { it.price * it.quantity }
                        )

                        withContext(Dispatchers.Main) {
                            _savedLists.value = _savedLists.value.map {
                                if (it.id == id) updatedList else it
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun exportList(context: Context, format: ExportFormat): File? {
        val list = currentListId?.let { id -> _savedLists.value.find { it.id == id } }
        val items = _shoppingItems.value
        if (list == null || items.isEmpty()) return null

        val downloadsDir = File(context.getExternalFilesDir(null), "Downloads")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date())

        return try {
            when (format) {
                ExportFormat.TXT -> exportToTxt(downloadsDir, list, items, timestamp)
                ExportFormat.CSV -> exportToCsv(downloadsDir, list, items, timestamp)
                ExportFormat.EXCEL -> exportToExcel(downloadsDir, list, items, timestamp)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun exportToTxt(dir: File, list: ShoppingListInfo, items: List<ShoppingItem>, timestamp: String): File {
        val file = File(dir, "${list.name}_$timestamp.txt")
        file.bufferedWriter().use { writer ->
            writer.write("Shopping List: ${list.name}\n")
            writer.write("Created: ${formatDate(list.createdDate)}\n")
            writer.write("Exported: ${formatDate(System.currentTimeMillis())}\n")
            writer.write("-----------------------------\n")
            writer.write("Item\tPrice\tQuantity\tTotal\n")
            writer.write("-----------------------------\n")
            items.forEach { item ->
                writer.write("${item.name}\t${item.price}\t${item.quantity}\t${item.price * item.quantity}\n")
            }
            writer.write("-----------------------------\n")
            writer.write("Total: ${items.sumOf { it.price * it.quantity }}")
        }
        return file
    }

    private fun exportToCsv(dir: File, list: ShoppingListInfo, items: List<ShoppingItem>, timestamp: String): File {
        val file = File(dir, "${list.name}_$timestamp.csv")
        CSVWriter(file.bufferedWriter()).use { writer ->
            writer.writeNext(arrayOf("Shopping List: ${list.name}"))
            writer.writeNext(arrayOf("Created", formatDate(list.createdDate)))
            writer.writeNext(arrayOf("Exported", formatDate(System.currentTimeMillis())))
            writer.writeNext(arrayOf("Item", "Price", "Quantity", "Total"))
            items.forEach { item ->
                writer.writeNext(arrayOf(
                    item.name,
                    item.price.toString(),
                    item.quantity.toString(),
                    (item.price * item.quantity).toString()
                ))
            }
            writer.writeNext(arrayOf("Total", "", "", items.sumOf { it.price * it.quantity }.toString()))
        }
        return file
    }

    private fun exportToExcel(dir: File, list: ShoppingListInfo, items: List<ShoppingItem>, timestamp: String): File {
        val file = File(dir, "${list.name}_$timestamp.xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Shopping List")

            var rowNum = 0
            sheet.createRow(rowNum++).apply {
                createCell(0).setCellValue("Shopping List: ${list.name}")
            }
            sheet.createRow(rowNum++).apply {
                createCell(0).setCellValue("Created")
                createCell(1).setCellValue(formatDate(list.createdDate))
            }
            sheet.createRow(rowNum++).apply {
                createCell(0).setCellValue("Exported")
                createCell(1).setCellValue(formatDate(System.currentTimeMillis()))
            }

            sheet.createRow(rowNum++).apply {
                createCell(0).setCellValue("Item")
                createCell(1).setCellValue("Price")
                createCell(2).setCellValue("Quantity")
                createCell(3).setCellValue("Total")
            }

            items.forEach { item ->
                sheet.createRow(rowNum++).apply {
                    createCell(0).setCellValue(item.name)
                    createCell(1).setCellValue(item.price)
                    createCell(2).setCellValue(item.quantity.toDouble())
                    createCell(3).setCellValue(item.price * item.quantity)
                }
            }

            sheet.createRow(rowNum).apply {
                createCell(0).setCellValue("Total")
                createCell(3).setCellValue(items.sumOf { it.price * it.quantity })
            }

            FileOutputStream(file).use { fos ->
                workbook.write(fos)
            }
        }
        return file
    }

    private fun readListItems(file: File): List<ShoppingItem>? {
        return when (file.extension.lowercase()) {
            "txt" -> readTxtFile(file)
            "csv" -> readCsvFile(file)
            "xlsx" -> readExcelFile(file)
            else -> null
        }
    }

    private fun readTxtFile(file: File): List<ShoppingItem>? {
        return try {
            BufferedReader(FileReader(file)).use { reader ->
                val lines = reader.readLines()
                // Try to determine the format
                if (lines.any { it.contains("Shopping List") && it.contains("-----------------------------") }) {
                    // Python script format
                    readPythonStyleFormat(lines)
                } else {
                    // Standard format
                    readStandardFormat(lines)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readPythonStyleFormat(lines: List<String>): List<ShoppingItem> {
        val items = mutableListOf<ShoppingItem>()
        var readingItems = false
        var currentId = 0

        lines.forEach { line ->
            when {
                line.contains("-----------------------------") -> {
                    readingItems = !readingItems
                }
                readingItems && line.contains(":") -> {
                    val parts = line.split(":")
                    if (parts.size >= 3) {
                        val item = ShoppingItem(
                            id = currentId++,
                            name = parts[0].trim(),
                            price = parts[1].trim().toDoubleOrNull() ?: 0.0,
                            quantity = parts[2].trim().toIntOrNull() ?: 0
                        )
                        items.add(item)
                    }
                }
            }
        }
        return items
    }

    private fun readStandardFormat(lines: List<String>): List<ShoppingItem> {
        return lines.filter { it.contains(":") }
            .mapIndexed { index, line ->
                val parts = line.split(":")
                ShoppingItem(
                    id = index,
                    name = parts[0].trim(),
                    price = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: 0.0,
                    quantity = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                )
            }
    }

    private fun readCsvFile(file: File): List<ShoppingItem>? {
        return try {
            CSVReader(FileReader(file)).use { reader ->
                reader.skip(1) // Skip header
                reader.readAll()
                    .mapIndexed { index, line ->
                        ShoppingItem(
                            id = index,
                            name = line.getOrNull(0) ?: "",
                            price = line.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
                            quantity = line.getOrNull(2)?.toIntOrNull() ?: 0
                        )
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readExcelFile(file: File): List<ShoppingItem>? {
        return try {
            WorkbookFactory.create(file).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                sheet.drop(1) // Skip header
                    .mapIndexed { index, row ->
                        ShoppingItem(
                            id = index,
                            name = row.getCell(0)?.stringCellValue ?: "",
                            price = row.getCell(1)?.numericCellValue ?: 0.0,
                            quantity = row.getCell(2)?.numericCellValue?.toInt() ?: 0
                        )
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readTxtFromStream(inputStream: InputStream): List<ShoppingItem>? {
        return try {
            val lines = inputStream.bufferedReader().readLines()
            if (lines.any { it.contains("Shopping List") && it.contains("-----------------------------") }) {
                readPythonStyleFormat(lines)
            } else {
                readStandardFormat(lines)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readCsvFromStream(inputStream: InputStream): List<ShoppingItem>? {
        return try {
            CSVReader(InputStreamReader(inputStream)).use { reader ->
                reader.skip(1) // Skip header
                reader.readAll()
                    .mapIndexed { index, line ->
                        ShoppingItem(
                            id = index,
                            name = line.getOrNull(0) ?: "",
                            price = line.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
                            quantity = line.getOrNull(2)?.toIntOrNull() ?: 0
                        )
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readExcelFromStream(inputStream: InputStream): List<ShoppingItem>? {
        return try {
            WorkbookFactory.create(inputStream).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                sheet.drop(1) // Skip header
                    .mapIndexed { index, row ->
                        ShoppingItem(
                            id = index,
                            name = row.getCell(0)?.stringCellValue ?: "",
                            price = row.getCell(1)?.numericCellValue ?: 0.0,
                            quantity = row.getCell(2)?.numericCellValue?.toInt() ?: 0
                        )
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(timestamp))
    }

    fun changeSortOrder(order: SortOrder) {
        viewModelScope.launch {
            _sortOrder.value = order
            sortLists()
        }
    }

    private fun sortLists() {
        val currentLists = _savedLists.value
        val newLists = when (_sortOrder.value) {
            SortOrder.LAST_MODIFIED -> currentLists.sortedByDescending { it.lastModified }
            SortOrder.CREATED_DATE -> currentLists.sortedByDescending { it.createdDate }
            SortOrder.TOTAL_AMOUNT -> currentLists.sortedByDescending { it.totalAmount }
        }
        _savedLists.value = newLists
    }

    enum class SortOrder {
        LAST_MODIFIED,
        CREATED_DATE,
        TOTAL_AMOUNT
    }

    enum class ExportFormat {
        TXT,
        CSV,
        EXCEL
    }

    data class ShoppingItem(
        val id: Int,
        val name: String,
        val quantity: Int,
        val price: Double
    )

    data class ShoppingListInfo(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val createdDate: Long = System.currentTimeMillis(),
        val lastModified: Long = System.currentTimeMillis(),
        val totalAmount: Double,
        val filePath: String
    )

    override fun onCleared() {
        super.onCleared()
        stopAutoSave()
    }
}