package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import logcat.asLog
import tachiyomi.core.common.util.system.logcat
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf

/**
 * Serializes and deserializes source [Filter]s to and from JSON, e.g. for saved searches.
 *
 * Ported from TachiyomiSY's FilterSerializer.
 */
class FilterSerializer {
    private val serializers = listOf<Serializer<*>>(
        HeaderSerializer(this),
        SeparatorSerializer(this),
        SelectSerializer(this),
        TextSerializer(this),
        CheckboxSerializer(this),
        TriStateSerializer(this),
        GroupSerializer(this),
        SortSerializer(this),
    )

    fun serialize(filters: FilterList) = buildJsonArray {
        filters.filterIsInstance<Filter<Any?>>().forEach {
            add(serialize(it))
        }
    }

    fun serialize(filter: Filter<Any?>): JsonObject {
        return serializers
            .filterIsInstance<Serializer<Filter<Any?>>>()
            .firstOrNull {
                filter::class.isSubclassOf(it.clazz)
            }?.let { serializer ->
                buildJsonObject {
                    with(serializer) { serialize(filter) }

                    val classMappings = mutableListOf<Pair<String, Any>>()

                    serializer.mappings().forEach {
                        val res = it.second.get(filter)
                        put(it.first, res.toString())
                        classMappings += it.first to (res?.javaClass?.name ?: "null")
                    }

                    putJsonObject(CLASS_MAPPINGS) {
                        classMappings.forEach { (t, u) ->
                            put(t, u.toString())
                        }
                    }

                    put(TYPE, serializer.type)
                }
            } ?: throw IllegalArgumentException("Cannot serialize this Filter object!")
    }

    fun deserialize(filters: FilterList, json: JsonArray) {
        filters.filterIsInstance<Filter<Any?>>().zip(json).forEach { (filter, obj) ->
            try {
                deserialize(filter, obj.jsonObject)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { e.asLog() }
            }
        }
    }

    fun deserialize(filter: Filter<Any?>, json: JsonObject) {
        val serializer = serializers
            .filterIsInstance<Serializer<Filter<Any?>>>()
            .firstOrNull {
                it.type == json[TYPE]!!.jsonPrimitive.content
            } ?: throw IllegalArgumentException("Cannot deserialize this type!")

        serializer.deserialize(json, filter)

        serializer.mappings().forEach {
            if (it.second is KMutableProperty1) {
                val obj = json[it.first]!!.jsonPrimitive
                val res: Any? = when (json[CLASS_MAPPINGS]!!.jsonObject[it.first]!!.jsonPrimitive.content) {
                    java.lang.Integer::class.java.name -> obj.int
                    java.lang.Long::class.java.name -> obj.long
                    java.lang.Float::class.java.name -> obj.float
                    java.lang.Double::class.java.name -> obj.double
                    java.lang.String::class.java.name -> obj.content
                    java.lang.Boolean::class.java.name -> obj.boolean
                    java.lang.Byte::class.java.name -> obj.content.toByte()
                    java.lang.Short::class.java.name -> obj.content.toShort()
                    java.lang.Character::class.java.name -> obj.content[0]
                    "null" -> null
                    else -> throw IllegalArgumentException("Cannot deserialize this type!")
                }
                @Suppress("UNCHECKED_CAST")
                (it.second as KMutableProperty1<in Filter<Any?>, in Any?>).set(filter, res)
            }
        }
    }

    companion object {
        const val TYPE = "_type"
        const val CLASS_MAPPINGS = "_cmaps"
    }
}

private interface Serializer<in T : Filter<out Any?>> {
    fun JsonObjectBuilder.serialize(filter: T) {}
    fun deserialize(json: JsonObject, filter: T) {}

    /**
     * Automatic two-way mappings between fields and JSON
     */
    fun mappings(): List<Pair<String, KProperty1<in T, *>>> = emptyList()

    val serializer: FilterSerializer
    val type: String
    val clazz: KClass<in T>
}

private class HeaderSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Header> {
    override val type = "HEADER"
    override val clazz = Filter.Header::class

    override fun mappings() = listOf(
        Pair(NAME, Filter.Header::name),
    )

    companion object {
        const val NAME = "name"
    }
}

private class SeparatorSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Separator> {
    override val type = "SEPARATOR"
    override val clazz = Filter.Separator::class

