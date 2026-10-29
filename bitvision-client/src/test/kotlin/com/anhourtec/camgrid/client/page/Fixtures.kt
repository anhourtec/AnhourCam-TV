package com.anhourtec.camgrid.client.page

/** HTML shaped like the portal's pages (docs/api.md), with fake identifiers. */
object Fixtures {
    const val DEVICE_A = "1000000000001" // check-redaction:allow fake test ID
    const val DEVICE_B = "1000000000002" // check-redaction:allow fake test ID

    fun spacePage(): String =
        """
        <li><a class="J-username" href="/home/user">user@example.com</a></li>
        <ul class="list-unstyled list-block J-list-block list0">
            <li id="camera-$DEVICE_A" data-id="0" data-num="0"
                data-status="1" class="J-tree-node-toggle-button">
                <a href="/home/video/show?id=$DEVICE_A"
                    title="Front &amp; Back">
                    <img src="/statics/home/img/product/NVR.png" class="device-pic">
                    <i class="beyond-ellipsis">Front &amp; Back</i>
                    <p>
                        NVR                                    <img src="/statics/home/img/status1.png">
                    </p>
                </a>
            </li>
            <ol class="list-unstyled channel-list J-channel-list" data-name="Front &amp; Back">
                <li data-channel="0"  channel="1">
                    <em></em>
                    <img src="/statics/home/img/product/thumb/NVR.png" class="channel-pic">
                    <span class="beyond-ellipsis">CH1</span>
                </li>
                <li data-channel="1"  channel="2">
                    <em></em>
                    <span class="beyond-ellipsis">Gate</span>
                </li>
            </ol>
            <li id="camera-$DEVICE_B" data-id="1" data-num="1"
                data-status="0" class="J-tree-node-toggle-button">
                <a href="/home/video/show?id=$DEVICE_B"
                    title="Shop">
                    <i class="beyond-ellipsis">Shop</i>
                    <p>
                        DVR <img src="/statics/home/img/status0.png">
                    </p>
                </a>
            </li>
        </ul>
        """.trimIndent()

    fun devicePage(
        deviceJson: String,
        serviceJson: String,
    ): String =
        """
        <script>
            var tipsDeviceInfo = {
                getUserServiceAddr: '$serviceJson',
                subMenuActive: 'video',
                curDeviceInfo: '$deviceJson'
            };
            var channelNum = "16";
        </script>
        """.trimIndent()

    const val SERVICE_JSON =
        """{"user_gate_url":"wss:\/\/gw.example.com\/userGateWay\/1","ws_stream_url":"wss:\/\/gw.example.com\/stream\/1",""" +
            """"user_gate_way_ip":"192.0.2.10","stream_relay_ip":"192.0.2.11","ws_stream_port":35009}"""

    val DEVICE_JSON =
        """{"id":"7","user_id":"9","device_id":"$DEVICE_A","device_name":"Bob\'s NVR","cate_id":"0",""" +
            """"local_user":"admin","local_pwd":"p\'w\\"d","device_status":"1","device_model":"NVR_16",""" +
            """"device_type_name":"NVR","channel":"16","device_source":1,"extra_field":"ignored"}"""
}
