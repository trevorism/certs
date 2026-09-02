package com.trevorism.controller

import com.trevorism.model.RotationRun
import com.trevorism.model.SweepResult
import com.trevorism.service.CertificateSweepService
import com.trevorism.service.RotationRunService
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

class RotationControllerTest {

    private RotationRun run = new RotationRun(id: "run-1", outcome: "COMPLETED")

    private RotationRunService rotationRunService = [
            list  : { [run] },
            get   : { String id -> id == "run-1" ? run : null },
            create: { RotationRun r -> r },
            update: { String id, RotationRun r -> r }
    ] as RotationRunService

    private CertificateSweepService sweepService = [
            sweep: { new SweepResult(enabledCount: 8, dueCount: 1, rotatedWildcard: "*.draw.trevorism.com") }
    ] as CertificateSweepService

    private RotationController controller = new RotationController(rotationRunService, sweepService)

    @Test
    void testListReturnsEveryRun() {
        assertEquals(1, controller.list().size())
    }

    @Test
    void testGetReturnsOneRun() {
        assertEquals("COMPLETED", controller.get("run-1").outcome)
    }

    @Test
    void testGetReturnsNothingForAnUnknownId() {
        assertEquals(null, controller.get("missing"))
    }

    @Test
    void testSweepDelegatesToTheSweepService() {
        SweepResult result = controller.sweep()
        assertEquals(8, result.enabledCount)
        assertEquals("*.draw.trevorism.com", result.rotatedWildcard)
    }
}
