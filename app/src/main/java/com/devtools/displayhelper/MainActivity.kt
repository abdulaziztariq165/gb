package com.devtools.displayhelper

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(applicationContext)
        if (Build.VERSION.SDK_INT >= 33)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App(this) }
            }
        }
    }
}

private fun bg(f: () -> Unit) { Thread { f() }.start() }

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun SwitchRow(label: String, key: String, def: Boolean = false, onChange: () -> Unit) {
    var v by remember { mutableStateOf(Prefs.b(key, def)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = v, onCheckedChange = { v = it; Prefs.setB(key, it); onChange() })
    }
}

@Composable
fun SliderRow(label: String, key: String, min: Float, max: Float, def: Float, ver: Int = 0, onChange: () -> Unit) {
    var v by remember(ver) { mutableStateOf(Prefs.f(key, def)) }
    Column {
        Text("$label: ${"%.2f".format(v)}")
        Slider(value = v, onValueChange = { v = it; Prefs.setF(key, it); onChange() }, valueRange = min..max)
    }
}

@Composable
fun App(act: ComponentActivity) {
    var ver by remember { mutableStateOf(0) }
    var granted by remember { mutableStateOf(Shell.granted()) }
    var running by remember { mutableStateOf(Shell.running()) }
    var hz by remember { mutableStateOf(Prefs.f("hz", 60f).roundToInt()) }
    var scalePct by remember { mutableStateOf(100f) }
    val sync = { OverlayService.sync(act); OverlayService.anyOn(); Unit }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Dev Display Helper", style = MaterialTheme.typography.headlineSmall)

            Section("الصلاحيات (للمطورين)") {
                Text("Shizuku: " + if (!running) "غير مشغّل" else if (granted) "مفعّل ✔" else "يحتاج صلاحية")
                Button(onClick = { Shell.requestPermission(); running = Shell.running(); granted = Shell.granted() }) {
                    Text("طلب صلاحية Shizuku / تحديث")
                }
                Button(onClick = {
                    act.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${act.packageName}")))
                }) { Text("السماح بالعرض فوق التطبيقات") }
            }

            Section("معدل التحديث") {
                Text("الحالي: ${"%.0f".format(RefreshCtl.current(act))} Hz")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RefreshCtl.supported(act).forEach { r ->
                        FilterChip(selected = hz == r, onClick = { hz = r; Prefs.setF("hz", r.toFloat()) }, label = { Text("$r") })
                    }
                }
                Button(onClick = { bg { RefreshCtl.boost(hz) } }) { Text("رفع المعدل إلى $hz Hz") }
                Button(onClick = { bg { RefreshCtl.lock(hz) } }) { Text("تثبيت الإطارات على $hz Hz") }
                OutlinedButton(onClick = { bg { RefreshCtl.unlock() } }) { Text("إلغاء التثبيت") }
            }

            Section("عرض معدل التحديث الحي") {
                SwitchRow("إظهار اللوحة", "fps_on") { sync() }
                SliderRow("الحجم", "fps_size", 8f, 40f, 14f) { OverlayService.sync(act) }
                SliderRow("الشفافية", "fps_alpha", 0.1f, 1f, 0.85f) { OverlayService.sync(act) }
                SliderRow("الموضع أفقياً", "fps_x", 0f, 0.9f, 0.02f) { OverlayService.sync(act) }
                SliderRow("الموضع عمودياً", "fps_y", 0f, 0.9f, 0.05f) { OverlayService.sync(act) }
            }

            Section("دائرة مرئية في منتصف الشاشة") {
                Text("دائرة ثابتة للمساعدة البصرية فقط، لا تتعرف على شيء ولا تتحكم باللمس.")
                SwitchRow("إظهار الدائرة", "circle_on") { sync() }
                SwitchRow("لون أخضر (إيقاف = أحمر)", "circle_green") { OverlayService.sync(act) }
                SliderRow("نصف القطر", "circle_r", 20f, 200f, 60f) { OverlayService.sync(act) }
                SliderRow("الشفافية", "circle_alpha", 0.1f, 1f, 0.8f) { OverlayService.sync(act) }
            }

            Section("الزر العائم") {
                SwitchRow("تفعيل الزر العائم", "fab_on") { sync() }
                SliderRow("الحجم", "fab_size", 32f, 90f, 48f) { OverlayService.sync(act) }
                SliderRow("الشفافية", "fab_alpha", 0.1f, 1f, 0.85f) { OverlayService.sync(act) }
                Text("اسحب الزر لتغيير مكانه، واضغط عليه لفتح لوحة الإعدادات السريعة.")
            }

            Section("ألوان الشاشة (15 تعديل)") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ColorEngine.presets.keys.forEach { name ->
                        AssistChip(onClick = { ColorEngine.applyPreset(name); ver++; ColorEngine.applyAsync() }, label = { Text(name) })
                    }
                }
                ColorEngine.adjustments.forEach { a ->
                    SliderRow(a.label, a.key, a.min, a.max, a.def, ver) { ColorEngine.applyAsync() }
                }
                OutlinedButton(onClick = { ColorEngine.resetValues(); ver++; ColorEngine.resetScreen() }) { Text("إعادة ضبط الألوان") }
            }

            Section("الدقة وأبعاد الشاشة (بدون حواف سوداء)") {
                Text("تحافظ على نسبة أبعاد الشاشة الفيزيائية لتجنب الحواف السوداء.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Full HD" to 1080, "2K" to 1440, "4K" to 2160).forEach { (n, w) ->
                        AssistChip(onClick = { bg { RefreshCtl.setWidth(w) } }, label = { Text(n) })
                    }
                }
                Text("نسبة مخصصة: ${scalePct.roundToInt()}%")
                Slider(value = scalePct, onValueChange = { scalePct = it }, valueRange = 50f..150f)
                Button(onClick = {
                    bg { RefreshCtl.physical()?.let { RefreshCtl.setWidth((it.first * scalePct / 100f).roundToInt()) } }
                }) { Text("تطبيق") }
                OutlinedButton(onClick = { bg { RefreshCtl.resetSize() } }) { Text("إعادة الدقة الأصلية") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
