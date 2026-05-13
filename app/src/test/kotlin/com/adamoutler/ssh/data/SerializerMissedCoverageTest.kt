package com.adamoutler.ssh.data

import com.adamoutler.ssh.backup.BackupPayload
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method

class SerializerMissedCoverageTest {

    @Test
    fun testConnectionProfileSerializer_Exceptions() {
        val serializer = ConnectionProfile.serializer()
        testUnknownIndex(serializer)
        testMissingField(serializer)
    }

    @Test
    fun testBackupPayloadSerializer_Exceptions() {
        val serializer = BackupPayload.serializer()
        testUnknownIndex(serializer)
        testMissingField(serializer)
    }

    @Test
    fun testIdentityProfileSerializer_Exceptions() {
        val serializer = IdentityProfile.serializer()
        testUnknownIndex(serializer)
        testMissingField(serializer)
    }

    @Test
    fun testPortForwardConfigSerializer_Exceptions() {
        val serializer = PortForwardConfig.serializer()
        testUnknownIndex(serializer)
        testMissingField(serializer)
    }

    private fun <T> testUnknownIndex(serializer: KSerializer<T>) {
        val decoder = object : FakeDecoder() {
            override fun decodeElementIndex(descriptor: SerialDescriptor): Int = kotlinx.serialization.encoding.CompositeDecoder.UNKNOWN_NAME
        }
        var thrown = false
        try {
            serializer.deserialize(decoder)
        } catch (e: SerializationException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    private fun <T> testMissingField(serializer: KSerializer<T>) {
        val decoder = object : FakeDecoder() {
            override fun decodeElementIndex(descriptor: SerialDescriptor): Int = kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE
        }
        var thrown = false
        try {
            serializer.deserialize(decoder)
        } catch (e: SerializationException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    open class FakeDecoder : Decoder, CompositeDecoder {
        override val serializersModule: kotlinx.serialization.modules.SerializersModule
            get() = kotlinx.serialization.modules.EmptySerializersModule()

        override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = this

        override fun decodeBoolean(): Boolean = false
        override fun decodeByte(): Byte = 0
        override fun decodeChar(): Char = ' '
        override fun decodeDouble(): Double = 0.0
        override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = 0
        override fun decodeFloat(): Float = 0.0f
        override fun decodeInline(descriptor: SerialDescriptor): Decoder = this
        override fun decodeInt(): Int = 0
        override fun decodeLong(): Long = 0L
        override fun decodeNotNullMark(): Boolean = true
        override fun decodeNull(): Nothing? = null
        override fun decodeShort(): Short = 0
        override fun decodeString(): String = ""

        override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean = false
        override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte = 0
        override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char = ' '
        override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double = 0.0
        override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float = 0.0f
        override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder = this
        override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int = 0
        override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long = 0L
        
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> decodeNullableSerializableElement(descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T?>, previousValue: T?): T? = null
        
        override fun <T> decodeSerializableElement(descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T>, previousValue: T?): T {
            throw SerializationException("Fake")
        }
        override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short = 0
        override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String = ""
        override fun endStructure(descriptor: SerialDescriptor) {}

        override fun decodeElementIndex(descriptor: SerialDescriptor): Int = CompositeDecoder.DECODE_DONE
        override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = -1
        override fun decodeSequentially(): Boolean = false
    }
}
