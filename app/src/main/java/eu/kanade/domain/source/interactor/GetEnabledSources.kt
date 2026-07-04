package eu.kanade.domain.source.interactor

import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Pins
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.repository.SourceRepository
import tachiyomi.source.local.isLocal

class GetEnabledSources(
    private val repository: SourceRepository,
    private val preferences: SourcePreferences,
) {

    fun subscribe(): Flow<List<Source>> {
        val lastUsedListFlow = combine(
            preferences.lastUsedSources.changes(),
            preferences.lastUsedSource.changes(),
        ) { lastUsedList, legacy ->
            val baseList = if (lastUsedList.isEmpty() && legacy != -1L) {
                listOf(legacy)
            } else {
                lastUsedList
            }
            baseList.take(MAX_LAST_USED_SOURCES)
        }

        return combine(
            preferences.pinnedSources.changes(),
            preferences.enabledLanguages.changes(),
            preferences.disabledSources.changes(),
            lastUsedListFlow,
            repository.getSources(),
        ) { pinnedSourceIds, enabledLanguages, disabledSources, rawLastUsedList, sources ->
            val sourceIds = sources.mapTo(HashSet()) { it.id }
            val lastUsedList = rawLastUsedList.filter { it in sourceIds }
            sources
                .filter { it.lang in enabledLanguages || it.isLocal() }
                .filterNot { it.id.toString() in disabledSources }
                .sortedWith(mruThenAlphabetical(lastUsedList))
                .flatMap {
                    val flag = if ("${it.id}" in pinnedSourceIds) Pins.pinned else Pins.unpinned
                    val source = it.copy(pin = flag)
                    val toFlatten = mutableListOf(source)
                    if (it.id in lastUsedList) {
                        toFlatten.add(source.copy(isUsedLast = true, pin = source.pin - Pin.Actual))
                    }
                    toFlatten
                }
        }
            .distinctUntilChanged()
            .onEach { sources ->
                val validIds = sources.mapTo(HashSet()) { it.id }
                val current = preferences.lastUsedSources.get()
                val cleaned = current.filter { it in validIds }
                if (cleaned != current) {
                    preferences.lastUsedSources.set(cleaned)
                }
            }
    }

    private fun mruThenAlphabetical(lastUsedList: List<Long>): Comparator<Source> = Comparator { a, b ->
        val aIdx = lastUsedList.indexOf(a.id)
        val bIdx = lastUsedList.indexOf(b.id)
        when {
            aIdx == -1 && bIdx == -1 -> a.name.compareTo(b.name, ignoreCase = true)
            aIdx == -1 -> 1
            bIdx == -1 -> -1
            else -> aIdx.compareTo(bIdx)
        }
    }

    companion object {
        const val MAX_LAST_USED_SOURCES = 3
    }
}
