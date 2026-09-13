package com.left.app.feature.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.left.app.R
import com.left.app.core.designsystem.LeftIcons
import com.left.app.core.designsystem.component.LeftAmountField
import com.left.app.core.designsystem.component.LeftCard
import com.left.app.core.designsystem.component.LeftPrimaryButton
import com.left.app.core.designsystem.component.LeftTextButton
import com.left.app.core.designsystem.component.LeftTonalButton
import com.left.app.core.designsystem.theme.LeftTheme
import com.left.app.core.model.TransactionType
import com.left.app.feature.transactions.DATE_FORMATTER

/**
 * S08 Voice quick entry (PRD FR-04, Phase 5). Permission is requested inline
 * (only when the user opens this screen — PRD §21); recognized text is parsed
 * by [com.left.app.core.voice.VoiceExpenseParser] and ALWAYS confirmed before
 * saving. Graceful fallback to typing everywhere (unavailable recognizer,
 * denied permission, parse failure).
 */
@Composable
fun VoiceCaptureScreen(
    onBack: () -> Unit,
    onTypeInstead: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = LeftTheme.spacing

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (granted) viewModel.onPermissionGranted() else viewModel.onPermissionDenied()
    }

    // Ask once on entry; returning users with the grant go straight to listening.
    LaunchedEffect(Unit) {
        if (permissionGranted) viewModel.onPermissionGranted() else permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    // SpeechRecognizer lifecycle follows the LISTENING phase; destroyed on any exit.
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    DisposableEffect(state.phase) {
        if (state.phase == VoicePhase.LISTENING && permissionGranted) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                viewModel.onRecognizerUnavailable()
            } else {
                val sr = SpeechRecognizer.createSpeechRecognizer(context)
                sr.setRecognitionListener(
                    object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = Unit
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                        override fun onError(error: Int) = viewModel.onRecognizerError()
                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                            if (partial != null) viewModel.onPartialTranscript(partial)
                        }
                        override fun onResults(results: Bundle?) {
                            val best = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                            if (best != null) viewModel.onFinalTranscript(best) else viewModel.onRecognizerError()
                        }
                    },
                )
                sr.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    },
                )
                recognizer = sr
            }
        }
        onDispose {
            recognizer?.let { it.stopListening(); it.destroy() }
            recognizer = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.md),
    ) {
        Spacer(modifier = Modifier.height(spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = LeftIcons.Back,
                    contentDescription = stringResource(R.string.content_description_back),
                )
            }
            Spacer(modifier = Modifier.width(spacing.xs))
            Text(
                text = "Voice entry",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(spacing.xl))

        when (state.phase) {
            VoicePhase.PERMISSION -> {
                StatusText("Setting up the microphone…")
            }
            VoicePhase.LISTENING -> {
                Icon(
                    imageVector = LeftIcons.Mic,
                    contentDescription = "Listening",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.CenterHorizontally),
                )
                Spacer(modifier = Modifier.height(spacing.md))
                StatusText(
                    if (state.transcript.isBlank()) {
                        "Listening… try “Spent 250 on food at chai point”"
                    } else {
                        state.transcript
                    },
                )
            }
            VoicePhase.CONFIRMING -> {
                Text(
                    text = "Check before saving",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(spacing.md))
                LeftAmountField(
                    value = state.amountInput,
                    onValueChange = viewModel::onAmountChange,
                    label = "Amount (${state.currencyCode})",
                    isError = state.amountError != null,
                    supportingText = state.amountError,
                )
                Spacer(modifier = Modifier.height(spacing.md))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.type == TransactionType.EXPENSE,
                        onClick = { viewModel.onTypeChange(TransactionType.EXPENSE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Expense") }
                    SegmentedButton(
                        selected = state.type == TransactionType.INCOME,
                        onClick = { viewModel.onTypeChange(TransactionType.INCOME) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Income") }
                }
                Spacer(modifier = Modifier.height(spacing.md))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = state.categoryId == category.id,
                            onClick = {
                                viewModel.onCategoryChange(
                                    if (state.categoryId == category.id) null else category.id,
                                )
                            },
                            label = { Text(category.name) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.md))
                LeftCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        state.merchant?.let { ParsedRow("Merchant", it) }
                        state.date?.let { ParsedRow("Date", it.format(DATE_FORMATTER)) }
                        state.note?.let { ParsedRow("Heard", it) }
                    }
                }
                state.saveError?.let { error ->
                    Spacer(modifier = Modifier.height(spacing.sm))
                    Text(text = error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(spacing.lg))
                LeftPrimaryButton(
                    text = if (state.saving) "Saving…" else "Save transaction",
                    onClick = viewModel::onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.saving,
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    LeftTextButton(text = "Try again", onClick = viewModel::onStartListening, enabled = !state.saving)
                    LeftTextButton(text = "Type instead", onClick = onTypeInstead, enabled = !state.saving)
                }
            }
            VoicePhase.SAVED -> {
                StatusText("Saved. You’ll see it on the dashboard.")
                Spacer(modifier = Modifier.height(spacing.lg))
                LeftPrimaryButton(
                    text = "Add another",
                    onClick = viewModel::onStartListening,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    LeftTextButton(text = "Done", onClick = onBack)
                }
            }
            VoicePhase.ERROR -> {
                StatusText(state.errorMessage ?: "Something went wrong. Please try again.")
                Spacer(modifier = Modifier.height(spacing.lg))
                if (permissionGranted) {
                    LeftPrimaryButton(
                        text = "Try again",
                        onClick = viewModel::onStartListening,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(spacing.xs))
                }
                LeftTonalButton(
                    text = "Type it instead",
                    onClick = onTypeInstead,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ParsedRow(label: String, value: String) {
    val spacing = LeftTheme.spacing
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
