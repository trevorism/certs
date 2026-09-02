package com.trevorism.model

import java.security.KeyPair
import java.security.cert.X509Certificate

class IssuedCertificate {

    List<X509Certificate> chain
    KeyPair keyPair
    String chainPem

    X509Certificate getLeaf() {
        return chain?.first()
    }

    String getSerial() {
        return leaf?.serialNumber?.toString(16)
    }

    String getNotAfter() {
        return leaf?.notAfter?.toInstant()?.toString()
    }

    String getIssuer() {
        return leaf?.issuerX500Principal?.name
    }
}
