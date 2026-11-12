package com.webclass.plus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat

private data class ConversionUnit(
    val name: String,
    val toBase: (Double) -> Double,
    val fromBase: (Double) -> Double
)

private data class ConversionCategory(val name: String, val units: List<ConversionUnit>)

private val conversionCategories = listOf(
    ConversionCategory("長さ", listOf(
        ConversionUnit("ミリメートル", { it / 1000.0 }, { it * 1000.0 }),
        ConversionUnit("センチメートル", { it / 100.0 }, { it * 100.0 }),
        ConversionUnit("メートル", { it }, { it }),
        ConversionUnit("キロメートル", { it * 1000.0 }, { it / 1000.0 }),
        ConversionUnit("インチ", { it * 0.0254 }, { it / 0.0254 }),
        ConversionUnit("フィート", { it * 0.3048 }, { it / 0.3048 })
    )),
    ConversionCategory("重さ", listOf(
        ConversionUnit("ミリグラム", { it / 1_000_000.0 }, { it * 1_000_000.0 }),
        ConversionUnit("グラム", { it / 1000.0 }, { it * 1000.0 }),
        ConversionUnit("キログラム", { it }, { it }),
        ConversionUnit("トン", { it * 1000.0 }, { it / 1000.0 }),
        ConversionUnit("ポンド", { it * 0.45359237 }, { it / 0.45359237 })
    )),
    ConversionCategory("温度", listOf(
        ConversionUnit("摂氏（℃）", { it }, { it }),
        ConversionUnit("華氏（℉）", { (it - 32.0) * 5.0 / 9.0 }, { it * 9.0 / 5.0 + 32.0 }),
        ConversionUnit("ケルビン（K）", { it - 273.15 }, { it + 273.15 })
    )),
    ConversionCategory("速度", listOf(
        ConversionUnit("m/s", { it }, { it }),
        ConversionUnit("km/h", { it / 3.6 }, { it * 3.6 }),
        ConversionUnit("mph", { it * 0.44704 }, { it / 0.44704 }),
        ConversionUnit("ノット", { it * 0.514444 }, { it / 0.514444 })
    ))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitConverterScreen(isWideScreen: Boolean) {
    var categoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var fromIndex by rememberSaveable { mutableIntStateOf(0) }
    var toIndex by rememberSaveable { mutableIntStateOf(2) }
    var input by rememberSaveable { mutableStateOf("1") }
    val category = conversionCategories[categoryIndex]
    val parsed = input.replace(',', '.').toDoubleOrNull()
    val result = parsed?.let { category.units[toIndex].fromBase(category.units[fromIndex].toBase(it)) }
    val formatter = remember { DecimalFormat("0.########") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isWideScreen) 48.dp else 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("変換する種類", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            conversionCategories.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = categoryIndex == index,
                    onClick = {
                        categoryIndex = index
                        fromIndex = 0
                        toIndex = if (item.units.size > 2) 2 else 1
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, conversionCategories.size),
                    label = { Text(item.name) }
                )
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it.filter { char -> char.isDigit() || char == '.' || char == ',' || char == '-' } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("変換する値") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = input.isNotBlank() && parsed == null
        )

        UnitDropDown("変換元", category.units, fromIndex) { fromIndex = it }

        FilledTonalIconButton(
            onClick = {
                val previous = fromIndex
                fromIndex = toIndex
                toIndex = previous
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(Icons.Default.SwapVert, contentDescription = "変換元と変換先を入れ替え")
        }

        UnitDropDown("変換先", category.units, toIndex) { toIndex = it }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("変換結果", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = result?.let { formatter.format(it) } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(category.units[toIndex].name, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropDown(label: String, units: List<ConversionUnit>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = units[selectedIndex].name,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            units.forEachIndexed { index, unit ->
                DropdownMenuItem(
                    text = { Text(unit.name) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}
