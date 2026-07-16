package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.AppDatabase
import com.example.data.database.JobEntity
import com.example.data.database.ServiceEntity
import com.example.data.repository.KslRepository
import com.example.ui.theme.*
import com.example.ui.viewmodel.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize SQLite Room database, DAOs, and repository directly
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = KslRepository(database.serviceDao(), database.jobDao())

        // Create ViewModel using our factory
        val viewModelFactory = KslViewModelFactory(repository, applicationContext)

        setContent {
            MyApplicationTheme {
                val kslViewModel: KslViewModel = viewModel(factory = viewModelFactory)
                KslApp(viewModel = kslViewModel)
            }
        }
    }
}

class KslViewModelFactory(
    private val repository: KslRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(KslViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return KslViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun KslApp(viewModel: KslViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Observe synchronization notifications globally to trigger local notifications and system messages
    LaunchedEffect(Unit) {
        viewModel.syncNotification.collect { message ->
            Toast.makeText(context, "Sincronización Completada", Toast.LENGTH_SHORT).show()
            sendLocalNotification(context, "KSL Mecánica (Sincronizado)", message)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                is Screen.Login -> LoginScreen(viewModel = viewModel)
                is Screen.Dashboard -> DashboardScreen(viewModel = viewModel)
            }
        }
    }
}

// System-wide Local Notification sender
fun sendLocalNotification(context: Context, title: String, message: String) {
    val channelId = "ksl_sync_channel"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "KSL Sincronización de Trabajos",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Canal para notificaciones de trabajos sincronizados entre mecánicos"
        }
        notificationManager.createNotificationChannel(channel)
    }

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)

    notificationManager.notify((1..10000).random(), builder.build())
}

