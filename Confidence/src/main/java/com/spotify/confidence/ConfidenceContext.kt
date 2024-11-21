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
     * Add entry to context
     * @param context context to add.
     */
    suspend fun putContext(context: Map<String, ConfidenceValue>)

    /**
     * Add entry to context
     * @param key key of the entry.
     * @param value value of the entry.
     */
    suspend fun putContext(key: String, value: ConfidenceValue)

    /**
     * Remove entry from context
     * @param key key of the context to be removed.
     */
    suspend fun removeContext(key: String)

    /**
     * Add entry to context
     * @param context context to add.
     */
    fun putContextSync(context: Map<String, ConfidenceValue>)

    /**
     * Add entry to context
     * @param key key of the entry.
     * @param value value of the entry.
     */
    fun putContextSync(key: String, value: ConfidenceValue)

    /**
     * Remove entry from context
     * @param key key of the context to be removed.
     */
    fun removeContextSync(key: String)
}