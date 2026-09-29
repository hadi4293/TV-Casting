package com.hadii.tvcasing.streaming

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class CertUtilsTest {

    @Test
    fun generatesCertWithSanForGivenIp() {
        val ip = "192.168.1.50"
        val (keyPair, cert) = CertUtils.generate(ip)

        assertNotNull(keyPair)
        assertNotNull(cert)
        assertEquals(2048, keyPair.public.bitLength)

        val sans = cert.subjectAlternativeNames ?: emptyList()
        val ipSans = sans.mapNotNull { entry ->
            val type = entry[0] as? Int
            val value = entry[1]
            if (type == 7) value?.toString() else null // 7 = iPAddress
        }
        assertTrue("IP SAN missing", ipSans.contains(ip))

        // Verify the cert actually chains to its own public key.
        cert.verify(keyPair.public)
    }

    @Test
    fun certIsNotExpiredOnCreation() {
        val (_, cert) = CertUtils.generate("10.0.0.5")
        val now = System.currentTimeMillis()
        assertTrue(cert.notBefore.time <= now)
        assertTrue(cert.notAfter.time > now)
    }
}
