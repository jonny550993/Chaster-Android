package app.chaster.client.wear

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun CopyableVersion() {
    val context = LocalContext.current
    val versionName = BuildConfig.VERSION_NAME
    TextButton(onClick = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("App version", versionName))
        Toast.makeText(context, "Version $versionName copied", Toast.LENGTH_SHORT).show()
    }) {
        Text("Version $versionName")
    }
}
