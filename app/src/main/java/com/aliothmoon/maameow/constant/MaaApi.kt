package com.aliothmoon.maameow.constant

object MaaApi {
    // 主 API 服务器
    const val MAA_API = "https://api.maa.plus/MaaAssistantArknights/api/"

    // 备用 API 服务器
    const val MAA_API_BACKUP = "https://api2.maa.plus/MaaAssistantArknights/api/"

    // ==================== 验证平台配置 ====================

    // 验证平台基础地址
    const val VALIDATION_BASE_URL = "https://validate.stct-cnfl.cn/api.php"

    // APP ID
    const val VALIDATION_APP_ID = "10003"

    // APP KEY
    const val VALIDATION_APP_KEY = "cXCfGImNMNRNMPet"

    // RC4 秘钥
    const val VALIDATION_RC4_KEY = "i60QpQQiQ2i10003"

    // 活动关卡 API 路径
    const val STAGE_ACTIVITY_API = "gui/StageActivityV2.json"

    // 任务配置 API 路径
    const val TASKS_API = "resource/tasks.json"

    /**
     * 获取全球服 tasks.json API 路径
     */
    fun getGlobalTasksApi(clientType: String): String {
        return "resource/global/${clientType}/resource/tasks.json"
    }

    val API_URLS = listOf(
        MAA_API,
        MAA_API_BACKUP
    )


    // MirrorChyan 基础地址
    const val MIRROR_CHYAN_BASE = "https://mirrorchyan.com/"

    // MirrorChyan 更新源
    const val MIRROR_CHYAN_RESOURCE = "https://mirrorchyan.com/api/resources/MaaResource/latest"

    // ==================== App 更新 ====================

    // MirrorChyan App 更新源
    const val MIRROR_CHYAN_APP_RESOURCE = "https://mirrorchyan.com/api/resources/MAA-Meow/latest"

    const val BASE_SCHEDULING_SCHEMA =
        "https://maa.plus/docs/zh-cn/protocol/base-scheduling-schema.html"

}