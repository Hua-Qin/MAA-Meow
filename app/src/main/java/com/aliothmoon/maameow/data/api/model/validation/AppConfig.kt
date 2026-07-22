package com.aliothmoon.maameow.data.api.model.validation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    @SerialName("version") val version: String? = null,
    @SerialName("version_info") val versionInfo: String? = null,
    @SerialName("app_update_show") val appUpdateShow: String? = null,
    @SerialName("app_update_url") val appUpdateUrl: String? = null,
    @SerialName("app_update_pwd") val appUpdatePwd: String? = null,
    @SerialName("Trial_mode") val trialMode: String? = null,
    @SerialName("trial_kami") val trialKami: String? = null,
    @SerialName("app_name") val appName: String? = null,
    @SerialName("app_qq") val appQq: String? = null,
    @SerialName("app_groupchat") val appGroupchat: String? = null,
    @SerialName("app_img") val appImg: String? = null,
    @SerialName("app_background") val appBackground: String? = null,
    @SerialName("app_update_must") val appUpdateMust: String? = null,
    @SerialName("app_switch") val appSwitch: String? = null,
    @SerialName("template") val template: String? = null,
    @SerialName("diy_template") val diyTemplate: String? = null,
    @SerialName("api_total") val apiTotal: String? = null
) {
    fun isForceUpdate(): Boolean = appUpdateMust == "y"
    fun isPaidApp(): Boolean = appSwitch == "y"
    fun isKamiMode(): Boolean = trialMode == "kami"
    fun isTimeMode(): Boolean = trialMode == "time"
    fun isTrialClosed(): Boolean = trialMode == "close"
}