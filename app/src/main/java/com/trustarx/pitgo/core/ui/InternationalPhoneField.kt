package com.trustarx.pitgo.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.common.validation.InternationalPhone
import java.util.Locale

@Composable
fun InternationalPhoneField(
    value: String,
    region: String,
    onValueChange: (String) -> Unit,
    onRegionChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    var selecting by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val locale = Locale.getDefault()
    val countries = remember(locale) {
        InternationalPhone.regions.sortedBy { Locale("", it).getDisplayCountry(locale) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { search = ""; selecting = true }, enabled = enabled,
            modifier = Modifier.padding(top = 8.dp).height(56.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text("$region +${InternationalPhone.callingCode(region)} ▾")
        }
        OutlinedTextField(value = value, onValueChange = onValueChange,
            label = { Text(stringResource(R.string.registration_phone)) },
            enabled = enabled, singleLine = true, modifier = Modifier.weight(1f),
            visualTransformation = remember(region) { CountryPhoneTransformation(region) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
    }
    if (selecting) AlertDialog(
        onDismissRequest = { selecting = false },
        title = { Text(stringResource(R.string.phone_country_title)) },
        text = {
            Column {
                OutlinedTextField(search, { search = it }, singleLine = true,
                    label = { Text(stringResource(R.string.phone_country_search)) })
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(countries.filter {
                        it.contains(search, true) || Locale("", it).getDisplayCountry(locale).contains(search, true) ||
                            "+${InternationalPhone.callingCode(it)}".contains(search)
                    }) { country ->
                        Text("${Locale("", country).getDisplayCountry(locale)} (+${InternationalPhone.callingCode(country)})",
                            Modifier.fillMaxWidth().clickable { onRegionChange(country); selecting = false }.padding(vertical = 14.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { selecting = false }) { Text(stringResource(R.string.phone_country_close)) } },
    )
}

internal class CountryPhoneTransformation(private val region: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = InternationalPhone.format(text.text, region)
        val positions = formatted.indices.filter { formatted[it].isDigit() }
        return TransformedText(AnnotatedString(formatted), object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (offset <= 0) 0 else positions.getOrNull(offset - 1)?.plus(1) ?: formatted.length
            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset.coerceIn(0, formatted.length)).count(Char::isDigit).coerceAtMost(text.length)
        })
    }
}
