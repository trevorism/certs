package com.trevorism.service

import com.trevorism.model.AcmeAccountRecord

interface AcmeAccountStore {

    AcmeAccountRecord load(String server)

    AcmeAccountRecord store(AcmeAccountRecord record)
}
