/*
 * Copyright (C) 2010-2024, Danilo Pianini and contributors
 * listed, for each module, in the respective subproject's build.gradle.kts file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */

package graphql.api

import client.ClientConnection
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Optional
import it.unibo.alchemist.boundary.graphql.client.EnvironmentQuery
import it.unibo.alchemist.boundary.graphql.client.EnvironmentSubscription
import it.unibo.alchemist.boundary.graphql.client.NodeQuery
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Provides a set of functions to interact with the environment API.
 */
object EnvironmentApi {

    /**
     * Asynchronously queries a node with the specified ID from the server.
     *
     * @param nodeId the ID of the node to query (default value is 0 if not specified)
     * @return a Deferred object representing the asynchronous result of the node query
     */
    suspend fun nodeQuery(nodeId: Int = 0): Deferred<NodeQuery.Data?> = coroutineScope {
        async {
            ClientConnection.client.query(NodeQuery(id = Optional.present(nodeId))).execute().data
        }
    }

    /**
     * Subscribes to the environment subscription on the server and receives data updates.
     *
     * @return a Flow of ApolloResponse containing the data from the environment subscription
     */
    fun environmentSubscription(): Flow<ApolloResponse<EnvironmentSubscription.Data>> {
        return ClientConnection.client.subscription(EnvironmentSubscription()).toFlow()
    }

    /**
     * Asynchronously queries environment data from the server.
     *
     * @return a Deferred object representing the asynchronous result of the environment query
     */
    suspend fun environmentQuery(): Deferred<EnvironmentQuery.Data?> = coroutineScope {
        async {
            ClientConnection.client.query(EnvironmentQuery()).execute().data
        }
    }
}
