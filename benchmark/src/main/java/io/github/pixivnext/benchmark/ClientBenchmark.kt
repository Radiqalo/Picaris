package io.github.pixivnext.benchmark

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "io.github.pixivnext"

private fun MacrobenchmarkScope.preview() {
    if (!device.hasObject(By.text("海风经过的午后"))) device.wait(Until.hasObject(By.text("先体验界面")), 10000)
    device.findObject(By.text("先体验界面"))?.click()
    device.wait(Until.hasObject(By.text("海风经过的午后")), 10000)
}

@RunWith(AndroidJUnit4::class)
class ClientBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = CompilationMode.None(),
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
        }

    @Test
    fun browseArtwork() =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            compilationMode = CompilationMode.None(),
            setupBlock = {
                startActivityAndWait()
                preview()
            },
        ) {
            repeat(3) {
                device.swipe(700, 1700, 700, 750, 20)
                device.waitForIdle()
            }
            device.swipe(700, 600, 700, 1700, 20)
        }
}

@RunWith(AndroidJUnit4::class)
class ClientBaselineProfile {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun startup() =
        rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
        }

    @Test
    fun generate() =
        rule.collect(packageName = PACKAGE) {
            pressHome()
            startActivityAndWait()
            preview()
            repeat(3) {
                device.swipe(700, 1700, 700, 800, 20)
                device.waitForIdle()
            }
            device.findObject(By.text("我的"))?.click()
            device.waitForIdle()
            device.findObject(By.desc("设置"))?.click()
            device.waitForIdle()
        }
}
