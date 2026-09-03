package com.trevorism.controller

import com.trevorism.model.CertificateVerification
import com.trevorism.model.ManagedCertificate
import com.trevorism.model.ManagedCertificateValidator
import com.trevorism.model.RotationRequest
import com.trevorism.model.RotationRun
import com.trevorism.secure.Permissions
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.CertificateRotationService
import com.trevorism.service.CertificateVerifier
import com.trevorism.service.ManagedCertificateService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Controller("/api/certificate")
class CertificateController {

    private static final Logger log = LoggerFactory.getLogger(CertificateController)

    private final ManagedCertificateService managedCertificateService
    private final CertificateRotationService certificateRotationService
    private final CertificateVerifier certificateVerifier

    CertificateController(ManagedCertificateService managedCertificateService,
                          CertificateRotationService certificateRotationService,
                          CertificateVerifier certificateVerifier) {
        this.managedCertificateService = managedCertificateService
        this.certificateRotationService = certificateRotationService
        this.certificateVerifier = certificateVerifier
    }

    @Tag(name = "Certificate Operations")
    @Operation(summary = "Lists every managed wildcard certificate **Secure")
    @Get(value = "/", produces = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.USER, permissions = Permissions.READ)
    List<ManagedCertificate> list() {
        return managedCertificateService.list()
    }

    @Tag(name = "Certificate Operations")
    @Operation(summary = "Gets a single managed wildcard certificate **Secure")
    @Get(value = "/{id}", produces = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.USER, permissions = Permissions.READ)
    ManagedCertificate get(String id) {
        return managedCertificateService.get(id)
    }

    @Tag(name = "Certificate Operations")
    @Operation(summary = "Registers a wildcard certificate for management **Secure")
    @Post(value = "/", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.ADMIN, permissions = Permissions.CREATE)
    ManagedCertificate create(@Body ManagedCertificate certificate) {
        ManagedCertificateValidator.validateForRegistration(certificate)
        return managedCertificateService.create(certificate)
    }

    @Tag(name = "Certificate Operations")
    @Operation(summary = "Checks whether the edge is serving the certificate this service last issued **Secure")
    @Get(value = "/{id}/verify", produces = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.USER, permissions = Permissions.READ)
    CertificateVerification verify(String id) {
        ManagedCertificate certificate = managedCertificateService.get(id)
        if (!certificate) {
            throw new IllegalArgumentException("No managed certificate with id ${id}")
        }
        return certificateVerifier.verify(certificate)
    }

    @Tag(name = "Certificate Operations")
    @Operation(summary = "Rotates one wildcard certificate and replaces it in place on App Engine **Secure")
    @Post(value = "/{id}/rotate", produces = MediaType.APPLICATION_JSON, consumes = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.ADMIN, permissions = Permissions.EXECUTE)
    RotationRun rotate(String id, @Body RotationRequest request) {
        log.info("Rotation requested for certificate ${id}")
        return certificateRotationService.rotate(id, request ?: new RotationRequest())
    }
}
