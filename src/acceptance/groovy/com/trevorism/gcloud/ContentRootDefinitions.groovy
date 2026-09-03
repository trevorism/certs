package com.trevorism.gcloud

/**
 * @author tbrooks
 */

this.metaClass.mixin(io.cucumber.groovy.Hooks)
this.metaClass.mixin(io.cucumber.groovy.EN)

String baseUrl = System.getenv("ACCEPTANCE_BASE_URL") ?: "https://certs.project.trevorism.com"

def contextRootContent
def pingContent

Given(/the certs application is alive/) { ->
    try {
        new URL("${baseUrl}/api/ping").text
    }
    catch (Exception ignored) {
        Thread.sleep(10000)
        new URL("${baseUrl}/api/ping").text
    }
}

When(/I navigate to the context root/) { ->
    contextRootContent = new URL("${baseUrl}/api").text
}

When(/I navigate to \\/api\\/ping/) { ->
    pingContent = new URL("${baseUrl}/api/ping").text
}

Then(/the API returns a link to the help page/) { ->
    assert contextRootContent
    assert contextRootContent.contains("/api/help")
}

Then(/pong is returned, to indicate the service is alive/) { ->
    assert pingContent == "pong"
}
