package com.magicitengineer.batterynotifierandroidmobileapp

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseOptimizationContractTest {
    @Test
    fun releaseEnablesR8AndResourceShrinking() {
        val buildFile = File("build.gradle.kts").readText()

        assertEquals(1, "isMinifyEnabled = true".toRegex().findAll(buildFile).count())
        assertEquals(1, "isShrinkResources = true".toRegex().findAll(buildFile).count())
        assertTrue(buildFile.contains("getDefaultProguardFile(\"proguard-android-optimize.txt\")"))
        assertFalse(buildFile.contains("isMinifyEnabled = false"))
    }

    @Test
    fun projectEnablesOptimizedResourceShrinkingWithoutDisablingFullMode() {
        val properties = File("../gradle.properties").readText()

        assertTrue(properties.lineSequence().any { it.trim() == "android.r8.optimizedResourceShrinking=true" })
        assertFalse(properties.lineSequence().any { it.trim() == "android.enableR8.fullMode=false" })
    }

    @Test
    fun projectRulesDoNotDisableOptimizationOrKeepTheWholeApplication() {
        val rules = File("proguard-rules.pro").readText()

        assertFalse(rules.contains("-dontshrink"))
        assertFalse(rules.contains("-dontoptimize"))
        assertFalse(rules.contains("-dontobfuscate"))
        assertFalse(rules.contains("-keep class ** { *; }"))
        assertFalse(rules.contains("-keep class com.magicitengineer.** { *; }"))
    }
}
