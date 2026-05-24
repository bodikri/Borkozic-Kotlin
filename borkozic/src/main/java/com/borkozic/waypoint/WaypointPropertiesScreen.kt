package com.borkozic.waypoint

import android.content.Intent
import android.widget.PopupMenu
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Waypoint
import com.borkozic.data.WaypointSet
import com.borkozic.ui.ColorButton
import com.borkozic.ui.MarkerPickerActivity
import com.borkozic.ui.OnColorChangedListener
import com.borkozic.util.StringFormatter
import com.jhlabs.map.GeodeticPosition
import com.jhlabs.map.UTMReference

@Composable
fun WaypointPropertiesScreen(
    routeIdx: Int = 0,
    waypoint: Waypoint,
    iconValue: String?,
    defMarkerColor: Int,
    defTextColor: Int,
    onIconChanged: (String?) -> Unit,
    onSave: (Waypoint) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val application = BaseApplication.getApplication<Borkozic>()!!

    var name by remember { mutableStateOf(waypoint.name) }
    var description by remember { mutableStateOf(waypoint.description) }
    var altitude by remember {
        mutableStateOf(if (waypoint.altitude.toInt() == Int.MIN_VALUE) "" else waypoint.altitude.toString())
    }
    var proximity by remember {
        mutableStateOf(if (waypoint.proximity == 0) "" else waypoint.proximity.toString())
    }
    var selectedTab by remember { mutableStateOf(0) }
    var coordFormat by remember { mutableStateOf(application.coordinateFormat) }

    // Coordinate state — DD
    var latDD by remember { mutableStateOf(StringFormatter.coordinate(0, waypoint.latitude)) }
    var lonDD by remember { mutableStateOf(StringFormatter.coordinate(0, waypoint.longitude)) }

    // DDMM
    var latMD by remember { mutableStateOf("0") }
    var latMM by remember { mutableStateOf("0.0000") }
    var lonMD by remember { mutableStateOf("0") }
    var lonMM by remember { mutableStateOf("0.0000") }

    // DMS
    var latSD by remember { mutableStateOf("0") }
    var latSM by remember { mutableStateOf("0") }
    var latSS by remember { mutableStateOf("0.000") }
    var lonSD by remember { mutableStateOf("0") }
    var lonSM by remember { mutableStateOf("0") }
    var lonSS by remember { mutableStateOf("0.000") }

    // UTM
    var utmEasting by remember { mutableStateOf("") }
    var utmNorthing by remember { mutableStateOf("") }
    var utmZone by remember { mutableStateOf("") }
    var utmSouth by remember { mutableStateOf(false) }

    // Colors
    var markerColor by remember {
        mutableStateOf(if (waypoint.backcolor == Int.MIN_VALUE) defMarkerColor else waypoint.backcolor)
    }
    var textColor by remember {
        mutableStateOf(if (waypoint.textcolor == Int.MIN_VALUE) defTextColor else waypoint.textcolor)
    }

    // Set
    var setIndex by remember {
        mutableStateOf(
            if (waypoint.set == null) 0 else application.waypointSets.indexOf(waypoint.set).coerceAtLeast(0)
        )
    }

    fun initCoordFields(coords: GeodeticPosition) {
        latDD = StringFormatter.coordinate(0, coords.lat)
        lonDD = StringFormatter.coordinate(0, coords.lon)
        val absLat = Math.abs(coords.lat)
        val absLon = Math.abs(coords.lon)
        latMD = (Math.floor(absLat) * Math.signum(coords.lat)).toInt().toString()
        latMM = String.format("%.4f", (absLat - Math.floor(absLat)) * 60)
        lonMD = (Math.floor(absLon) * Math.signum(coords.lon)).toInt().toString()
        lonMM = String.format("%.4f", (absLon - Math.floor(absLon)) * 60)
        val dmsLat = (absLat - Math.floor(absLat)) * 60
        val dmsLon = (absLon - Math.floor(absLon)) * 60
        latSD = latMD; latSM = Math.floor(dmsLat).toInt().toString()
        latSS = String.format("%.3f", (dmsLat - Math.floor(dmsLat)) * 60)
        lonSD = lonMD; lonSM = Math.floor(dmsLon).toInt().toString()
        lonSS = String.format("%.3f", (dmsLon - Math.floor(dmsLon)) * 60)
        try {
            val utm = UTMReference.toUTMRef(GeodeticPosition(coords.lat, coords.lon))
            utmEasting = Math.round(utm.easting).toString()
            utmNorthing = Math.round(utm.northing).toString()
            utmZone = utm.lngZone.toString()
            utmSouth = utm.isSouthernHemisphere
        } catch (_: Exception) {}
    }

    fun parseCoords(): GeodeticPosition {
        return try {
            when (coordFormat) {
                0 -> GeodeticPosition(
                    latDD.toDoubleOrNull() ?: waypoint.latitude,
                    lonDD.toDoubleOrNull() ?: waypoint.longitude
                )
                1 -> {
                    var ld = latMD.toIntOrNull() ?: return GeodeticPosition(waypoint.latitude, waypoint.longitude)
                    var m = (latMM.toDoubleOrNull() ?: 0.0) / 60.0
                    if (ld != 0) m *= Math.signum(ld.toDouble())
                    val lat = ld + m
                    ld = lonMD.toIntOrNull() ?: 0
                    m = (lonMM.toDoubleOrNull() ?: 0.0) / 60.0
                    if (ld != 0) m *= Math.signum(ld.toDouble())
                    val lon = ld + m
                    GeodeticPosition(lat, lon)
                }
                2 -> {
                    var ld = latSD.toIntOrNull() ?: return GeodeticPosition(waypoint.latitude, waypoint.longitude)
                    var m = (latSM.toIntOrNull() ?: 0) + (latSS.toDoubleOrNull() ?: 0.0) / 60.0
                    m /= 60.0
                    if (ld != 0) m *= Math.signum(ld.toDouble())
                    val lat = ld + m
                    ld = lonSD.toIntOrNull() ?: 0
                    m = (lonSM.toIntOrNull() ?: 0) + (lonSS.toDoubleOrNull() ?: 0.0) / 60.0
                    m /= 60.0
                    if (ld != 0) m *= Math.signum(ld.toDouble())
                    val lon = ld + m
                    GeodeticPosition(lat, lon)
                }
                3 -> {
                    val e = utmEasting.toDoubleOrNull() ?: return GeodeticPosition(waypoint.latitude, waypoint.longitude)
                    val n = utmNorthing.toDoubleOrNull() ?: return GeodeticPosition(waypoint.latitude, waypoint.longitude)
                    val z = utmZone.toIntOrNull() ?: return GeodeticPosition(waypoint.latitude, waypoint.longitude)
                    val band = UTMReference.getUTMNorthingZoneLetter(utmSouth, n)
                    val utm = UTMReference(z, band, e, n)
                    val ll = utm.toLatLng()
                    GeodeticPosition(ll.lat, ll.lon)
                }
                else -> GeodeticPosition(waypoint.latitude, waypoint.longitude)
            }
        } catch (_: Exception) {
            GeodeticPosition(waypoint.latitude, waypoint.longitude)
        }
    }

    fun updateForFormat(newFormat: Int) {
        if (newFormat == coordFormat) return
        val coords = parseCoords()
        coordFormat = newFormat
        initCoordFields(coords)
    }

    // Init
    LaunchedEffect(Unit) { initCoordFields(GeodeticPosition(waypoint.latitude, waypoint.longitude)) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (routeIdx == 0) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Primary", Modifier.padding(12.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Advanced", Modifier.padding(12.dp)) }
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> PrimaryTab(
                    name = name,
                    onNameChange = { name = it },
                    showIcon = routeIdx == 0 && application.iconsEnabled,
                    iconValue = iconValue,
                    onIconClick = {
                        val act = context as? WaypointProperties
                        if (act != null) {
                            val fakeView = android.view.View(context)
                            fakeView.id = android.R.id.content
                            val popup = PopupMenu(context, fakeView)
                            popup.menuInflater.inflate(R.menu.marker_popup, popup.menu)
                            popup.setOnMenuItemClickListener { item ->
                                when (item.itemId) {
                                    R.id.change -> {
                                        act.startActivityForResult(
                                            Intent(context, MarkerPickerActivity::class.java), 0
                                        )
                                        true
                                    }
                                    R.id.remove -> { onIconChanged(null); true }
                                    else -> false
                                }
                            }
                            popup.show()
                        }
                    },
                    coordFormat = coordFormat,
                    onCoordFormatChange = { updateForFormat(it) },
                    latDD = latDD, onLatDDChange = { latDD = it },
                    lonDD = lonDD, onLonDDChange = { lonDD = it },
                    latMD = latMD, onLatMDChange = { latMD = it },
                    latMM = latMM, onLatMMChange = { latMM = it },
                    lonMD = lonMD, onLonMDChange = { lonMD = it },
                    lonMM = lonMM, onLonMMChange = { lonMM = it },
                    latSD = latSD, onLatSDChange = { latSD = it },
                    latSM = latSM, onLatSMChange = { latSM = it },
                    latSS = latSS, onLatSSChange = { latSS = it },
                    lonSD = lonSD, onLonSDChange = { lonSD = it },
                    lonSM = lonSM, onLonSMChange = { lonSM = it },
                    lonSS = lonSS, onLonSSChange = { lonSS = it },
                    utmEasting = utmEasting, onUtmEastingChange = { utmEasting = it },
                    utmNorthing = utmNorthing, onUtmNorthingChange = { utmNorthing = it },
                    utmZone = utmZone, onUtmZoneChange = { utmZone = it },
                    utmSouth = utmSouth, onUtmSouthChange = { utmSouth = it },
                    altitude = altitude, onAltitudeChange = { altitude = it },
                    proximity = proximity, onProximityChange = { proximity = it }
                )
                1 -> AdvancedTab(
                    description = description,
                    onDescriptionChange = { description = it },
                    setIndex = setIndex,
                    onSetIndexChange = { setIndex = it },
                    waypointSets = application.waypointSets,
                    markerColor = markerColor,
                    onMarkerColorChange = { markerColor = it },
                    textColor = textColor,
                    onTextColorChange = { textColor = it },
                    defMarkerColor = defMarkerColor,
                    defTextColor = defTextColor
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (name.isEmpty()) return@Button
                waypoint.name = name
                waypoint.description = description
                val coords = parseCoords()
                waypoint.latitude = coords.lat
                waypoint.longitude = coords.lon
                waypoint.altitude = altitude.toDoubleOrNull() ?: Int.MIN_VALUE.toDouble()
                waypoint.proximity = proximity.toIntOrNull() ?: 0
                if (iconValue == null) {
                    waypoint.image = ""; waypoint.drawImage = false
                } else {
                    waypoint.image = iconValue; waypoint.drawImage = true
                }
                if (markerColor != defMarkerColor) waypoint.backcolor = markerColor
                if (textColor != defTextColor) waypoint.textcolor = textColor
                if (routeIdx == 0 && waypoint.set == null) {
                    application.addWaypoint(waypoint)
                }
                if (routeIdx == 0) {
                    waypoint.set = application.waypointSets.getOrNull(setIndex)
                }
                onSave(waypoint)
            }) { Text("Done") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrimaryTab(
    name: String, onNameChange: (String) -> Unit,
    showIcon: Boolean, iconValue: String?,
    onIconClick: () -> Unit,
    coordFormat: Int, onCoordFormatChange: (Int) -> Unit,
    latDD: String, onLatDDChange: (String) -> Unit,
    lonDD: String, onLonDDChange: (String) -> Unit,
    latMD: String, onLatMDChange: (String) -> Unit,
    latMM: String, onLatMMChange: (String) -> Unit,
    lonMD: String, onLonMDChange: (String) -> Unit,
    lonMM: String, onLonMMChange: (String) -> Unit,
    latSD: String, onLatSDChange: (String) -> Unit,
    latSM: String, onLatSMChange: (String) -> Unit,
    latSS: String, onLatSSChange: (String) -> Unit,
    lonSD: String, onLonSDChange: (String) -> Unit,
    lonSM: String, onLonSMChange: (String) -> Unit,
    lonSS: String, onLonSSChange: (String) -> Unit,
    utmEasting: String, onUtmEastingChange: (String) -> Unit,
    utmNorthing: String, onUtmNorthingChange: (String) -> Unit,
    utmZone: String, onUtmZoneChange: (String) -> Unit,
    utmSouth: Boolean, onUtmSouthChange: (Boolean) -> Unit,
    altitude: String, onAltitudeChange: (String) -> Unit,
    proximity: String, onProximityChange: (String) -> Unit
) {
    val coordFormats = listOf("DD.DDDDDD", "DD\u00B0 MM.MMMM\u2032", "DD\u00B0 MM\u2032 SS.SSS\u2033", "UTM")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Name + Icon row
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                Text("Name", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = name, onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
            if (showIcon) {
                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Icon", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = onIconClick) {
                        Text("Pick")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Coordinate format
        Text("Coordinates format", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            coordFormats.forEachIndexed { idx, label ->
                FilterChip(
                    selected = coordFormat == idx,
                    onClick = { onCoordFormatChange(idx) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Coordinates
        when (coordFormat) {
            0 -> { // DD
                Text("Latitude", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(latDD, onLatDDChange, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("DD.DDDDDD") })
                Spacer(Modifier.height(8.dp))
                Text("Longitude", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(lonDD, onLonDDChange, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("DD.DDDDDD") })
            }
            1 -> { // DD MM.MMMM
                Text("Latitude", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(latMD, onLatMDChange, Modifier.weight(1f), singleLine = true,
                        label = { Text("DD\u00B0") })
                    OutlinedTextField(latMM, onLatMMChange, Modifier.weight(1f), singleLine = true,
                        label = { Text("MM.MMMM\u2032") })
                }
                Spacer(Modifier.height(8.dp))
                Text("Longitude", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(lonMD, onLonMDChange, Modifier.weight(1f), singleLine = true,
                        label = { Text("DD\u00B0") })
                    OutlinedTextField(lonMM, onLonMMChange, Modifier.weight(1f), singleLine = true,
                        label = { Text("MM.MMMM\u2032") })
                }
            }
            2 -> { // DMS
                Text("Latitude", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(latSD, onLatSDChange, Modifier.weight(1f), singleLine = true,
                        label = { Text("DD\u00B0") })
                    OutlinedTextField(latSM, onLatSMChange, Modifier.weight(1f), singleLine = true,
                        label = { Text("MM\u2032") })
                    OutlinedTextField(latSS, onLatSSChange, Modifier.weight(1f), singleLine = true,
                        label = { Text("SS\u2033") })
                }
                Spacer(Modifier.height(8.dp))
                Text("Longitude", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(lonSD, onLonSDChange, Modifier.weight(1f), singleLine = true,
                        label = { Text("DD\u00B0") })
                    OutlinedTextField(lonSM, onLonSMChange, Modifier.weight(1f), singleLine = true,
                        label = { Text("MM\u2032") })
                    OutlinedTextField(lonSS, onLonSSChange, Modifier.weight(1f), singleLine = true,
                        label = { Text("SS\u2033") })
                }
            }
            3 -> { // UTM
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(utmEasting, onUtmEastingChange, Modifier.weight(1f), singleLine = true,
                        label = { Text("Easting") })
                    OutlinedTextField(utmNorthing, onUtmNorthingChange, Modifier.weight(1f), singleLine = true,
                        label = { Text("Northing") })
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(utmZone, onUtmZoneChange, Modifier.weight(1f), singleLine = true,
                        label = { Text("Zone") })
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hemisphere", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !utmSouth, onClick = { onUtmSouthChange(false) })
                            Text("N")
                            Spacer(Modifier.width(8.dp))
                            RadioButton(selected = utmSouth, onClick = { onUtmSouthChange(true) })
                            Text("S")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Altitude", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(altitude, onAltitudeChange, Modifier.fillMaxWidth(), singleLine = true)
            }
            Column(Modifier.weight(1f)) {
                Text("Proximity", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(proximity, onProximityChange, Modifier.fillMaxWidth(), singleLine = true)
            }
        }
    }
}

@Composable
private fun AdvancedTab(
    description: String, onDescriptionChange: (String) -> Unit,
    setIndex: Int, onSetIndexChange: (Int) -> Unit,
    waypointSets: List<WaypointSet>,
    markerColor: Int, onMarkerColorChange: (Int) -> Unit,
    textColor: Int, onTextColorChange: (Int) -> Unit,
    defMarkerColor: Int, defTextColor: Int
) {
    var showSetDropdown by remember { mutableStateOf(false) }
    val setNames = remember(waypointSets) { waypointSets.map { it.name } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Description", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = description, onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5
        )

        Spacer(Modifier.height(12.dp))

        Text("Waypoint Set", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { showSetDropdown = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(setNames.getOrElse(setIndex) { "None" })
            }
            DropdownMenu(
                expanded = showSetDropdown,
                onDismissRequest = { showSetDropdown = false }
            ) {
                setNames.forEachIndexed { idx, nm ->
                    DropdownMenuItem(
                        text = { Text(nm) },
                        onClick = { onSetIndexChange(idx); showSetDropdown = false }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Marker Color", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                PropertyColorButton(markerColor, defMarkerColor, onMarkerColorChange)
            }
            Column(Modifier.weight(1f)) {
                Text("Text Color", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                PropertyColorButton(textColor, defTextColor, onTextColorChange)
            }
        }
    }
}

@Composable
private fun PropertyColorButton(
    currentColor: Int,
    defaultColor: Int,
    onColorChanged: (Int) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            ColorButton(ctx).apply {
                setColor(currentColor, defaultColor)
                setOnColorChangeListener(object : OnColorChangedListener {
                    override fun colorChanged(newColor: Int) {
                        onColorChanged(newColor)
                    }
                })
            }
        },
        modifier = Modifier.wrapContentWidth(),
        update = { btn -> btn.setColor(currentColor, defaultColor) }
    )
}
