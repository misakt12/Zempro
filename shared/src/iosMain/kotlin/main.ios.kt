import androidx.compose.ui.window.ComposeUIViewController

actual fun getPlatformName(): String = "iOS"

import com.zakazky.app.common.models.AppDatabase
import com.zakazky.app.common.utils.PlatformStorage

fun MainViewController() = ComposeUIViewController {
    AppDatabase.init(PlatformStorage())
    App()
}