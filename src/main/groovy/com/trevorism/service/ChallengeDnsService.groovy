package com.trevorism.service

import com.trevorism.model.DnsRecord

interface ChallengeDnsService {

    void setChallenge(String label, String digest)

    void clearChallenge(String label)

    List<DnsRecord> readChallenge(String label)
}
