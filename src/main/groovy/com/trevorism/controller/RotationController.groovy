package com.trevorism.controller

import com.trevorism.model.RotationRun
import com.trevorism.secure.Permissions
import com.trevorism.secure.Roles
import com.trevorism.secure.Secure
import com.trevorism.service.RotationRunService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Controller("/api/rotation")
class RotationController {

    private final RotationRunService rotationRunService

    RotationController(RotationRunService rotationRunService) {
        this.rotationRunService = rotationRunService
    }

    @Tag(name = "Rotation Operations")
    @Operation(summary = "Lists every rotation run **Secure")
    @Get(value = "/", produces = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.USER, permissions = Permissions.READ)
    List<RotationRun> list() {
        return rotationRunService.list()
    }

    @Tag(name = "Rotation Operations")
    @Operation(summary = "Gets the audit trail for a single rotation run **Secure")
    @Get(value = "/{id}", produces = MediaType.APPLICATION_JSON)
    @Secure(value = Roles.USER, permissions = Permissions.READ)
    RotationRun get(String id) {
        return rotationRunService.get(id)
    }
}
