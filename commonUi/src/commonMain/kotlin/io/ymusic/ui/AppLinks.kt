package io.ymusic.ui

import io.ymusic.remoteconfig.RemoteConfigData

/** The app's links and contact address: the remote config's value when it sends one, else the built-in one. */
internal class AppLinks(config: RemoteConfigData?) {
    val website: String = config?.features?.websiteUrl ?: "https://bagja.dev"
    val telegram: String = config?.features?.telegramUrl ?: "https://bagja.dev"
    val feedbackEmail: String = config?.features?.feedbackEmail ?: "ganjarboy033@gmail.com"
    val feedbackIssue: String = config?.features?.feedbackIssueUrl ?: "https://github.com/Gandzar/CuteFetcher/issues/new"
    val sourceCode: String = config?.features?.sourceCodeUrl ?: "https://github.com/Gandzar/CuteFetcher"
    val terms: String = config?.features?.termsUrl ?: "https://github.com/Gandzar/CuteFetcher/blob/main/TERMS.md"
    val changelog: String = config?.features?.changelogUrl ?: "https://github.com/Gandzar/CuteFetcher/blob/main/CHANGELOG.md"
}
