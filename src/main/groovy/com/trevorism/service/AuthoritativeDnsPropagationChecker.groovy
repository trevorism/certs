package com.trevorism.service

import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Lookup
import org.xbill.DNS.Message
import org.xbill.DNS.NSRecord
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.TXTRecord
import org.xbill.DNS.Type

import java.time.Duration

@Singleton
class AuthoritativeDnsPropagationChecker implements PropagationChecker {

    static final String ZONE = "trevorism.com"

    private static final Logger log = LoggerFactory.getLogger(AuthoritativeDnsPropagationChecker)

    long settleMillis = 5000
    long pollIntervalMillis = 10000
    long timeoutMillis = 240000
    int requiredCleanRounds = 4
    int queryTimeoutSeconds = 5

    @Override
    void awaitPropagation(String fqdn, String expectedValue) {
        List<String> nameservers = resolveAuthoritativeNameserverAddresses(ZONE)
        if (!nameservers) {
            throw new IllegalStateException("Unable to resolve any authoritative nameserver for ${ZONE}")
        }
        log.info("Waiting for ${fqdn} on authoritative nameservers ${nameservers}")

        pause(settleMillis)
        long deadline = System.currentTimeMillis() + timeoutMillis
        int consecutiveCleanRounds = 0

        while (System.currentTimeMillis() < deadline) {
            consecutiveCleanRounds = isRoundClean(nameservers, fqdn, expectedValue) ? consecutiveCleanRounds + 1 : 0
            if (consecutiveCleanRounds >= requiredCleanRounds) {
                log.info("${fqdn} is visible on every authoritative nameserver")
                return
            }
            pause(pollIntervalMillis)
        }
        throw new IllegalStateException(
                "${fqdn} did not reach every authoritative nameserver within ${timeoutMillis / 1000} seconds")
    }

    boolean isRoundClean(List<String> nameservers, String fqdn, String expectedValue) {
        return nameservers.every { servesExpectedValue(it, fqdn, expectedValue) }
    }

    boolean servesExpectedValue(String nameserverAddress, String fqdn, String expectedValue) {
        try {
            return queryTxtValues(nameserverAddress, fqdn).contains(expectedValue)
        } catch (Exception e) {
            log.warn("Unable to query ${nameserverAddress} for ${fqdn}: ${e.message}")
            return false
        }
    }

    List<String> queryTxtValues(String nameserverAddress, String fqdn) {
        SimpleResolver resolver = new SimpleResolver(nameserverAddress)
        resolver.setTimeout(Duration.ofSeconds(queryTimeoutSeconds))
        Record question = Record.newRecord(Name.fromString("${fqdn}."), Type.TXT, DClass.IN)
        Message query = Message.newQuery(question)
        query.getHeader().unsetFlag(Flags.RD)
        return readTxtValues(resolver.send(query))
    }

    static List<String> readTxtValues(Message response) {
        return response.getSection(Section.ANSWER)
                .findAll { it.getType() == Type.TXT }
                .collectMany { ((TXTRecord) it).getStrings() }
    }

    List<String> resolveAuthoritativeNameserverAddresses(String zone) {
        List<String> hostnames = resolveNameserverHostnames(zone)
        if (!hostnames) {
            return []
        }
        Map<String, List<String>> addressesByHostname = hostnames.collectEntries { [(it): resolveAddresses(it)] }
        List<String> unresolved = addressesByHostname.findAll { !it.value }.keySet().toList()
        if (unresolved) {
            throw new IllegalStateException(
                    "Cannot resolve an address for the authoritative nameserver(s) ${unresolved} of ${zone}; " +
                            "checking propagation against the remaining ${hostnames.size() - unresolved.size()} would be incomplete")
        }
        return addressesByHostname.values().flatten().unique() as List<String>
    }

    List<String> resolveNameserverHostnames(String zone) {
        Record[] records = new Lookup(zone, Type.NS).run()
        return records?.collect { ((NSRecord) it).getTarget().toString(true) } ?: []
    }

    List<String> resolveAddresses(String hostname) {
        return resolveIpv4Addresses(hostname) + resolveIpv6Addresses(hostname)
    }

    List<String> resolveIpv4Addresses(String hostname) {
        Record[] records = new Lookup(hostname, Type.A).run()
        return records?.collect { ((ARecord) it).getAddress().getHostAddress() } ?: []
    }

    List<String> resolveIpv6Addresses(String hostname) {
        Record[] records = new Lookup(hostname, Type.AAAA).run()
        return records?.collect { ((AAAARecord) it).getAddress().getHostAddress() } ?: []
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            throw new IllegalStateException("Interrupted while waiting for dns propagation", e)
        }
    }
}
