import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPlacement
import com.zakazky.app.common.models.AppDatabase
import com.zakazky.app.common.utils.PlatformStorage

fun main() {
    // OPRAVA: init() musí být voláno JEDNOU před spuštěním Compose,
    // ne uvnitř application{} lambdy, která se re-spouští při každé rekompose okna!
    AppDatabase.init(PlatformStorage())

    application {
        val state = rememberWindowState(placement = WindowPlacement.Maximized)
        Window(
            onCloseRequest = ::exitApplication, 
            title = "Zempro CZ - Správa Zakázek",
            state = state
        ) {
            MainView()
        }
    }
}