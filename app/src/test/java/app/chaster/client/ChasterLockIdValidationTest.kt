package app.chaster.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChasterLockIdValidationTest {
    @Test fun acceptsSafeLockIdentifiers() {
        listOf("abc", "lock_123", "LOCK-456", "a".repeat(128)).forEach {
            assertTrue(it, isValidChasterLockId(it))
        }
    }

    @Test fun rejectsEmptyOverlongOrPathLikeIdentifiers() {
        listOf("", "a".repeat(129), "../profile", "id/other", "id?x=1", "white space").forEach {
            assertFalse(it, isValidChasterLockId(it))
        }
    }
}
