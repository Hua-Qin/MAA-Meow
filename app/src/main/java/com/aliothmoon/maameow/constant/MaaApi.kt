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

    // GitHub 资源直链
    const val GITHUB_RESOURCE =
        "https://github.com/MaaAssistantArknights/MaaResource/archive/refs/heads/main.zip"

    // ==================== App 更新 ====================

    // GitHub 仓库
    const val APP_GITHUB_OWNER = "Aliothmoon"
    const val APP_GITHUB_REPO = "MAA-Meow"

    // GitHub Release API (包含 prerelease)
    const val APP_GITHUB_RELEASES =
        "https://api.github.com/repos/$APP_GITHUB_OWNER/$APP_GITHUB_REPO/releases?per_page=1"

    const val APP_GITHUB_RELEASES_BETA =
        "https://api.github.com/repos/$APP_GITHUB_OWNER/$APP_GITHUB_REPO/releases?per_page=5"

    fun appGitHubReleaseByTag(tag: String): String =
        "https://api.github.com/repos/$APP_GITHUB_OWNER/$APP_GITHUB_REPO/releases/tags/$tag"

    // MirrorChyan App 更新源
    const val MIRROR_CHYAN_APP_RESOURCE = "https://mirrorchyan.com/api/resources/MAA-Meow/latest"

    const val BASE_SCHEDULING_SCHEMA =
        "https://maa.plus/docs/zh-cn/protocol/base-scheduling-schema.html"

}