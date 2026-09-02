package com.trevorism.service

import com.trevorism.crypto.PermissiveTrustManager
import jakarta.inject.Singleton

import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate

@Singleton
class TlsEdgeCertificateInspector implements EdgeCertificateInspector {

    static final int HTTPS_PORT = 443

    int timeoutMillis = 10000

    @Override
    String readSerial(String host) {
        SSLSocket socket = null
        try {
            socket = (SSLSocket) createSocketFactory().createSocket()
            socket.connect(new InetSocketAddress(host, HTTPS_PORT), timeoutMillis)
            socket.setSoTimeout(timeoutMillis)

            SSLParameters parameters = socket.getSSLParameters()
            parameters.setServerNames([new SNIHostName(host)])
            socket.setSSLParameters(parameters)
            socket.startHandshake()

            X509Certificate leaf = (X509Certificate) socket.getSession().getPeerCertificates()[0]
            return leaf.getSerialNumber().toString(16)
        } finally {
            socket?.close()
        }
    }

    private static SSLSocketFactory createSocketFactory() {
        SSLContext context = SSLContext.getInstance("TLS")
        context.init(null, [new PermissiveTrustManager()] as TrustManager[], new SecureRandom())
        return context.getSocketFactory()
    }
}
