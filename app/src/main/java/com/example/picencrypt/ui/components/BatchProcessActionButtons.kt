package com.example.picencrypt.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.picencrypt.R
import com.example.picencrypt.utils.ImageScramble.ProcessType

/**
 * Reusable action buttons for triggering batch encryption and decryption.
 */
@Composable
fun BatchProcessActionButtons(
    onTriggerProcess: (ProcessType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        Button(
            onClick = { onTriggerProcess(ProcessType.ENCRYPT) },
            modifier = Modifier.width(120.dp)
        ) {
            Text(text = stringResource(id = R.string.encrypt_btn))
        }

        Button(
            onClick = { onTriggerProcess(ProcessType.DECRYPT) },
            modifier = Modifier.width(120.dp)
        ) {
            Text(text = stringResource(id = R.string.decrypt_btn))
        }
    }
}
