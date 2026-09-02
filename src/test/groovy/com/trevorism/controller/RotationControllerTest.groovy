package com.trevorism.controller

import com.trevorism.model.RotationRun
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

    private RotationController controller = new RotationController(rotationRunService)

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
}