    override fun mappings() = listOf(
        Pair(NAME, Filter.Separator::name),
    )

    companion object {
        const val NAME = "name"
    }
}

private class SelectSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Select<Any>> {
    override val type = "SELECT"
    override val clazz = Filter.Select::class

    override fun JsonObjectBuilder.serialize(filter: Filter.Select<Any>) {
        // Serialize values to JSON
        putJsonArray(VALUES) {
            filter.values.map {
                it.toString()
            }.forEach { add(it) }
        }
    }

    override fun mappings() = listOf(
        Pair(NAME, Filter.Select<Any>::name),
        Pair(STATE, Filter.Select<Any>::state),
    )

    companion object {
        const val NAME = "name"
        const val VALUES = "values"
        const val STATE = "state"
    }
}

private class TextSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Text> {
    override val type = "TEXT"
    override val clazz = Filter.Text::class

    override fun mappings() = listOf(
        Pair(NAME, Filter.Text::name),
        Pair(STATE, Filter.Text::state),
    )

    companion object {
        const val NAME = "name"
        const val STATE = "state"
    }
}

private class CheckboxSerializer(override val serializer: FilterSerializer) : Serializer<Filter.CheckBox> {
    override val type = "CHECKBOX"
    override val clazz = Filter.CheckBox::class

    override fun mappings() = listOf(
        Pair(NAME, Filter.CheckBox::name),
        Pair(STATE, Filter.CheckBox::state),
    )

    companion object {
        const val NAME = "name"
        const val STATE = "state"
    }
}

private class TriStateSerializer(override val serializer: FilterSerializer) : Serializer<Filter.TriState> {
    override val type = "TRISTATE"
    override val clazz = Filter.TriState::class

    override fun mappings() = listOf(
        Pair(NAME, Filter.TriState::name),
        Pair(STATE, Filter.TriState::state),
    )

    companion object {
        const val NAME = "name"
        const val STATE = "state"
    }
}

private class GroupSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Group<Any?>> {
    override val type = "GROUP"
    override val clazz = Filter.Group::class

    override fun JsonObjectBuilder.serialize(filter: Filter.Group<Any?>) {
        putJsonArray(STATE) {
            filter.state.forEach {
                add(
                    if (it is Filter<*>) {
                        @Suppress("UNCHECKED_CAST")
                        serializer.serialize(it as Filter<Any?>)
                    } else {
                        JsonNull
                    },
                )
            }
        }
    }

    override fun deserialize(json: JsonObject, filter: Filter.Group<Any?>) {
        json[STATE]!!.jsonArray.forEachIndexed { index, jsonElement ->
            if (jsonElement !is JsonNull) {
                @Suppress("UNCHECKED_CAST")
                serializer.deserialize(filter.state[index] as Filter<Any?>, jsonElement.jsonObject)
            }
        }
    }

    override fun mappings() = listOf(
        Pair(NAME, Filter.Group<Any?>::name),
    )

    companion object {
        const val NAME = "name"
        const val STATE = "state"
    }
}

private class SortSerializer(override val serializer: FilterSerializer) : Serializer<Filter.Sort> {
    override val type = "SORT"
    override val clazz = Filter.Sort::class

    override fun JsonObjectBuilder.serialize(filter: Filter.Sort) {
        // Serialize values
        putJsonArray(VALUES) {
            filter.values.forEach { add(it) }
        }
        // Serialize state
        put(
            STATE,
            filter.state?.let { (index, ascending) ->
                buildJsonObject {
                    put(STATE_INDEX, index)
                    put(STATE_ASCENDING, ascending)
                }
            } ?: JsonNull,
        )
    }

    override fun deserialize(json: JsonObject, filter: Filter.Sort) {
        // Deserialize state
        filter.state = (json[STATE] as? JsonObject)?.let {
            Filter.Sort.Selection(
                it[STATE_INDEX]!!.jsonPrimitive.int,
                it[STATE_ASCENDING]!!.jsonPrimitive.boolean,
            )
        }
    }

    override fun mappings() = listOf(
        Pair(NAME, Filter.Sort::name),
    )

    companion object {
        const val NAME = "name"
        const val VALUES = "values"
        const val STATE = "state"

        const val STATE_INDEX = "index"
        const val STATE_ASCENDING = "ascending"
    }
}
