package com.trustarx.pitgo.core.vehicle.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.trustarx.pitgo.R

private val vehicleMakes = listOf(
    "Agrale", "Audi", "BMW", "CAOA Chery", "Chevrolet", "Citroën", "DAF",
    "Dodge", "Fiat", "Ford", "Foton", "Honda", "Hyundai", "Iveco", "JAC",
    "Jeep", "Kia", "Land Rover", "MAN", "Mercedes-Benz", "Mitsubishi", "Nissan",
    "Peugeot", "Porsche", "RAM", "Renault", "Scania", "Subaru", "Suzuki",
    "Toyota", "Volkswagen", "Volvo",
)

@Composable
fun VehicleMakePicker(
    value: String,
    enabled: Boolean,
    focusRequester: FocusRequester? = null,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.vehicle_brand), style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().then(
                    focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
                ),
            ) {
                Text(value.ifBlank { stringResource(R.string.vehicle_make_select) })
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                vehicleMakes.forEach { make ->
                    DropdownMenuItem(
                        text = { Text(make) },
                        onClick = {
                            expanded = false
                            onSelected(make)
                        },
                    )
                }
            }
        }
    }
}
