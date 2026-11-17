package com.vivid.app.data.storage

/** HTTP status is retained for recovery; never expose tokens or signed URLs in the UI. */
class StorageRequestException(val statusCode: Int) : java.io.IOException("Storage request failed ($statusCode)")
