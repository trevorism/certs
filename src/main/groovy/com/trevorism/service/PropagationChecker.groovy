package com.trevorism.service

interface PropagationChecker {

    void awaitPropagation(String fqdn, String expectedValue)
}
