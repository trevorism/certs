package com.trevorism.model

class RotationRun {

    String id
    String certificateId
    String wildcard
    String gcpProject
    String acmeServer
    String startedAt
    String finishedAt
    String outcome
    String failureDetail
    String newSerial
    String newNotAfter
    List<RotationEvent> events = []
}
