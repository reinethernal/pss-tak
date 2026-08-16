package soy.engindearing.omnitak.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.maplibre.android.geometry.LatLng
import soy.engindearing.omnitak.mobile.data.SarPointCatalog
import soy.engindearing.omnitak.mobile.ui.theme.TacticalAccent
import soy.engindearing.omnitak.mobile.ui.theme.TacticalBackground
import soy.engindearing.omnitak.mobile.ui.theme.TacticalSurface

data class SarPointSelection(
    val point: SarPointCatalog.SarPoint,
    val name: String,
    val remarksExtra: String,
)

/**
 * Civil SAR / ПСР point palette (LKP, PLS, IPP, checked, danger, rally).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SarPointPaletteSheet(
    visible: Boolean,
    latLng: LatLng?,
    onConfirm: (SarPointSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var picked by remember { mutableStateOf<SarPointCatalog.SarPoint?>(null) }
    var name by remember(picked) { mutableStateOf(picked?.kind?.callsign ?: "") }
    var remarksExtra by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = TacticalBackground,
        scrimColor = Color.Black.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                "Точки ПСР",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "LKP / PLS / IPP / проверено / опасность / сборка",
                color = Color(0xFFFFEB3B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "На карте штаба фильтруются по префиксу remarks «psr:…».",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            latLng?.let {
                Text(
                    rememberCoordText(it.latitude, it.longitude),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            ) {
                items(SarPointCatalog.all, key = { it.kind.name }) { point ->
                    SarTile(
                        point = point,
                        isPicked = picked?.kind == point.kind,
                        onPick = { picked = it },
                    )
                }
            }

            picked?.let { selected ->
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя на карте") },
                    singleLine = true,
                    colors = sarFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = remarksExtra,
                    onValueChange = { remarksExtra = it },
                    label = { Text("Примечание (опционально)") },
                    singleLine = false,
                    minLines = 2,
                    colors = sarFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        onConfirm(
                            SarPointSelection(
                                point = selected,
                                name = name.ifBlank { selected.kind.callsign },
                                remarksExtra = remarksExtra,
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TacticalAccent,
                        contentColor = TacticalBackground,
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(
                        "Поставить ${selected.kind.callsign}",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SarTile(
    point: SarPointCatalog.SarPoint,
    isPicked: Boolean,
    onPick: (SarPointCatalog.SarPoint) -> Unit,
) {
    val border = if (isPicked) BorderStroke(2.dp, point.accent) else null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isPicked) point.accent.copy(alpha = 0.18f) else TacticalSurface)
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(10.dp)) else Modifier)
            .clickable { onPick(point) }
            .padding(8.dp)
            .fillMaxWidth()
            .height(92.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .padding(top = 4.dp),
        ) {
            Icon(
                imageVector = point.image,
                contentDescription = point.labelRu,
                tint = point.accent,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = point.kind.callsign,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = point.labelRu.substringAfter("— ").ifBlank { point.labelEn },
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun sarFieldColors() = TextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
    focusedContainerColor = TacticalSurface,
    unfocusedContainerColor = TacticalSurface,
    focusedIndicatorColor = TacticalAccent,
    unfocusedIndicatorColor = TacticalAccent.copy(alpha = 0.4f),
    focusedLabelColor = TacticalAccent,
    unfocusedLabelColor = TacticalAccent.copy(alpha = 0.6f),
    cursorColor = TacticalAccent,
)
