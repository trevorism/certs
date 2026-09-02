package com.trevorism.service

interface FailureReporter {

    void report(String message, Map details)
}
