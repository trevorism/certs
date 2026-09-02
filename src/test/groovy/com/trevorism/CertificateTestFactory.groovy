package com.trevorism

import com.trevorism.model.IssuedCertificate
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit

class CertificateTestFactory {

    static final String WILDCARD = "*.action.trevorism.com"
    static final String CA_NAME = "Test Root CA"
    static final String STAGING_CA_NAME = "(STAGING) Pretend Pear X1"

    static final KeyPair CA_KEY = generate(2048)
    static final KeyPair LEAF_KEY = generate(2048)
    static final KeyPair OTHER_KEY = generate(2048)

    static KeyPair generate(int bits) {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(bits)
        return generator.generateKeyPair()
    }

    static X509Certificate caCertificate(String commonName = CA_NAME) {
        return build(commonName, commonName, CA_KEY.getPublic(), CA_KEY.getPrivate(), [], futureDate())
    }

    static X509Certificate leafCertificate(Map options = [:]) {
        String issuerName = options.containsKey("issuerName") ? options.issuerName : CA_NAME
        List<String> dnsNames = options.containsKey("dnsNames") ? options.dnsNames : [WILDCARD]
        PublicKey publicKey = (options.leafKey ?: LEAF_KEY).getPublic()
        Date notAfter = options.notAfter ?: futureDate()
        return build(issuerName, "leaf", publicKey, CA_KEY.getPrivate(), dnsNames, notAfter)
    }

    static IssuedCertificate issued(Map options = [:]) {
        List<X509Certificate> chain = options.containsKey("chain") ? options.chain :
                [leafCertificate(options), caCertificate(options.issuerName ?: CA_NAME)]
        KeyPair keyPair = options.keyPair ?: LEAF_KEY
        return new IssuedCertificate(chain: chain, keyPair: keyPair, chainPem: toPem(chain))
    }

    static String toPem(List<X509Certificate> chain) {
        StringWriter stringWriter = new StringWriter()
        PemWriter pemWriter = new PemWriter(stringWriter)
        try {
            chain.each { pemWriter.writeObject(new PemObject("CERTIFICATE", it.getEncoded())) }
        } finally {
            pemWriter.close()
        }
        return stringWriter.toString()
    }

    static Date futureDate(long days = 90) {
        return Date.from(Instant.now().plus(days, ChronoUnit.DAYS))
    }

    static Date pastDate(long days = 1) {
        return Date.from(Instant.now().minus(days, ChronoUnit.DAYS))
    }

    private static X509Certificate build(String issuerName, String subjectName, PublicKey publicKey,
                                         PrivateKey signingKey, List<String> dnsNames, Date notAfter) {
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name("CN=${issuerName}"),
                BigInteger.valueOf(System.nanoTime()),
                Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                notAfter,
                new X500Name("CN=${subjectName}"),
                publicKey)

        if (dnsNames) {
            GeneralName[] names = dnsNames.collect { new GeneralName(GeneralName.dNSName, it) } as GeneralName[]
            builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names))
        }

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(signingKey)
        return new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(builder.build(signer))
    }
}
