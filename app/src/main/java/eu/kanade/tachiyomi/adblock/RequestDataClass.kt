package eu.kanade.tachiyomi.adblock

enum class RequestDisposition {
    Default,
    Allowed,
    Blocked,
}

class RequestDataClass(
    val requestUrlString: String,
    val requestUrlWithSeparatorsString: String,
    val truncatedRequestUrlString: String,
    val truncatedRequestUrlWithSeparatorsString: String,
    val firstPartyHostString: String,
    val isThirdPartyRequest: Boolean,
)
