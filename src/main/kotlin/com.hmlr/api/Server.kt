package com.hmlr.api

import com.hmlr.api.listener.EventListenerRPC
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import kotlin.concurrent.thread


/**
 * Our Spring Boot application.
 */
@SpringBootApplication
open class Server

/**
 * Starts our Spring Boot application.
 */
fun main(args: Array<String>) {
    //Check required env vars exist
    require(System.getenv("CONFIG_RPC_HOST") != null) { "CONFIG_RPC_HOST env var was not set." }
    require(System.getenv("CONFIG_RPC_PORT") != null) { "CONFIG_RPC_PORT env var was not set." }
    require(System.getenv("CONFIG_RPC_USERNAME") != null) { "CONFIG_RPC_USERNAME env var was not set." }
    require(System.getenv("CONFIG_RPC_PASSWORD") != null) { "CONFIG_RPC_PASSWORD env var was not set." }

    //Run main api
    thread(start=true, name="API") {
        SpringApplication.run(Server::class.java, *args)
    }

    //Run event listener in parallel
    thread(start=true, name="Listener") {
        EventListenerRPC().run()
    }
}