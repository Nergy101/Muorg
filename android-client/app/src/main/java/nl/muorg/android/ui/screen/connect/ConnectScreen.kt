package nl.muorg.android.ui.screen.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.muorg.android.ui.component.FieldIconButton
import nl.muorg.android.ui.component.LabeledField
import nl.muorg.android.ui.component.OnboardingBackButton
import nl.muorg.android.ui.component.OnboardingHeadline
import nl.muorg.android.ui.component.OnboardingPage
import nl.muorg.android.ui.component.OnboardingScreen
import nl.muorg.android.ui.component.OrcMotion
import nl.muorg.android.ui.component.OrcPortrait
import nl.muorg.android.ui.component.OrcStage
import nl.muorg.android.ui.component.OrcVariant
import nl.muorg.android.ui.component.PrimaryButton
import nl.muorg.android.ui.icon.MageIcon
import nl.muorg.android.ui.theme.ErrorRed
import nl.muorg.android.ui.theme.OnboardingBrand
import nl.muorg.android.ui.theme.OnboardingType

@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    onBack: () -> Unit,
    viewModel: ConnectViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ConnectEvent.Connected -> onConnected()
            }
        }
    }

    var showApiKey by remember { mutableStateOf(false) }
    val apiKeyFocus = remember { FocusRequester() }

    OnboardingScreen {
        OnboardingPage(
            modifier = Modifier.imePadding(),
            top = 56.dp,
            bottom = 40.dp,
        ) {
            OnboardingBackButton(onClick = onBack, modifier = Modifier.align(Alignment.Start))

            OrcStage(OnboardingBrand.ServerStage, Modifier.size(176.dp)) {
                OrcPortrait(
                    variant = OrcVariant.ServerTinker,
                    contentDescription = OrcVariant.ServerTinker.description,
                    motion = if (uiState.isLoading) OrcMotion.Wiggle else OrcMotion.Still,
                )
            }

            Spacer(Modifier.height(22.dp))

            OnboardingHeadline("Where does your server live?", OnboardingType.Headline32)

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Give me the address and key, I’ll wire it up.",
                style = OnboardingType.Body16,
                color = OnboardingBrand.Mist,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            LabeledField(
                label = "Server URL",
                value = uiState.serverUrl,
                onValueChange = viewModel::onServerUrlChange,
                placeholder = "http://192.168.1.100:7700",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { apiKeyFocus.requestFocus() }
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            LabeledField(
                label = "API key",
                value = uiState.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                placeholder = "Paste your key",
                visualTransformation = if (showApiKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { viewModel.connect() }
                ),
                trailing = {
                    FieldIconButton(
                        iconName = if (showApiKey) "eye-off" else "eye",
                        contentDescription = if (showApiKey) "Hide API key" else "Show API key",
                        tint = if (showApiKey) OnboardingBrand.Spotlight else OnboardingBrand.Mist,
                        onClick = { showApiKey = !showApiKey },
                    )
                },
                fieldModifier = Modifier.focusRequester(apiKeyFocus),
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.error?.let { error ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MageIcon(
                        "exclamation-triangle",
                        Modifier.padding(top = 1.dp).size(16.dp),
                        tint = ErrorRed,
                    )
                    Text(text = error, style = OnboardingType.Body13, color = ErrorRed)
                }
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(24.dp))

            PrimaryButton(
                text = if (uiState.isLoading) "Wiring it up…" else "Connect",
                busy = uiState.isLoading,
                onClick = viewModel::connect,
            )
        }
    }
}
