package com.anhourtec.camgrid.client.page

/**
 * Parses the device tree on `/home/space`: one `<li id="camera-…">` per device followed by an
 * `<ol class="… channel-list …">` of its channels. See docs/api.md.
 */
object DeviceListParser {
    private const val GROUP_CHANNELS = 3
    private val deviceBlock = Regex("""<li\s+id="camera-(\d+)"(.*?)</li>\s*(<ol[^>]*channel-list.*?</ol>)?""", RegexOption.DOT_MATCHES_ALL)
    private val status = Regex("""data-status="(\d+)"""")
    private val title = Regex("""<a\s+href="/home/video/show\?id=\d+"\s+title="([^"]*)"""")
    private val typeName = Regex("""<p>\s*([^<]*?)\s*<img""")
    private val channel = Regex("""<li\s+data-channel="(\d+)"[^>]*>.*?<span[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)

    fun parse(html: String): List<Device> =
        deviceBlock
            .findAll(html)
            .map { m ->
                val body = m.groupValues[2]
                Device(
                    deviceId = m.groupValues[1],
                    name =
                        unescapeHtml(
                            title
                                .find(body)
                                ?.groupValues
                                ?.get(1)
                                .orEmpty(),
                        ),
                    typeName =
                        typeName
                            .find(body)
                            ?.groupValues
                            ?.get(1)
                            .orEmpty(),
                    online = status.find(body)?.groupValues?.get(1) == "1",
                    channels =
                        channel
                            .findAll(m.groupValues[GROUP_CHANNELS])
                            .map { Channel(it.groupValues[1].toInt(), unescapeHtml(it.groupValues[2].trim())) }
                            .toList(),
                )
            }.distinctBy { it.deviceId }
            .toList()

    private val accountName = Regex("""class="[^"]*\bJ-username\b[^"]*"[^>]*>\s*([^<]+?)\s*<""")

    /** The signed-in account name shown in the page header; the gateway registers it (cmd 4101). */
    fun accountName(html: String): String? =
        accountName
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.let(::unescapeHtml)

    /** True if [html] is the login page, i.e. the session has expired. */
    fun isLoginPage(html: String): Boolean = html.contains("ajax-login") || html.contains("name=\"yzm\"")
}
