package com.whaleup.gameshub.data

import org.json.JSONObject

interface CatalogLoaderCallback {
    fun onSuccess(catalog: HubCatalog)
    fun onError(error: Exception)
}

object CatalogLoader {

    fun loadFromNetwork(callback: CatalogLoaderCallback) {
        com.whaleup.gameshub.network.APIBridge.getConfig(object : com.whaleup.gameshub.network.APICallback {
            override fun onSuccess(response: String) {
                try {
                    val jsonObject = JSONObject(response)
                    val catalog = HubCatalog.fromJson(jsonObject)
                    BiomeState.setBonusConfig(catalog.bonusConfig)
                    CatalogCache.set(catalog)
                    callback.onSuccess(CatalogCache.get() ?: catalog)
                } catch (e: Exception) {
                    callback.onError(e)
                }
            }

            override fun onError(code: Int, message: String) {
                callback.onError(Exception(message))
            }
        })
    }
}
