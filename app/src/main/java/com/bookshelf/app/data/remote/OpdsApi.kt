package com.bookshelf.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface OpdsApi {
    
    @GET
    suspend fun getFeed(@Url url: String): String
    
    companion object {
        fun create(baseUrl: String = ""): OpdsApi {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            
            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
            
            return Retrofit.Builder()
                .baseUrl(baseUrl.ifEmpty { "http://temp.uri/" })
                .client(client)
                .build()
                .create(OpdsApi::class.java)
        }
    }
}
