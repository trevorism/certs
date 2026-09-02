package com.trevorism.crypto

class CertificateValidationException extends RuntimeException {

    final String assertionName

    CertificateValidationException(String assertionName, String message) {
        super("${assertionName}: ${message}")
        this.assertionName = assertionName
    }
}
