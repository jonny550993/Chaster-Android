package app.chaster.client

import java.io.IOException

/** HTTP error from the Chaster API, including status for background retry decisions. */
internal class ChasterHttpException(
    val statusCode: Int,
    path: String
) : IOException("Chaster API returned HTTP $statusCode for $path")
