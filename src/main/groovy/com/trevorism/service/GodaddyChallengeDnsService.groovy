package com.trevorism.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trevorism.https.AppClientSecureHttpClient
import com.trevorism.https.SecureHttpClient
import com.trevorism.model.DnsRecord
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.reflect.Type

@Singleton
class GodaddyChallengeDnsService implements ChallengeDnsService {

    static final int CHALLENGE_TTL = 600
    static final String TXT = "TXT"

    private static final Logger log = LoggerFactory.getLogger(GodaddyChallengeDnsService)
    private static final String BASE_URL = "https://godaddy.project.trevorism.com/record"
    private static final Type RECORD_LIST = new TypeToken<List<DnsRecord>>() {}.getType()

    private SecureHttpClient secureHttpClient = new AppClientSecureHttpClient()
    private Gson gson = new Gson()

    @Override
    void setChallenge(String label, String digest) {
        clearChallenge(label)
        DnsRecord record = new DnsRecord(name: label, type: TXT, data: digest, ttl: CHALLENGE_TTL)
        log.info("Creating TXT challenge record at ${label}")
        secureHttpClient.post(BASE_URL, gson.toJson(record))
        confirmChallenge(label, digest)
    }

    @Override
    void clearChallenge(String label) {
        log.info("Removing any TXT records at ${label}")
        secureHttpClient.delete("${BASE_URL}/${TXT}/${label}")
    }

    @Override
    List<DnsRecord> readChallenge(String label) {
        String response = secureHttpClient.get("${BASE_URL}/${TXT}/${label}")
        List<DnsRecord> records = gson.fromJson(response, RECORD_LIST)
        return records ?: []
    }

    private void confirmChallenge(String label, String digest) {
        List<DnsRecord> records = readChallenge(label)
        if (records.size() != 1) {
            throw new IllegalStateException("Expected exactly one TXT record at ${label} but found ${records.size()}")
        }
        DnsRecord written = records.first()
        if (written.name != label) {
            throw new IllegalStateException("Expected the TXT record to be written at ${label} but it landed at ${written.name}")
        }
        if (written.data != digest) {
            throw new IllegalStateException("The TXT record at ${label} does not hold the expected challenge value")
        }
    }
}
