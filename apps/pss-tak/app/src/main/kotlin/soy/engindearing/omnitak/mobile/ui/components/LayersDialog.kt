package soy.engindearing.omnitak.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import soy.engindearing.omnitak.mobile.ui.theme.TacticalAccent
import soy.engindearing.omnitak.mobile.ui.theme.TacticalBackground
import soy.engindearing.omnitak.mobile.ui.theme.TacticalSurface

/**
 * Map overlay visibility picker. One switch per toggleable layer;
 * caller owns the backing state and handles persistence.
 *
 * The "Mesh nodes" row controls visibility of mesh-origin contacts
 * (UID prefix `MESHTASTIC-`) on the tactical map without disabling
 * the bridge globally — the iOS layers picker exposes the same toggle.
 */
@Composable
fun LayersDialog(
    gridEnabled: Boolean,
    drawingsVisible: Boolean,
    trailsVisible: Boolean = false,
    aircraftVisible: Boolean,
    vesselsVisible: Boolean = true,
    contactsVisible: Boolean,
    callsignCardVisible: Boolean,
    meshNodesVisible: Boolean = true,
    map3dEnabled: Boolean = false,
    sarPointsOnly: Boolean = false,
    onToggleGrid: (Boolean) -> Unit,
    onToggleDrawings: (Boolean) -> Unit,
    onToggleTrails: (Boolean) -> Unit = {},
    onToggleAircraft: (Boolean) -> Unit,
    onToggleVessels: (Boolean) -> Unit = {},
    onToggleContacts: (Boolean) -> Unit,
    onToggleCallsignCard: (Boolean) -> Unit,
    onToggleMeshNodes: (Boolean) -> Unit = {},
    onToggle3d: (Boolean) -> Unit = {},
    onToggleSarPointsOnly: (Boolean) -> Unit = {},
    onOpenOfflineMaps: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TacticalSurface,
        title = {
            Text(
                "Слои карты",
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Monospace,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LayerRow("3D рельеф", map3dEnabled, onToggle3d)
                LayerRow("Контакты", contactsVisible, onToggleContacts)
                LayerRow("Узлы mesh", meshNodesVisible, onToggleMeshNodes)
                LayerRow("Только точки ПСР", sarPointsOnly, onToggleSarPointsOnly)
                LayerRow("Рисунки", drawingsVisible, onToggleDrawings)
                LayerRow("Треки", trailsVisible, onToggleTrails)
                LayerRow("Воздух (ADS-B)", aircraftVisible, onToggleAircraft)
                LayerRow("Суда (AIS)", vesselsVisible, onToggleVessels)
                LayerRow("Сетка lat/lon", gridEnabled, onToggleGrid)
                LayerRow("Карточка позывного", callsignCardVisible, onToggleCallsignCard)
                if (onOpenOfflineMaps != null) {
                    TextButton(
                        onClick = onOpenOfflineMaps,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Text(
                            "Офлайн-карты…",
                            color = TacticalAccent,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово", color = TacticalAccent)
            }
        },
    )
}

@Composable
private fun LayerRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TacticalBackground,
                checkedTrackColor = TacticalAccent,
                uncheckedThumbColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                uncheckedTrackColor = TacticalBackground,
            ),
        )
    }
}
