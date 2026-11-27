package pizza.kulhad.medslave

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun versionsCompareNumberByNumber() {
        assertTrue(newerVersion("2.02", "2.01"))
        assertTrue(newerVersion("v2.10", "2.9"))
        assertTrue(newerVersion("2.0.1", "2.0"))
        assertFalse(newerVersion("2.01", "2.01"))
        assertFalse(newerVersion("v2.01", "2.01"))
        assertFalse(newerVersion("2.0", "2.0.1"))
        assertFalse(newerVersion("1.99", "2.00"))
    }
}
