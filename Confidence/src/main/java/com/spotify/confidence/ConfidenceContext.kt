package com.spotify.confidence

interface ConfidenceContextProvider {
    /**
     * @return context of given instance, including parent context if applicable.
     */
    fun getContext(): Map<String, ConfidenceValue>
}

typealias ConfidenceFieldsType = Map<String, ConfidenceValue>

interface Contextual : ConfidenceContextProvider {
    /**
     * Create a new Confidence child instance based on an existing Confidence instance
     * @param context additional context.
     */
    fun withContext(context: Map<String, ConfidenceValue>): Contextual

    /**
     * Add entry to context and await the reconciliation
     * @param context context to add.
     */
    suspend fun putContextAndAwait(context: Map<String, ConfidenceValue>)

    /**
     * Add entry to context and await the reconciliation
     * @param key key of the entry.
     * @param value value of the entry.
     */
    suspend fun putContextAndAwait(key: String, value: ConfidenceValue)

    /**
     * Remove entry from context and await the reconciliation
     * @param key key of the context to be removed.
     */
    suspend fun removeContextAndAwait(key: String)

    /**
     * Add entry to context
     * @param context context to add.
     */
    fun putContext(context: Map<String, ConfidenceValue>)

    /**
     * Add entry to context
     * @param key key of the entry.
     * @param value value of the entry.
     */
    fun putContext(key: String, value: ConfidenceValue)

    /**
     * Remove entry from context
     * @param key key of the context to be removed.
     */
    fun removeContext(key: String)
}