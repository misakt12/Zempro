import androidx.compose.ui.window.ComposeUIViewController
import com.zakazky.app.common.models.AppDatabase
import com.zakazky.app.common.utils.PlatformStorage

actual fun getPlatformName(): String = "iOS"

fun MainViewController() = ComposeUIViewController {
    AppDatabase.init(PlatformStorage())
    App()
}