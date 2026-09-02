package com.trevorism.service

interface Dns01ChallengeHandler {

    void publish(String recordName, String digest)

    void cleanup(String recordName)
}
