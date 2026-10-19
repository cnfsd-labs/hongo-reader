package app.hongo.reader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hongo.reader.HongoApplication
import app.hongo.reader.R
import app.hongo.reader.data.sync.KoSyncAccount
import app.hongo.reader.data.sync.KoSyncException
import kotlinx.coroutines.launch

/**
 * Settings → reading progress sync: a KOReader sync server account (KOReader's own server, a
 * self-hosted one, or Calibre-Web Automated). Off until the reader signs in.
 */
@Composable
fun ProgressSyncSection() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as HongoApplication).container }
    val settings = container.koSyncSettings
    val account by settings.account.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.sync_title)) },
        supportingContent = {
            Text(
                account?.let { stringResource(R.string.sync_signed_in, it.username, it.server) }
                    ?: stringResource(R.string.sync_off),
            )
        },
        modifier = Modifier.clickable { editing = true },
    )
    if (!editing) return
    val signedIn = account
    if (signedIn != null) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.sync_title)) },
            text = { Text(stringResource(R.string.sync_signed_in_details, signedIn.username, signedIn.server)) },
            confirmButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.sync_close)) } },
            dismissButton = {
                TextButton(onClick = {
                    settings.signOut()
                    editing = false
                }) { Text(stringResource(R.string.sync_sign_out)) }
            },
        )
    } else {
        SignInDialog(onDismiss = { editing = false })
    }
}

@Composable
private fun SignInDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as HongoApplication).container }
    val scope = rememberCoroutineScope()
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }

    fun submit(register: Boolean) {
        if (server.isBlank() || username.isBlank() || password.isEmpty() || checking) return
        checking = true
        error = null
        scope.launch {
            try {
                val account = if (register) {
                    KoSyncAccount(server.trim(), username.trim(), KoSyncAccount.userKey(password)).also {
                        container.koSyncClient.register(it)
                        container.koSyncClient.authorize(it)
                    }
                } else {
                    container.koSyncClient.signIn(server, username, password)
                }
                container.koSyncSettings.signIn(account)
                onDismiss()
            } catch (e: KoSyncException) {
                error = when (e.reason) {
                    KoSyncException.Reason.UNAUTHORIZED -> R.string.sync_error_unauthorized
                    KoSyncException.Reason.USER_EXISTS -> R.string.sync_error_user_exists
                    KoSyncException.Reason.REGISTRATION_CLOSED -> R.string.sync_error_registration_closed
                    KoSyncException.Reason.NETWORK -> R.string.sync_error_network
                    KoSyncException.Reason.SERVER -> R.string.sync_error_server
                }
            } finally {
                checking = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.sync_message), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text(stringResource(R.string.sync_server)) },
                    placeholder = { Text(stringResource(R.string.sync_server_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    enabled = !checking,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.sync_username)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    enabled = !checking,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.catalog_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    enabled = !checking,
                )
                if (server.trim().startsWith("http://", ignoreCase = true) && password.isNotEmpty()) {
                    Text(
                        stringResource(R.string.catalog_insecure_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                error?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit(register = false) }, enabled = !checking) {
                Text(stringResource(R.string.sync_sign_in))
            }
        },
        dismissButton = {
            TextButton(onClick = { submit(register = true) }, enabled = !checking) {
                Text(stringResource(R.string.sync_register))
            }
        },
    )
}
