package com.trevorism.gcloud

import com.trevorism.CertsWorld

this.metaClass.mixin(io.cucumber.groovy.Hooks)
this.metaClass.mixin(io.cucumber.groovy.EN)

When(~/^I GET "(.*)" anonymously$/) { String path ->
    anonGet(path)
}

When(~/^I anonymously POST a registration with no fields$/) { ->
    anonPost("api/certificate", incompleteRegistration())
}

When(~/^I anonymously POST a rotation of an unknown certificate$/) { ->
    anonPost("api/certificate/${CertsWorld.UNKNOWN_ID}/rotate", unknownCertificateRotation())
}

Then(~/^the request is rejected$/) { ->
    assert rejected, "an anonymous caller was served: ${body}"
}

Then(~/^the request is allowed$/) { ->
    assert !rejected, "a publicly available endpoint rejected an anonymous caller"
    assert body
}

Then(~/^the response body is "(.*)"$/) { String expected ->
    assert !rejected, "a publicly available endpoint rejected an anonymous caller"
    assert body?.trim() == expected
}
