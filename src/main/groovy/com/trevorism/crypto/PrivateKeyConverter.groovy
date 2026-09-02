package com.trevorism.crypto

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter

import java.security.PrivateKey

class PrivateKeyConverter {

    static final String PKCS1_HEADER = "-----BEGIN RSA PRIVATE KEY-----"

    static String toPkcs1Pem(PrivateKey privateKey) {
        PrivateKeyInfo info = PrivateKeyInfo.getInstance(privateKey.getEncoded())
        ASN1Encodable encodable = info.parsePrivateKey()
        byte[] der = encodable.toASN1Primitive().getEncoded()
        StringWriter stringWriter = new StringWriter()
        PemWriter pemWriter = new PemWriter(stringWriter)
        try {
            pemWriter.writeObject(new PemObject("RSA PRIVATE KEY", der))
        } finally {
            pemWriter.close()
        }
        return stringWriter.toString()
    }
}
