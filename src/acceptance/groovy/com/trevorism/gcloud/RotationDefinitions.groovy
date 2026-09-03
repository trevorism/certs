package com.trevorism.gcloud

import com.trevorism.CertsWorld

import java.time.Instant

this.metaClass.mixin(io.cucumber.groovy.Hooks)
this.metaClass.mixin(io.cucumber.groovy.EN)

When(~/^I list the rotation runs$/) { ->
    listRotationRuns()
}

Given(~/^the first rotation run$/) { ->
    assert listRotationRuns(), "no rotation run has been recorded to read"
    firstRotationRun()
}

When(~/^I read that run by id$/) { ->
    String requested = rotationRun.id as String
    readRotationRun(requested)
    assert rotationRun.id == requested
}

Then(~/^at least one rotation run is recorded$/) { ->
    assert rotationRuns, "the certs service has no rotation history at all"
}

Then(~/^every run names a wildcard, a gcp project and an outcome$/) { ->
    rotationRuns.each { Map run ->
        assert run.id, "a rotation run has no id"
        assert run.wildcard, "rotation run ${run.id} has no wildcard"
        assert run.gcpProject, "rotation run ${run.id} has no gcpProject"
        assert run.outcome, "rotation run ${run.id} has no outcome"
        assert run.startedAt, "rotation run ${run.id} has no start time"
    }
}

Then(~/^the run returned is the one I asked for$/) { ->
    assert rotationRun.wildcard
    assert rotationRun.outcome
    assert rotationRun.events, "rotation run ${rotationRun.id} recorded no states"
}

Then(~/^every recorded state carries a timestamp$/) { ->
    rotationRun.events.each { Map event ->
        assert event.state, "a recorded state on run ${rotationRun.id} has no name"
        assert event.at, "state ${event.state} on run ${rotationRun.id} has no timestamp"
    }
}

Then(~/^the recorded states are in chronological order$/) { ->
    List<Instant> timestamps = CertsWorld.eventTimestamps(rotationRun)
    assert timestamps == timestamps.sort(false),
            "the states on run ${rotationRun.id} are not in the order they happened"
    if (rotationRun.finishedAt) {
        assert !timestamps.last().isAfter(Instant.parse(rotationRun.finishedAt as String)),
                "run ${rotationRun.id} recorded a state after it finished"
    }
}

Then(~/^every completed run records the serial and expiry it issued$/) { ->
    rotationRuns.findAll { it.outcome == "COMPLETED" }.each { Map run ->
        if (run.events?.any { it.state == "UPLOADED" }) {
            assert run.newSerial, "completed run ${run.id} uploaded material but recorded no serial"
            assert run.newNotAfter, "completed run ${run.id} uploaded material but recorded no expiry"
        }
        assert run.finishedAt, "completed run ${run.id} never recorded a finish time"
    }
}

Then(~/^every failed run records a failure detail$/) { ->
    rotationRuns.findAll { it.outcome == "FAILED" }.each { Map run ->
        assert run.failureDetail, "failed run ${run.id} does not say why it failed"
        assert run.events?.any { it.state == "FAILED" },
                "failed run ${run.id} has no FAILED state in its audit trail"
    }
}

Then(~/^every certificate that names a last rotation agrees with that run$/) { ->
    Map runsById = rotationRuns.collectEntries { [(it.id as String): it] }
    certificates.findAll { it.lastRotationRunId }.each { Map certificate ->
        Map run = runsById[certificate.lastRotationRunId as String]
        assert run, "${certificate.wildcard} names rotation run ${certificate.lastRotationRunId}, which is not listed"
        assert run.wildcard == certificate.wildcard,
                "rotation run ${run.id} rotated ${run.wildcard}, not ${certificate.wildcard}"
        assert certificate.lastOutcome == run.outcome,
                "${certificate.wildcard} reports ${certificate.lastOutcome} but run ${run.id} says ${run.outcome}"
        if (run.newSerial) {
            assert certificate.serial == run.newSerial,
                    "${certificate.wildcard} serves serial ${certificate.serial} but run ${run.id} issued ${run.newSerial}"
        }
    }
}
