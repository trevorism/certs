package com.trevorism.gcloud

import com.trevorism.CertsWorld

this.metaClass.mixin(io.cucumber.groovy.Hooks)
this.metaClass.mixin(io.cucumber.groovy.EN)

World {
    new CertsWorld()
}

When(~/^I list the managed certificates$/) { ->
    listCertificates()
}

Given(~/^the first managed certificate$/) { ->
    assert listCertificates(), "no managed certificate is registered to read"
    firstCertificate()
}

When(~/^I read that certificate by id$/) { ->
    String requested = certificate.id as String
    readCertificate(requested)
    assert certificate.id == requested
}

When(~/^I verify that certificate$/) { ->
    verifyCertificate(certificate.id as String)
}

Then(~/^at least one certificate is managed$/) { ->
    assert certificates, "the certs service is managing no certificates at all"
}

Then(~/^every certificate names a wildcard, a gcp project and an app engine certificate$/) { ->
    certificates.each { Map certificate ->
        assert certificate.id, "a managed certificate has no id"
        assert certificate.wildcard, "a managed certificate has no wildcard"
        assert certificate.category, "${certificate.wildcard} has no category"
        assert certificate.gcpProject, "${certificate.wildcard} has no gcpProject"
        assert certificate.appEngineCertificateId, "${certificate.wildcard} has no appEngineCertificateId"
        assert certificate.probeHost, "${certificate.wildcard} has no probeHost"
    }
}

Then(~/^every wildcard is the wildcard for its category$/) { ->
    certificates.each { Map certificate ->
        assert certificate.wildcard == CertsWorld.expectedWildcard(certificate),
                "${certificate.wildcard} does not match category ${certificate.category}"
    }
}

Then(~/^every probe host is a single label under its wildcard$/) { ->
    certificates.each { Map certificate ->
        assert CertsWorld.coveredByWildcard(certificate),
                "${certificate.probeHost} is not covered by ${certificate.wildcard}"
    }
}

Then(~/^every challenge fqdn is the acme challenge record for its category$/) { ->
    certificates.each { Map certificate ->
        assert certificate.challengeFqdn == CertsWorld.expectedChallengeFqdn(certificate),
                "${certificate.challengeFqdn} is not the challenge record for ${certificate.category}"
    }
}

Then(~/^the certificate returned is the one I asked for$/) { ->
    assert certificate.wildcard
    assert certificate.wildcard == CertsWorld.expectedWildcard(certificate)
    assert certificate.probeHost
}

Then(~/^the verification is about that certificate$/) { ->
    assert verification.certificateId == certificate.id
    assert verification.wildcard == certificate.wildcard
    assert verification.probeHost == certificate.probeHost
}

Then(~/^the verification explains what the edge is serving$/) { ->
    assert verification.checkedAt, "the verification did not record when it checked"
    assert verification.detail, "the verification did not explain what it found"
    if (!certificate.serial) {
        assert !verification.matches, "a certificate with no issued serial cannot match the edge"
    }
}
