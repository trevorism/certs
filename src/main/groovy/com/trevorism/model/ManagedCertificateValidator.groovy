package com.trevorism.model

import java.time.Instant

class ManagedCertificateValidator {

    static final String ZONE = "trevorism.com"
    static final String CATEGORY_PATTERN = "[a-z0-9][a-z0-9-]*"

    static void validateForRegistration(ManagedCertificate certificate) {
        if (!certificate) {
            throw new IllegalArgumentException("A managed certificate is required")
        }
        requireText(certificate.category, "category")
        requireText(certificate.gcpProject, "gcpProject")
        requireText(certificate.probeHost, "probeHost")
        requireText(certificate.appEngineCertificateId, "appEngineCertificateId")

        if (!(certificate.category ==~ CATEGORY_PATTERN)) {
            throw new IllegalArgumentException(
                    "The category '${certificate.category}' must match ${CATEGORY_PATTERN}; it becomes a dns label")
        }
        requireMatchingWildcard(certificate)
        requireProbeHostUnderWildcard(certificate)
        requireParseableInstant(certificate.notAfter, "notAfter")
        requireParseableInstant(certificate.lastRotatedAt, "lastRotatedAt")
    }

    static String expectedWildcard(ManagedCertificate certificate) {
        return "*.${certificate.category}.${ZONE}"
    }

    private static void requireMatchingWildcard(ManagedCertificate certificate) {
        String expected = expectedWildcard(certificate)
        if (certificate.wildcard != expected) {
            throw new IllegalArgumentException(
                    "The wildcard must be ${expected} for category '${certificate.category}' but was '${certificate.wildcard}'")
        }
    }

    private static void requireProbeHostUnderWildcard(ManagedCertificate certificate) {
        String suffix = ".${certificate.category}.${ZONE}"
        String label = certificate.probeHost.endsWith(suffix) ?
                certificate.probeHost - suffix : null
        if (!label || label.contains(".")) {
            throw new IllegalArgumentException(
                    "The probeHost '${certificate.probeHost}' is not a single label under ${expectedWildcard(certificate)}, " +
                            "so the wildcard would not cover it")
        }
    }

    private static void requireText(String value, String field) {
        if (!value?.trim()) {
            throw new IllegalArgumentException("The ${field} is required to register a managed certificate")
        }
    }

    private static void requireParseableInstant(String value, String field) {
        if (!value) {
            return
        }
        try {
            Instant.parse(value)
        } catch (Exception e) {
            throw new IllegalArgumentException("The ${field} '${value}' is not an ISO-8601 instant")
        }
    }
}
