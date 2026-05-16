/*
 * Copyright (C) 2025 Elias Gheeraert
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.mashopy.passbook.parser

import android.content.Context
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

object PassSigner {

    private const val ALIAS = "passbook_signing_key"
    private const val KEYSTORE_FILE = "passbook.keystore"
    private const val KEYSTORE_PASSWORD = "passbook"

    // ── Get or create a persistent self-signed cert ───────────────────────────

    private fun getOrCreateKeyStore(context: Context): KeyStore {
        val ksFile = File(context.filesDir, KEYSTORE_FILE)
        val ks = KeyStore.getInstance("PKCS12")

        if (ksFile.exists()) {
            ksFile.inputStream().use { ks.load(it, KEYSTORE_PASSWORD.toCharArray()) }
            return ks
        }

        // Generate new key pair
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

        // Build self-signed certificate valid for 10 years
        val subject  = X500Principal("CN=Passbook, O=Self Signed, C=FR")
        val notBefore = Date()
        val notAfter  = Date(notBefore.time + 10L * 365 * 24 * 60 * 60 * 1000)

        val certHolder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(System.currentTimeMillis()),
            notBefore,
            notAfter,
            subject,
            keyPair.public
        ).build(
            JcaContentSignerBuilder("SHA256withRSA")
                .build(keyPair.private)
        )

        val cert = JcaX509CertificateConverter().getCertificate(certHolder)

        // Save to keystore
        ks.load(null, KEYSTORE_PASSWORD.toCharArray())
        ks.setKeyEntry(ALIAS, keyPair.private, KEYSTORE_PASSWORD.toCharArray(), arrayOf(cert))
        ksFile.outputStream().use { ks.store(it, KEYSTORE_PASSWORD.toCharArray()) }

        return ks
    }

    // ── Sign a manifest and return the DER-encoded PKCS7 signature ────────────

    fun sign(context: Context, manifestBytes: ByteArray): ByteArray {
        val ks   = getOrCreateKeyStore(context)
        val key  = ks.getKey(ALIAS, KEYSTORE_PASSWORD.toCharArray()) as PrivateKey
        val cert = ks.getCertificate(ALIAS) as X509Certificate

        val gen = CMSSignedDataGenerator()

        val signerBuilder = JcaSignerInfoGeneratorBuilder(
            JcaDigestCalculatorProviderBuilder().build()
        )
        gen.addSignerInfoGenerator(
            signerBuilder.build(
                JcaContentSignerBuilder("SHA256withRSA").build(key),
                cert
            )
        )
        gen.addCertificates(JcaCertStore(listOf(cert)))

        val signed = gen.generate(
            CMSProcessableByteArray(manifestBytes),
            false  // detached signature
        )
        return signed.encoded
    }
}
