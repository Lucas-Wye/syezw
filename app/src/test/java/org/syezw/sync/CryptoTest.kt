package org.syezw.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CryptoTest {

    @Test
    fun deriveAesKeyFromPassphrase_produces16Bytes() {
        val key = deriveAesKeyFromPassphrase("test-passphrase")
        assertEquals(16, key.encoded.size)
    }

    @Test
    fun encryptDecrypt_roundTrip() {
        val key = deriveAesKeyFromPassphrase("secret")
        val input = "hello-sync".toByteArray(Charsets.UTF_8)
        val blob = encryptToBlob(input, key)
        val output = decryptFromBlob(blob, key)
        assertArrayEquals(input, output)
    }
}
