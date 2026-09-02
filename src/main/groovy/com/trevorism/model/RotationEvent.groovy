package com.trevorism.model

import java.time.Instant

class RotationEvent {

    String state
    String at
    String detail

    RotationEvent() {
    }

    RotationEvent(RotationState state, String detail) {
        this.state = state.name()
        this.at = Instant.now().toString()
        this.detail = detail
    }
}
