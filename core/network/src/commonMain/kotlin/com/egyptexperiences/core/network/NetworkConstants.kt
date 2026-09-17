package com.egyptexperiences.core.network

object NetworkConstants {
    /**
     * Content locale. The backend resolves it from `Accept-Language` and falls
     * back to Arabic when unspecified, so this header is what decides whether a
     * trip's title comes back in Arabic or English.
     */
    const val HEADER_ACCEPT_LANGUAGE = "Accept-Language"

    const val REQUEST_TIMEOUT_MILLIS = 30_000L
    const val CONNECT_TIMEOUT_MILLIS = 15_000L
}
