package com.example.confidencedemoapp

object ClientSecretProvider {
    fun clientSecret(): String = BuildConfig.CLIENT_SECRET
}