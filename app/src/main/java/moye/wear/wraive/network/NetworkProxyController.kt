package moye.wear.wraive.network

import moye.wear.wraive.model.NetworkProxyConfig
import moye.wear.wraive.model.ProxyType
import okhttp3.Authenticator
import okhttp3.Credentials
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

class NetworkProxyController : ProxySelector() {
    @Volatile
    private var config = NetworkProxyConfig()

    val authenticator = Authenticator { _, response ->
        val current = config
        if (
            !current.enabled ||
            current.username.isBlank() ||
            response.request.header("Proxy-Authorization") != null
        ) {
            null
        } else {
            response.request.newBuilder()
                .header("Proxy-Authorization", Credentials.basic(current.username, current.password))
                .build()
        }
    }

    fun update(value: NetworkProxyConfig) {
        config = value
    }

    override fun select(uri: URI): List<Proxy> {
        val current = config
        if (!current.enabled || current.host.isBlank() || shouldBypass(uri.host, current)) {
            return listOf(Proxy.NO_PROXY)
        }
        val type = if (current.type == ProxyType.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
        return listOf(
            Proxy(type, InetSocketAddress.createUnresolved(current.host, current.port))
        )
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit

    private fun shouldBypass(host: String?, config: NetworkProxyConfig): Boolean {
        val target = host?.lowercase() ?: return true
        return config.bypassHosts.orEmpty().any { rule ->
            val normalized = rule.trim().lowercase().removePrefix("*")
            normalized.isNotBlank() && (
                target == normalized.removePrefix(".") || target.endsWith(normalized)
                )
        }
    }
}
