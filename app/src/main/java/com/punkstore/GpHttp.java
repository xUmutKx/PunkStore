package com.punkstore;

import com.aurora.gplayapi.network.DefaultHttpClient;
import com.aurora.gplayapi.network.IHttpClient;

/** gplayapi'nin DefaultHttpClient'ı Kotlin'de "internal"; Java üzerinden erişilir. */
public final class GpHttp {
    private GpHttp() {}
    public static IHttpClient get() { return DefaultHttpClient.INSTANCE; }
}
