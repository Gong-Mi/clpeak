package kr.clpeak

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkSelectionAndArgvTest {
    private val catalog = BackendCatalog(
        listOf(
            BackendInfo("OpenCL", true, listOf(DeviceRef(0, 0, "Adreno"), DeviceRef(1, 2, "Mali"))),
            BackendInfo("Vulkan", true, listOf(DeviceRef(0, 0, "Adreno"), DeviceRef(0, 2, "Mali"))),
            BackendInfo("CPU", true, listOf(DeviceRef(0, 0, "CPU")))
        )
    )

    @Test
    fun allSelectedLeavesDeviceFlagsImplicit() {
        assertArrayEquals(arrayOf("clpeak"), ArgvBuilder.build(BenchmarkSelection.allOf(catalog), catalog))
    }

    @Test
    fun partialOpenClSelectionEmitsSortedPlatformAndDeviceFilters() {
        val selection = BenchmarkSelection(mapOf("OpenCL" to setOf("1:2"), "Vulkan" to emptySet(), "CPU" to emptySet()))
        assertArrayEquals(
            arrayOf("clpeak", "--cl-platform", "1", "--cl-device", "2", "--no-vulkan", "--no-cpu"),
            ArgvBuilder.build(selection, catalog)
        )
    }

    @Test
    fun toggleDeviceUpdatesOnlyTargetDevice() {
        val initial = BenchmarkSelection(mapOf("Vulkan" to setOf("0:0")))
        val toggled = initial.toggleDevice("Vulkan", "0:2")
        assertTrue(toggled.isSelected("Vulkan", "0:0"))
        assertTrue(toggled.isSelected("Vulkan", "0:2"))
        assertFalse(toggled.isEmpty)
        assertTrue(toggled.toggleDevice("Vulkan", "0:0").isSelected("Vulkan", "0:2"))
    }
}
