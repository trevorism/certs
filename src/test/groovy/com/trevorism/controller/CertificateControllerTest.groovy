package com.trevorism.controller

import com.trevorism.model.ManagedCertificate
import com.trevorism.model.RotationRequest
import com.trevorism.model.RotationRun
import com.trevorism.service.CertificateRotationService
import com.trevorism.service.ManagedCertificateService
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull

class CertificateControllerTest {

    private ManagedCertificate certificate = new ManagedCertificate(id: "cert-1", category: "action")
    private RotationRequest capturedRequest

    private ManagedCertificateService certificateService = [
            list  : { [certificate] },
            get   : { String id -> id == "cert-1" ? certificate : null },
            create: { ManagedCertificate c -> c.id = "created"; c },
            update: { String id, ManagedCertificate c -> c }
    ] as ManagedCertificateService

    private CertificateRotationService rotationService = [
            rotate: { String id, RotationRequest request ->
                capturedRequest = request
                new RotationRun(id: "run-1", certificateId: id, outcome: "COMPLETED")
            }
    ] as CertificateRotationService

    private CertificateController controller = new CertificateController(certificateService, rotationService)

    @Test
    void testListReturnsEveryCertificate() {
        assertEquals(1, controller.list().size())
    }

    @Test
    void testGetReturnsOneCertificate() {
        assertEquals("cert-1", controller.get("cert-1").id)
    }

    @Test
    void testGetReturnsNothingForAnUnknownId() {
        assertEquals(null, controller.get("missing"))
    }

    @Test
    void testCreateRegistersACertificate() {
        assertEquals("created", controller.create(new ManagedCertificate(category: "draw")).id)
    }

    @Test
    void testRotateDelegatesTheRequest() {
        RotationRun run = controller.rotate("cert-1", new RotationRequest(force: true))
        assertEquals("run-1", run.id)
        assertEquals("cert-1", run.certificateId)
        assertEquals(true, capturedRequest.force)
    }

    @Test
    void testRotateWithoutABodyUsesTheDefaults() {
        controller.rotate("cert-1", null)
        assertNotNull(capturedRequest)
        assertEquals(RotationRequest.PRODUCTION, capturedRequest.acmeServer)
        assertEquals(false, capturedRequest.force)
    }
}
