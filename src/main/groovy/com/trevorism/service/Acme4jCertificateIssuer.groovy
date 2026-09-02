package com.trevorism.service

import com.trevorism.PropertiesProvider
import com.trevorism.crypto.AcmeKeyCipher
import com.trevorism.model.AcmeAccountRecord
import com.trevorism.model.IssuedCertificate
import com.trevorism.model.RotationRequest
import groovy.transform.CompileStatic
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.shredzone.acme4j.AccountBuilder
import org.shredzone.acme4j.Authorization
import org.shredzone.acme4j.Certificate
import org.shredzone.acme4j.Login
import org.shredzone.acme4j.Order
import org.shredzone.acme4j.Session
import org.shredzone.acme4j.Status
import org.shredzone.acme4j.challenge.Dns01Challenge
import org.shredzone.acme4j.util.KeyPairUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.security.KeyPair
import java.time.Duration
import java.time.Instant

@CompileStatic
@Singleton
class Acme4jCertificateIssuer implements CertificateIssuer {

    static final String PRODUCTION_DIRECTORY = "acme://letsencrypt.org"
    static final String STAGING_DIRECTORY = "acme://letsencrypt.org/staging"
    static final String DEFAULT_CONTACT_EMAIL = "tbrooks@trevorism.com"
    static final int KEY_SIZE = 2048

    private static final Logger log = LoggerFactory.getLogger(Acme4jCertificateIssuer)

    @Inject
    AcmeAccountStore acmeAccountStore

    @Inject
    PropertiesProvider propertiesProvider

    Duration authorizationTimeout = Duration.ofMinutes(5)
    Duration orderTimeout = Duration.ofMinutes(5)

    @Override
    IssuedCertificate issue(String wildcard, String acmeServer, Dns01ChallengeHandler challengeHandler) {
        Session session = new Session(directoryFor(acmeServer))
        Login login = loginOrRegister(session, acmeServer)

        Order order = login.newOrder().domain(wildcard).create()
        Authorization authorization = order.getAuthorizations().first()
        Dns01Challenge challenge = findDnsChallenge(authorization)
        String recordName = stripTrailingDot(challenge.getRRName(authorization.getIdentifier()))

        try {
            challengeHandler.publish(recordName, challenge.getDigest())
            challenge.trigger()
            requireValid(authorization.waitForCompletion(authorizationTimeout), "authorization for ${wildcard}")

            KeyPair domainKeyPair = KeyPairUtils.createKeyPair(KEY_SIZE)
            order.execute(domainKeyPair)
            requireValid(order.waitForCompletion(orderTimeout), "order for ${wildcard}")

            return toIssuedCertificate(order.getCertificate(), domainKeyPair)
        } finally {
            challengeHandler.cleanup(recordName)
        }
    }

    static String directoryFor(String acmeServer) {
        return RotationRequest.STAGING.equalsIgnoreCase(acmeServer) ? STAGING_DIRECTORY : PRODUCTION_DIRECTORY
    }

    static String stripTrailingDot(String name) {
        return name?.endsWith(".") ? name[0..-2] : name
    }

    private static Dns01Challenge findDnsChallenge(Authorization authorization) {
        Optional<Dns01Challenge> challenge = authorization.findChallenge(Dns01Challenge)
        if (!challenge.isPresent()) {
            throw new IllegalStateException("The authorization for ${authorization.getIdentifier().getDomain()} offers no dns-01 challenge")
        }
        return challenge.get()
    }

    private static void requireValid(Status status, String what) {
        if (status != Status.VALID) {
            throw new IllegalStateException("The ${what} finished as ${status} instead of VALID")
        }
    }

    private static IssuedCertificate toIssuedCertificate(Certificate certificate, KeyPair keyPair) {
        StringWriter writer = new StringWriter()
        certificate.writeCertificate(writer)
        return new IssuedCertificate(
                chain: certificate.getCertificateChain(),
                keyPair: keyPair,
                chainPem: writer.toString())
    }

    private Login loginOrRegister(Session session, String acmeServer) {
        AcmeAccountRecord record = acmeAccountStore.load(acmeServer)
        if (record) {
            KeyPair keyPair = KeyPairUtils.readKeyPair(new StringReader(cipher().decrypt(record.encryptedKeyPem)))
            return session.login(URI.create(record.accountUrl).toURL(), keyPair)
        }
        return registerAccount(session, acmeServer)
    }

    private Login registerAccount(Session session, String acmeServer) {
        log.info("Registering a new acme account against ${acmeServer}")
        KeyPair keyPair = KeyPairUtils.createKeyPair(KEY_SIZE)
        Login login = new AccountBuilder()
                .agreeToTermsOfService()
                .addEmail(contactEmail())
                .useKeyPair(keyPair)
                .createLogin(session)

        StringWriter writer = new StringWriter()
        KeyPairUtils.writeKeyPair(keyPair, writer)
        acmeAccountStore.store(new AcmeAccountRecord(
                server: acmeServer,
                accountUrl: login.getAccount().getLocation().toString(),
                encryptedKeyPem: cipher().encrypt(writer.toString()),
                createdAt: Instant.now().toString()))
        return login
    }

    private String contactEmail() {
        return propertiesProvider?.getProperty("acmeContactEmail") ?: DEFAULT_CONTACT_EMAIL
    }

    private AcmeKeyCipher cipher() {
        return new AcmeKeyCipher(propertiesProvider.getProperty("encryptionKey"))
    }
}
