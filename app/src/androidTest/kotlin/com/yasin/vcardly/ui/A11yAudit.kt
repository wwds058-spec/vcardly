package com.yasin.vcardly.ui

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheckResult
import com.google.android.apps.common.testing.accessibility.framework.Parameters
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchyAndroid
import com.google.android.apps.common.testing.accessibility.framework.utils.contrast.BitmapImage
import java.util.Locale

/**
 * Runs Google's Accessibility Test Framework (the engine behind Android's Accessibility Scanner) on what is on screen:
 * touch target size, text and image contrast (measured on a real screenshot), missing or duplicate labels, clickable
 * items without a role, and more. The node tree is read through UiAutomation, so Compose semantics are included.
 * Every result is written to TestStorage (accessibility/<screen>.txt); errors are returned so the caller can fail.
 */
object A11yAudit {
    fun check(screen: String): List<String> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        // The tree appears once the accessibility connection is up; give Compose a moment to publish its semantics.
        var root = automation.rootInActiveWindow
        var tries = 0
        while ((root == null || root.childCount == 0) && tries < 20) {
            Thread.sleep(150)
            instrumentation.waitForIdleSync()
            root = automation.rootInActiveWindow
            tries++
        }
        val node = root ?: return listOf("[$screen] no accessibility tree was available")
        val hierarchy = AccessibilityHierarchyAndroid.newBuilder(node, instrumentation.targetContext).build()
        val parameters = Parameters().apply { automation.takeScreenshot()?.let { putScreenCapture(BitmapImage(it)) } }

        val results: List<AccessibilityHierarchyCheckResult> = AccessibilityCheckPreset
            .getAccessibilityHierarchyChecksForPreset(AccessibilityCheckPreset.LATEST)
            .flatMap { it.runCheckOnHierarchy(hierarchy, null, parameters) }
            .filter { it.type == AccessibilityCheckResultType.ERROR || it.type == AccessibilityCheckResultType.WARNING }

        val lines = results.map { r ->
            val e = r.element
            val what = listOfNotNull(e?.className?.toString()?.substringAfterLast('.'), e?.text?.toString(), e?.contentDescription?.toString())
                .filter { it.isNotBlank() }.joinToString(" | ")
            "[$screen] ${r.type} ${r.sourceCheckClass.simpleName}: ${r.getMessage(Locale.ENGLISH)} <$what> ${e?.boundsInScreen}"
        }
        TestStorage().openOutputFile("accessibility/$screen.txt").bufferedWriter().use { w ->
            w.write(if (lines.isEmpty()) "No accessibility issues.\n" else lines.joinToString("\n", postfix = "\n"))
        }
        return lines.filter { it.contains(" ${AccessibilityCheckResultType.ERROR} ") }
    }
}
