package moye.wear.wraive

import android.app.Application
import com.google.gson.GsonBuilder
import moye.wear.wraive.chat.ChatEngine
import moye.wear.wraive.chat.ToolRuntime
import moye.wear.wraive.chat.TranslationService
import moye.wear.wraive.audio.TranscriptionController
import moye.wear.wraive.data.AppRepository
import moye.wear.wraive.data.AttachmentReader
import moye.wear.wraive.data.BackupService
import moye.wear.wraive.data.CloudBackupService
import moye.wear.wraive.data.ConversationExporter
import moye.wear.wraive.data.ForeignImportService
import moye.wear.wraive.data.PreferencesStore
import moye.wear.wraive.data.ProviderQrCodec
import moye.wear.wraive.network.AiGateway
import moye.wear.wraive.network.McpClient
import moye.wear.wraive.network.NetworkProxyController
import moye.wear.wraive.network.SearchClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WraiveApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

class AppGraph(application: Application) {
    val gson = GsonBuilder().serializeNulls().create()
    val repository = AppRepository(application, gson)
    val preferences = PreferencesStore(application, gson)
    val networkProxyController = NetworkProxyController()
    val httpClient = AiGateway.defaultHttpClient(networkProxyController)
    val gateway = AiGateway(httpClient, gson, application)
    val searchClient = SearchClient(httpClient, gson)
    val mcpClient = McpClient(httpClient, gson)
    val toolRuntime = ToolRuntime(repository, searchClient, mcpClient, httpClient, gson)
    val chatEngine = ChatEngine(
        application,
        repository,
        preferences,
        gateway,
        toolRuntime
    )
    val translationService = TranslationService(repository, gateway)
    val backupService = BackupService(repository, preferences, gson)
    val cloudBackupService = CloudBackupService(repository, backupService, httpClient)
    val conversationExporter = ConversationExporter(repository)
    val foreignImportService = ForeignImportService(repository, gson)
    val providerQrCodec = ProviderQrCodec(gson)
    val attachmentReader = AttachmentReader(application)
    val transcriptionController = TranscriptionController(application, httpClient, gson)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            repository.networkProxyConfig.collectLatest(networkProxyController::update)
        }
    }
}
