package com.trevorism.crypto

import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

class PermissiveTrustManager implements X509TrustManager {

    @Override
    void checkClientTrusted(X509Certificate[] chain, String authType) {
    }

    @Override
    void checkServerTrusted(X509Certificate[] chain, String authType) {
    }

    @Override
    X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0]
    }
}
