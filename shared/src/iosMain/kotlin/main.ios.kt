import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.ui.uikit.OnFocusBehavior
import com.zakazky.app.common.models.AppDatabase
import com.zakazky.app.common.utils.PlatformStorage

actual fun getPlatformName(): String = "iOS"

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun MainViewController() = ComposeUIViewController(
    configure = {
        onFocusBehavior = OnFocusBehavior.DoNotPan
    }
) {
    AppDatabase.init(PlatformStorage())
    App()
}