// ==========================================
// 1. LOGIN SCREEN (Single-use security password)
// ==========================================
@Composable
fun LoginScreen(viewModel: KslViewModel) {
    var password by remember { mutableStateOf("") }
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BankNavy,
                        Color(0xFF070F1C)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // High-end Bank/Workshop branding header
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(BankGold, BankBlue)))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(BankNavy),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = "Logo",
                        tint = BankGold,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "KSL MECÁNICA",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = BankGold,
                letterSpacing = 2.sp
            )

            Text(
                text = "Gestor Profesional de Precios para Taller",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Dark card for premium secure sign-on
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BankObsidian),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = BankBlue,
                        modifier = Modifier.size(28.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Acceso Único Inicial",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 17.sp
                    )
                    
                    Text(
                        text = "Introduce la contraseña autorizada para desbloquear permanentemente la estación de trabajo en este teléfono.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Introduce la contraseña", color = Color.White.copy(alpha = 0.35f)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = BankGold,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = BankNavy.copy(alpha = 0.5f),
                            unfocusedContainerColor = BankNavy.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (loginError != null) {
                        Text(
                            text = loginError ?: "",
                            color = BankRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    Button(
                        onClick = { viewModel.login(password) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BankGold,
                            contentColor = BankNavy
                        ),
                        shape = RoundedCornerShape(50.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_button")
                    ) {
                        Text(
                            text = "INGRESAR AL SISTEMA",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = "Estación de Trabajo Encriptada",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.35f),
                fontWeight = FontWeight.Light
            )


        }
    }
}

// ==========================================
// 2. DASHBOARD / WORKSTATION
// ==========================================
@Composable
fun DashboardScreen(viewModel: KslViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column {
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                
                // Professional Workshop Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BankNavy)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ElectricBike,
                            contentDescription = "Bike Logo",
                            tint = BankGold,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "KSL WORKSTATION",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                letterSpacing = 1.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(BankJade)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sincronizado / Base de Datos Activa",
                                    color = BankJade,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Logout / Lock app button
                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(BankRed.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = BankRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = currentTab == DashboardTab.COTIZAR,
                    onClick = { viewModel.selectTab(DashboardTab.COTIZAR) },
                    icon = { Icon(Icons.Default.Calculate, contentDescription = "Cotizar") },
                    label = { Text("Cotizar", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = BankMutedText,
                        unselectedTextColor = BankMutedText,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("tab_cotizar")
                )
                NavigationBarItem(
                    selected = currentTab == DashboardTab.HISTORIAL,
                    onClick = { viewModel.selectTab(DashboardTab.HISTORIAL) },
                    icon = { Icon(Icons.Default.History, contentDescription = "Historial") },
                    label = { Text("Historial", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = BankMutedText,
                        unselectedTextColor = BankMutedText,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("tab_historial")
                )
                NavigationBarItem(
                    selected = currentTab == DashboardTab.AJUSTES,
                    onClick = { viewModel.selectTab(DashboardTab.AJUSTES) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Configuración") },
                    label = { Text("Ajustes", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = BankMutedText,
                        unselectedTextColor = BankMutedText,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("tab_ajustes")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "tab_content_transition"
            ) { tab ->
                when (tab) {
                    DashboardTab.COTIZAR -> CotizarTab(viewModel = viewModel)
                    DashboardTab.HISTORIAL -> HistorialTab(viewModel = viewModel)
                    DashboardTab.AJUSTES -> AjustesTab(viewModel = viewModel)
                }
            }
        }
    }
}

// ==========================================
// 3. COTIZAR / SMART CALCULATOR TAB
// ==========================================
@Composable
fun CotizarTab(viewModel: KslViewModel) {
    val clientName by viewModel.clientNameInput.collectAsStateWithLifecycle()
    val description by viewModel.descriptionInput.collectAsStateWithLifecycle()
    val activeQuote by viewModel.activeQuote.collectAsStateWithLifecycle()
    val suggestion by viewModel.spellingSuggestion.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Main input card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Calculator Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "¿Qué servicio o repuesto necesita la moto?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Customer name
                OutlinedTextField(
                    value = clientName,
                    onValueChange = { viewModel.clientNameInput.value = it },
                    label = { Text("Nombre del Cliente (Opcional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("client_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Job Description Text Box
                OutlinedTextField(
                    value = description,
                    onValueChange = { viewModel.descriptionInput.value = it },
                    label = { Text("Escribe el servicio o repuesto (Mano de obra y piezas)") },
                    placeholder = { Text("Ej: cambio de puastillas y un bombillo de foco con aceite") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("job_description_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { viewModel.calculateAndSaveQuote() },
                    enabled = description.isNotBlank(),
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("calculate_button")
                ) {
                    Icon(imageVector = Icons.Default.FlashOn, contentDescription = "Calculate")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CALCULAR PRECIO AUTOMÁTICO", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // SPELLING CORRECTION CARD ("Did you mean... / Quisiste decir?")
        if (!suggestion.isNullOrBlank()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { viewModel.applySpellingSuggestion(suggestion!!) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Tip",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "¿Quisiste decir?",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 12.sp
                        )
                        Text(
                            text = suggestion!!,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                        Text(
                            text = "Haz clic aquí para autocorregir tu búsqueda al instante.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Active output billing calculation
        if (activeQuote != null) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("results_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "DESGLOSE DE COTIZACIÓN",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val matchedList = activeQuote!!.matchedServices
                    if (matchedList.isEmpty()) {
                        Text(
                            text = "No se encontraron coincidencias exactas o parciales de servicios o piezas en el catálogo.\nRevisa la ortografía de los términos o agrega el elemento nuevo en Ajustes.",
                            color = BankMutedText,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        matchedList.forEach { service ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Visual badge for Service vs Product
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (service.isProduct) Color(0xFFFF9800).copy(alpha = 0.12f)
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (service.isProduct) "REPUESTO" else "SERVICIO",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (service.isProduct) Color(0xFFE65100) else MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = service.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "$${String.format(Locale.US, "%,.2f", service.price)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BankDarkText,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            HorizontalDivider(color = BankBorder, thickness = 0.5.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL ESTIMADO",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = BankMutedText
                        )
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", activeQuote?.total ?: 0.0)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = BankRed
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.clearQuote() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Text("Limpiar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        val isQuoteSaved = activeQuote?.isSaved == true

                        Button(
                            onClick = { viewModel.registerJobAndNotify() },
                            enabled = (activeQuote?.total ?: 0.0) > 0 && !isQuoteSaved,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isQuoteSaved) BankJade else BankJade
                            ),
                            modifier = Modifier.weight(1.8f),
                            shape = RoundedCornerShape(50.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (isQuoteSaved) Icons.Default.CheckCircle else Icons.Default.Send,
                                contentDescription = "Sync"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isQuoteSaved) "TRABAJO REGISTRADO" else "REGISTRAR Y NOTIFICAR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Explanatory guidelines tip card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = BankLightGold)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Tip",
                    tint = BankGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "El sistema de inteligencia detecta errores de ortografía de los mecánicos al instante y sugiere la corrección con el botón '¿Quisiste decir?'. Además, cuando registras un trabajo, la aplicación simula el envío y sincronización instantánea de una notificación push al resto de los teléfonos de los mecánicos.",
                    fontSize = 11.sp,
                    color = BankMutedText,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// ==========================================
// 4. HISTORIAL / REGISTERED JOBS TAB
// ==========================================
@Composable
fun HistorialTab(viewModel: KslViewModel) {
    val jobs by viewModel.jobsHistory.collectAsStateWithLifecycle()

    if (jobs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.HistoryToggleOff,
                    contentDescription = "Empty",
                    tint = BankMutedText,
                    modifier = Modifier.size(60.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sin Trabajos Registrados",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = BankDarkText
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Todos los trabajos guardados se sincronizarán y se mostrarán aquí para el control de los demás mecánicos.",
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = BankMutedText,
                    lineHeight = 18.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("history_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "HISTORIAL GLOBAL DE MECÁNICOS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = BankMutedText,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            items(jobs, key = { it.id }) { job ->
                JobHistoryCard(job = job)
            }
        }
    }
}

@Composable
fun JobHistoryCard(job: JobEntity) {
    val formattedDate = remember(job.timestamp) {
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        sdf.format(Date(job.timestamp))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, BankBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.clientName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = BankDarkText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        color = BankMutedText
                    )
                }

                // Sincronizado status badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(BankJade.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Sync",
                        tint = BankJade,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SINCRONIZADO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = BankJade
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = job.description,
                fontSize = 13.sp,
                color = BankDarkText.copy(alpha = 0.85f),
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BankBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Importe Registrado",
                    fontSize = 11.sp,
                    color = BankMutedText,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$${String.format(Locale.US, "%,.2f", job.totalPrice)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = BankNavy
                )
            }


        }
    }
}

// ==========================================
// 5. AJUSTES / CATALOG CONFIGURATION TAB
// ==========================================
@Composable
fun AjustesTab(viewModel: KslViewModel) {
    val catalogItems by viewModel.services.collectAsStateWithLifecycle()

    var selectedSubTab by remember { mutableStateOf(0) } // 0 = Servicios, 1 = Productos/Piezas

    val servicesList = remember(catalogItems) { catalogItems.filter { !it.isProduct } }
    val productsList = remember(catalogItems) { catalogItems.filter { it.isProduct } }

    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<ServiceEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Sub Tabs selector
            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Servicios (${servicesList.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Productos Taller (${productsList.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                )
            }

            // Group description header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (selectedSubTab == 0) "MANO DE OBRA REGISTRADA" else "REPUESTOS Y MATERIALES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = BankMutedText
                    )
                    Text(
                        text = if (selectedSubTab == 0) "Precios fijados para reparaciones" else "Piezas, bombillos, aceites del taller",
                        fontSize = 11.sp,
                        color = BankMutedText
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = BankNavy),
                    shape = RoundedCornerShape(50.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AÑADIR NUEVO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            // Main catalog list
            val activeList = if (selectedSubTab == 0) servicesList else productsList

            if (activeList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (selectedSubTab == 0) Icons.Default.Build else Icons.Default.Inventory2,
                            contentDescription = "Empty catalog",
                            tint = BankMutedText.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No hay elementos registrados",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BankMutedText
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeList, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(0.5.dp, BankBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = BankDarkText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$${String.format(Locale.US, "%,.2f", item.price)}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = { itemToEdit = item },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = BankBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteCatalogItem(item) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = BankRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Elegant developer credit footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sistema KSL Mecánica • Made by Shodoky999",
                    fontSize = 11.sp,
                    color = BankMutedText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Add Dialog
        if (showAddDialog) {
            AddEditCatalogDialog(
                title = "Añadir al Catálogo",
                isDefaultProduct = selectedSubTab == 1,
                onDismiss = { showAddDialog = false },
                onSave = { name, price, isProduct ->
                    viewModel.addCatalogItem(name, price, isProduct)
                    showAddDialog = false
                }
            )
        }

        // Edit Dialog
        if (itemToEdit != null) {
            val item = itemToEdit!!
            AddEditCatalogDialog(
                title = "Editar Elemento",
                initialName = item.name,
                initialPrice = item.price,
                isDefaultProduct = item.isProduct,
                isEditMode = true,
                onDismiss = { itemToEdit = null },
                onSave = { name, price, isProduct ->
                    viewModel.updateCatalogItem(item, name, price, isProduct)
                    itemToEdit = null
                }
            )
        }
    }
}

@Composable
fun AddEditCatalogDialog(
    title: String,
    initialName: String = "",
    initialPrice: Double = 0.0,
    isDefaultProduct: Boolean = false,
    isEditMode: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, Double, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var priceStr by remember { mutableStateOf(if (initialPrice > 0) initialPrice.toString() else "") }
    var isProduct by remember { mutableStateOf(isDefaultProduct) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del servicio o producto") },
                    placeholder = { Text("Ej: Aceite, bombillito de foco...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Precio ($)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Render Type Selector Switch in both Create and Edit Mode
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BankLightBg)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tipo de Elemento",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = BankDarkText
                            )
                            Text(
                                text = if (isProduct) "Producto (Pieza/Taller)" else "Mano de Obra (Servicio)",
                                fontSize = 11.sp,
                                color = BankMutedText
                            )
                        }
                        
                        Switch(
                            checked = isProduct,
                            onCheckedChange = { isProduct = it }
                        )
                    }
                }
            },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceStr.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && price > 0.0) {
                        onSave(name, price, isProduct)
                    }
                },
                enabled = name.isNotBlank() && (priceStr.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
