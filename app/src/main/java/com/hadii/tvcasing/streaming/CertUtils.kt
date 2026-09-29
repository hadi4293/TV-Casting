package com.hadii.tvcasing.streaming

import org.bouncycastle.asn1.DERUTF8String
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Generates a self-signed X.509 certificate that includes the local Wi-Fi IP
 * as a SAN entry, so browsers (incl. Android TV) accept it without warnings.
 */
object CertUtils {

    fun generate(ip: String, commonName: String = "TV Casting"): Pair<KeyPair, X509Certificate> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom())
        }.generateKeyPair()

        val now = Date()
        val expiry = Date(now.time + 365L * 24 * 60 * 60 * 1000)

        val name = X500NameBuilder(BCStyle.INSTANCE).apply {
            addRDN(BCStyle.CN, DERUTF8String(commonName))
            addRDN(BCStyle.O, DERUTF8String("Local"))
        }.build()

        val san = GeneralNames(arrayOf(
            GeneralName(GeneralName.iPAddress, ip),
            GeneralName(GeneralName.dNSName, "localhost"),
            GeneralName(GeneralName.dNSName, commonName),
        ))

        val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            name,
            BigInteger(64, SecureRandom()),
            now,
            expiry,
            name,
            keyPair.public,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(Extension.subjectAlternativeName, false, san)

        val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        return keyPair to cert
    }
}
