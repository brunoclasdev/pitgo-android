package com.trustarx.pitgo.features.signup.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal object PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val separators = if (text.length > 10) {
            mapOf(0 to "(", 2 to ") ", 7 to "-")
        } else {
            mapOf(0 to "(", 2 to ") ", 6 to "-")
        }
        return transformWithSeparators(text, separators)
    }
}

internal object CpfVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = transformWithSeparators(
        text = text,
        separatorsBeforeIndex = mapOf(3 to ".", 6 to ".", 9 to "-"),
    )
}

internal object CnpjVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = transformWithSeparators(
        text = text,
        separatorsBeforeIndex = mapOf(2 to ".", 5 to ".", 8 to "/", 12 to "-"),
    )
}

private fun transformWithSeparators(
    text: AnnotatedString,
    separatorsBeforeIndex: Map<Int, String>,
): TransformedText {
    val transformed = buildString {
        text.text.forEachIndexed { index, character ->
            append(separatorsBeforeIndex[index].orEmpty())
            append(character)
        }
    }

    val transformedSeparatorPositions = buildList {
        var addedCharacters = 0
        text.text.indices.forEach { originalIndex ->
            separatorsBeforeIndex[originalIndex]?.let { separator ->
                separator.indices.forEach { add(originalIndex + addedCharacters + it) }
                addedCharacters += separator.length
            }
        }
    }

    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            val safeOffset = offset.coerceIn(0, text.length)
            val separators = separatorsBeforeIndex
                .filterKeys { it < safeOffset && it < text.length }
                .values
                .sumOf(String::length)
            return (safeOffset + separators).coerceAtMost(transformed.length)
        }

        override fun transformedToOriginal(offset: Int): Int {
            val safeOffset = offset.coerceIn(0, transformed.length)
            val separators = transformedSeparatorPositions.count { it < safeOffset }
            return (safeOffset - separators).coerceIn(0, text.length)
        }
    }

    return TransformedText(AnnotatedString(transformed), mapping)
}
