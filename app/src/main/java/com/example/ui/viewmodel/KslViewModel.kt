package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.JobEntity
import com.example.data.database.ServiceEntity
import com.example.data.repository.KslRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

sealed interface Screen {
    object Login : Screen
    object Dashboard : Screen
}

enum class DashboardTab {
    COTIZAR, HISTORIAL, AJUSTES
}

data class QuoteResult(
    val clientName: String,
    val description: String,
    val matchedServices: List<ServiceEntity>,
    val total: Double,
    val isSaved: Boolean = false,
    val savedJobId: Int? = null
)

class KslViewModel(
    private val repository: KslRepository,
    context: Context
) : ViewModel() {

    private val sharedPrefs = context.getSharedPreferences("ksl_prefs", Context.MODE_PRIVATE)

    // UI States
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Login)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _currentTab = MutableStateFlow(DashboardTab.COTIZAR)
    val currentTab: StateFlow<DashboardTab> = _currentTab.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // Forms
    val clientNameInput = MutableStateFlow("")
    val descriptionInput = MutableStateFlow("")

    // Active Quote
    private val _activeQuote = MutableStateFlow<QuoteResult?>(null)
    val activeQuote: StateFlow<QuoteResult?> = _activeQuote.asStateFlow()

    // Spelling Autocorrect Suggestion
    private val _spellingSuggestion = MutableStateFlow<String?>(null)
    val spellingSuggestion: StateFlow<String?> = _spellingSuggestion.asStateFlow()

    // Sync notification event bus
    private val _syncNotification = MutableSharedFlow<String>(replay = 0)
    val syncNotification: SharedFlow<String> = _syncNotification.asSharedFlow()

    // DB flows
    val services: StateFlow<List<ServiceEntity>> = repository.allServices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val jobsHistory: StateFlow<List<JobEntity>> = repository.allJobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // Seed initial data if first time running
            repository.seedInitialServicesIfEmpty()
            
            // Check session persistence
            val isAuth = sharedPrefs.getBoolean("ksl_auth_sahul_gay", false)
            if (isAuth) {
                _currentScreen.value = Screen.Dashboard
            }
        }

        // Background Cloud Sync Loop (Polls every 10 seconds)
        viewModelScope.launch {
            while (isActive) {
                repository.syncWithCloud(onNewJobDetected = { newJob ->
                    // Emit synchronization event to notify other mechanics
                    viewModelScope.launch {
                        _syncNotification.emit("🔔 ¡TRABAJO REGISTRADO POR OTRO MECÁNICO!\nCliente: ${newJob.clientName}\nDetalles: ${newJob.description.take(45)}...\nTotal: $${newJob.totalPrice}")
                    }
                })
                delay(10000)
            }
        }
    }

    fun login(password: String) {
        if (password == "Sahul es Gey") {
            sharedPrefs.edit().putBoolean("ksl_auth_sahul_gay", true).apply()
            _loginError.value = null
            _currentScreen.value = Screen.Dashboard
        } else {
            _loginError.value = "❌ Contraseña incorrecta"
        }
    }

    fun logout() {
        // Technically they shouldn't need to login again because it is "single-use password" once installed.
        // But we keep this button in settings as an administrator reset to allow clean testing or locking the app.
        sharedPrefs.edit().putBoolean("ksl_auth_sahul_gay", false).apply()
        _currentScreen.value = Screen.Login
        // Clear active states
        clientNameInput.value = ""
        descriptionInput.value = ""
        _activeQuote.value = null
        _spellingSuggestion.value = null
    }

    fun selectTab(tab: DashboardTab) {
        _currentTab.value = tab
    }

    // Apply the smart spelling suggestion recommendation
    fun applySpellingSuggestion(suggestion: String) {
        descriptionInput.value = suggestion
        _spellingSuggestion.value = null
        calculateAndSaveQuote()
    }

    // Smart cotización algorithm
    fun calculateAndSaveQuote() {
        val desc = descriptionInput.value.trim()
        val client = clientNameInput.value.trim().ifEmpty { "Cliente sin registrar" }
        if (desc.isEmpty()) return

        viewModelScope.launch {
            val allCatalog = services.value
            
            // Detect if they typed a slightly misspelled word and find the best correction
            val suggestion = getSpellingCorrection(desc, allCatalog)
            _spellingSuggestion.value = suggestion

            val matched = getMatchingServices(desc, allCatalog)
            val total = matched.sumOf { it.price }

            _activeQuote.value = QuoteResult(
                clientName = client,
                description = desc,
                matchedServices = matched,
                total = total,
                isSaved = false,
                savedJobId = null
            )
        }
    }

    // Save job permanently in local database and trigger live cloud synchronization
    fun registerJobAndNotify() {
        val quote = _activeQuote.value ?: return
        viewModelScope.launch {
            val newJob = JobEntity(
                clientName = quote.clientName,
                description = quote.description,
                totalPrice = quote.total,
                status = "Registrado",
                paymentMethod = "",
                referenceCode = "",
                isSync = false // Sincronización real con Firebase RTDB
            )
            val insertedId = repository.insertJob(newJob).toInt()
            
            _activeQuote.value = quote.copy(isSaved = true, savedJobId = insertedId)
            
            // Trigger an immediate cloud sync to upload this job instantly
            launch {
                repository.syncWithCloud(onNewJobDetected = {})
            }
            
            // Emit a synchronization notification event to demonstrate local sync completed
            _syncNotification.emit("🔔 ¡TRABAJO REGISTRADO! Sincronizado en la nube de KSL.\nCliente: ${quote.clientName}\nDetalles: ${quote.description.take(45)}...\nTotal: $${quote.total}")
        }
    }

    fun clearQuote() {
        _activeQuote.value = null
        _spellingSuggestion.value = null
        clientNameInput.value = ""
        descriptionInput.value = ""
    }

    // Levenshtein-based spelling correction recommendator ("Did you mean...?")
    fun getSpellingCorrection(input: String, catalog: List<ServiceEntity>): String? {
        val cleanInput = input.trim()
        if (cleanInput.isEmpty()) return null

        val words = cleanInput.split("\\s+".toRegex())
        var modified = false
        val correctedWords = words.map { word ->
            val cleanWord = word.lowercase().replace("[,.!?]".toRegex(), "")
            if (cleanWord.length <= 3) return@map word

            // Check if this word exists in any catalog item name
            val exists = catalog.any { item ->
                item.name.lowercase().contains(cleanWord)
            }
            if (exists) return@map word

            // Find best matching word from catalog
            var bestMatch: String? = null
            var bestDistance = Int.MAX_VALUE

            for (item in catalog) {
                val itemWords = item.name.lowercase().split("\\s+".toRegex())
                    .map { it.replace("[,.!?]".toRegex(), "") }
                    .filter { it.length > 3 }
                for (catalogWord in itemWords) {
                    val dist = levenshteinDistance(cleanWord, catalogWord)
                    if (dist <= 2 && dist < bestDistance) {
                        bestDistance = dist
                        bestMatch = catalogWord
                    }
                }
            }

            if (bestMatch != null && bestDistance <= 2) {
                modified = true
                if (word.first().isUpperCase()) {
                    bestMatch.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                } else {
                    bestMatch
                }
            } else {
                word
            }
        }

        return if (modified) correctedWords.joinToString(" ") else null
    }

    // Levenshtein-based fuzzy match for item catalog search
    private fun getMatchingServices(text: String, allServices: List<ServiceEntity>): List<ServiceEntity> {
        val cleanText = text.lowercase()
        val inputWords = cleanText.split("\\s+".toRegex()).filter { it.length > 2 }
        if (inputWords.isEmpty() && cleanText.length <= 2) return emptyList()

        val matched = mutableListOf<ServiceEntity>()
        allServices.forEach { service ->
            val serviceNameLower = service.name.lowercase()
            var isCoincidence = false

            if (serviceNameLower.contains(cleanText)) {
                isCoincidence = true
            } else {
                val serviceWords = serviceNameLower.split("\\s+".toRegex()).filter { it.length > 2 }
                if (serviceWords.isNotEmpty()) {
                    var matchesCount = 0
                    serviceWords.forEach { sWord ->
                        val hasFuzzyMatch = inputWords.any { iWord ->
                            iWord.contains(sWord) || sWord.contains(iWord) || levenshteinDistance(iWord, sWord) <= 2
                        }
                        if (hasFuzzyMatch) {
                            matchesCount++
                        }
                    }
                    if (matchesCount >= serviceWords.size * 0.6) {
                        isCoincidence = true
                    }
                }
            }
            if (isCoincidence) {
                matched.add(service)
            }
        }
        return matched
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                if (s1[i - 1] == s2[j - 1]) {
                    dp[j] = prev
                } else {
                    dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + 1)
                }
                prev = temp
            }
        }
        return dp[s2.length]
    }

    // --- Adjustments / Services & Products Admin ---
    fun addCatalogItem(name: String, price: Double, isProduct: Boolean) {
        viewModelScope.launch {
            repository.insertService(ServiceEntity(name = name, price = price, isProduct = isProduct))
        }
    }

    fun updateCatalogItem(item: ServiceEntity, newName: String, newPrice: Double, newIsProduct: Boolean) {
        viewModelScope.launch {
            repository.updateService(item.copy(name = newName, price = newPrice, isProduct = newIsProduct))
        }
    }

    fun deleteCatalogItem(item: ServiceEntity) {
        viewModelScope.launch {
            repository.deleteService(item)
        }
    }
}
