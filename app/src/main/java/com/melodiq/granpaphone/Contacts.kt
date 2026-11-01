package com.melodiq.granpaphone

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

data class Person(
    val name: String,
    val number: String,
    /** Все формы слов имени, по которым можно узнать контакт (дочь, дочери, дочке…). */
    val forms: Set<String>,
    /** Нормализованные слова имени. */
    val nameWords: List<String>
)

object Contacts {

    /** Ласковые и падежные формы для типичных подписей контактов. */
    private val synonyms = mapOf(
        "дочь" to listOf("дочери", "дочерью", "дочка", "дочке", "дочку", "дочкой", "доча", "доче", "дочу", "дочурка", "доченька", "доченьке"),
        "дочка" to listOf("дочь", "дочери", "дочке", "дочку", "доча", "доченька"),
        "сын" to listOf("сына", "сыну", "сыном", "сынок", "сынку", "сынишка", "сыночек"),
        "мама" to listOf("мать", "матери", "маме", "маму", "мамочка", "мамочке", "мамуля"),
        "мать" to listOf("мама", "маме", "матери"),
        "папа" to listOf("отец", "отцу", "папе", "папу", "папочка"),
        "отец" to listOf("папа", "папе", "отцу"),
        "жена" to listOf("супруга", "супруге", "жене", "жену", "женушка"),
        "муж" to listOf("супруг", "супругу", "мужу", "мужа"),
        "внук" to listOf("внуку", "внука", "внучок", "внучек"),
        "внучка" to listOf("внучке", "внучку", "внученька", "внучечка"),
        "брат" to listOf("брату", "брата", "братишка", "братик"),
        "сестра" to listOf("сестре", "сестру", "сестренка", "сестренке", "сестричка"),
        "бабушка" to listOf("бабушке", "бабушку", "бабуля", "бабуле"),
        "дедушка" to listOf("дедушке", "дед", "деду"),
        "врач" to listOf("врачу", "доктор", "доктору"),
        "соседка" to listOf("соседке", "соседку"),
        "сосед" to listOf("соседу", "соседа")
    )

    /** Падежные формы слова по окончанию. Лишние отсекаются словарём модели. */
    fun wordForms(w: String): Set<String> {
        val s = linkedSetOf(w)
        if (w.length < 3) return s
        val stem1 = w.dropLast(1)
        when {
            w.endsWith("ия") -> listOf("ии", "ию", "ией").forEach { s += w.dropLast(2) + it }
            w.endsWith("а") -> listOf("ы", "и", "е", "у", "ой").forEach { s += stem1 + it }
            w.endsWith("я") -> listOf("и", "е", "ю", "ей").forEach { s += stem1 + it }
            w.endsWith("ь") -> listOf("и", "ю", "я", "ем", "ью", "е").forEach { s += stem1 + it }
            w.endsWith("й") -> listOf("я", "ю", "ем", "е").forEach { s += stem1 + it }
            w.last() in "оеиуыэю" -> {}
            w.last() in 'а'..'я' -> listOf("а", "у", "ом", "е").forEach { s += w + it }
        }
        synonyms[w]?.let { s.addAll(it) }
        return s
    }

    fun load(ctx: Context): List<Person> {
        val byName = LinkedHashMap<String, String>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY + " DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    if (!byName.containsKey(name)) byName[name] = number
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        val phr = Phrases.of(ctx)
        return byName.map { (name, number) ->
            val minLen = if (phr.code == "zh") 1 else 2
            val nw = phr.words(name).filter { it.length >= minLen && it.any { ch -> ch.isLetter() } }
            val forms = nw.flatMap { phr.contactForms(it) }.toSet()
            Person(name, number, forms, nw)
        }
    }

    /** Контакты, подходящие под фразу, лучшие первыми. */
    fun match(people: List<Person>, phrase: String, phr: Phrases): List<Person> {
        return people
            .map { p -> p to phr.contactScore(p, phrase) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /** Имя звонящего по номеру. */
    fun nameFor(ctx: Context, number: String?): String {
        val phr = Phrases.of(ctx)
        if (number.isNullOrBlank()) return phr.unknownNumber
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: phr.strangerNumber
        } catch (e: Exception) {
            phr.strangerNumber
        }
    }
}
