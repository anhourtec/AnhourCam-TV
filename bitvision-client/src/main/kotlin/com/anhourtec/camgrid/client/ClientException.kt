package com.anhourtec.camgrid.client

/** Base for every error the client reports besides plain network [java.io.IOException]s. */
open class ClientException(
    message: String,
) : Exception(message)
