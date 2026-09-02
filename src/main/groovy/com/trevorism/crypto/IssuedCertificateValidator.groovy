package com.trevorism.crypto

import com.trevorism.model.IssuedCertificate

import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant

class IssuedCertificateValidator {

    static final int REQUIRED_KEY_BITS = 2048
    static final int MIN_CHAIN_LENGTH = 2
    static final int MAX_CHAIN_LENGTH = 5

    private static final int SAN_TYPE_DNS = 2

    static void validateForUpload(IssuedCertificate issued, String wildcard) {
        rejectStagingIssuer(issued)
        requireChainLength(issued)
        requireChainSignatures(issued)
        requireUnexpiredLeaf(issued)
        requireRsaKeyOfExpectedSize(issued)
        requireKeyMatchesCertificate(issued)
        requireSubjectAlternativeName(issued, wildcard)
    }

    static void rejectStagingIssuer(IssuedCertificate issued) {
        String issuer = issued.issuer ?: ""
        if (issuer.toUpperCase().contains("STAGING")) {
            throw new CertificateValidationException("issuerIsNotStaging",
                    "refusing to upload a certificate issued by ${issuer}")
        }
    }

    static void requireChainLength(IssuedCertificate issued) {
        int length = issued.chain?.size() ?: 0
        if (length < MIN_CHAIN_LENGTH || length > MAX_CHAIN_LENGTH) {
            throw new CertificateValidationException("chainLength",
                    "expected between ${MIN_CHAIN_LENGTH} and ${MAX_CHAIN_LENGTH} certificates but found ${length}")
        }
    }

    static void requireChainSignatures(IssuedCertificate issued) {
        List<X509Certificate> chain = issued.chain
        for (int i = 0; i < chain.size() - 1; i++) {
            try {
                chain[i].verify(chain[i + 1].getPublicKey())
            } catch (Exception e) {
                throw new CertificateValidationException("chainSignatures",
                        "certificate ${i} is not signed by certificate ${i + 1} (${e.message})")
            }
        }
    }

    static void requireUnexpiredLeaf(IssuedCertificate issued) {
        Instant notAfter = issued.leaf.notAfter.toInstant()
        if (notAfter.isBefore(Instant.now())) {
            throw new CertificateValidationException("leafNotExpired",
                    "the issued certificate already expired at ${notAfter}")
        }
    }

    static void requireRsaKeyOfExpectedSize(IssuedCertificate issued) {
        if (!(issued.keyPair.getPrivate() instanceof RSAPrivateKey)) {
            throw new CertificateValidationException("keyIsRsa",
                    "App Engine only accepts RSA private keys")
        }
        int bits = ((RSAPrivateKey) issued.keyPair.getPrivate()).getModulus().bitLength()
        if (bits != REQUIRED_KEY_BITS) {
            throw new CertificateValidationException("keySize",
                    "App Engine requires a ${REQUIRED_KEY_BITS} bit modulus but this key is ${bits} bits")
        }
    }

    static void requireKeyMatchesCertificate(IssuedCertificate issued) {
        RSAPublicKey certificateKey = (RSAPublicKey) issued.leaf.getPublicKey()
        RSAPrivateKey privateKey = (RSAPrivateKey) issued.keyPair.getPrivate()
        if (certificateKey.getModulus() != privateKey.getModulus()) {
            throw new CertificateValidationException("keyMatchesCertificate",
                    "the private key does not belong to the issued certificate")
        }
    }

    static void requireSubjectAlternativeName(IssuedCertificate issued, String wildcard) {
        List<String> dnsNames = readDnsNames(issued.leaf)
        if (!dnsNames.contains(wildcard)) {
            throw new CertificateValidationException("subjectAlternativeName",
                    "expected the literal name ${wildcard} but the certificate covers ${dnsNames}")
        }
    }

    static List<String> readDnsNames(X509Certificate certificate) {
        Collection<List<?>> names = certificate.getSubjectAlternativeNames()
        if (!names) {
            return []
        }
        return names.findAll { it[0] == SAN_TYPE_DNS }.collect { it[1] as String }
    }
}
