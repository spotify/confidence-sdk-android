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
     * @param skipFetch if true, the context will not initiate a new fetch.
     */
    fun putContext(context: Map<String, ConfidenceValue>, skipFetch: Boolean = false)

    /**
     * Add entry to context
     * @param key key of the entry.
     * @param value value of the entry.
     * @param skipFetch if true, the context will not initiate a new fetch.
     */
    fun putContext(key: String, value: ConfidenceValue, skipFetch: Boolean = false)

    /**
     * Remove entry from context
     * @param key key of the context to be removed.
     * @param skipFetch if true, the context will not initiate a new fetch.
     */
    fun removeContext(key: String, skipFetch: Boolean = false)
}