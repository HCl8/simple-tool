package com.hcl.tool

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

object ApkTool {
    private fun ByteBuffer.skip(num: Int): ByteBuffer = this.apply {
        position(position() + num)
    }

    private fun ByteBuffer.readNChar(num: Int): CharArray {
        val result = CharArray(num)
        for (i in 0 until num) {
            result[i] = getChar()
        }
        return result
    }

    private fun ByteBuffer.readNByte(num: Int): ByteArray {
        val result = ByteArray(num)
        get(result)
        return result
    }

    data class Attribute(
        val nameSpeaceUri: Int,
        val name: String,
        val value: String?,
        val type: Int,
        val data: Int
    ) {
        companion object {
            fun from(bytes: ByteBuffer, stringMap: List<String>): Attribute {
                val nameSpeaceUri = bytes.getInt()
                val name = stringMap[bytes.getInt()]
                val valueNum = bytes.getInt()
                val value = if (valueNum > 0 && valueNum < stringMap.size) stringMap[valueNum] else null
                val type = bytes.getInt()
                val data = bytes.getInt()
                return Attribute(nameSpeaceUri, name, value, type, data)
            }
        }
    }

    sealed class XmlContent {
        data class StartNameSpeace(val prefix: String, val uri: Int) : XmlContent()
        data class EndNameSpeace(val prefix: String, val uri: Int) : XmlContent()
        data class StartTag(
            val nameSpeaceUri: Int,
            val name: String,
            val flag: Int,
            val attributeCount: Int,
            val attributeClass: Int,
            val attributes: List<Attribute>
        ) : XmlContent()

        data class EndTag(val nameSpeaceUri: Int, val name: String) : XmlContent()
        data class Text(val name: String) : XmlContent()

        companion object {
            fun from(flag: Int, bytes: ByteBuffer, stringMap: List<String>): XmlContent {
                when (flag) {
                    0x100100 -> run {
                        val prefix = bytes.getInt()
                        val uri = bytes.getInt()
                        return StartNameSpeace(stringMap[prefix], uri)
                    }

                    0x100101 -> run {
                        val prefix = bytes.getInt()
                        val uri = bytes.getInt()
                        return EndNameSpeace(stringMap[prefix], uri)
                    }

                    0x100102 -> run {
                        val nameSpeaceUri = bytes.getInt()
                        val name = stringMap[bytes.getInt()]
                        val flag = bytes.getInt()
                        val attributeCount = bytes.getInt()
                        val attributeClass = bytes.getInt()
                        val attributes = (0 until attributeCount).map {
                            Attribute.from(bytes, stringMap)
                        }
                        return StartTag(nameSpeaceUri, name, flag, attributeCount, attributeClass, attributes)
                    }

                    0x100103 -> run {
                        val nameSpeaceUri = bytes.getInt()
                        val name = stringMap[bytes.getInt()]
                        return EndTag(nameSpeaceUri, name)
                    }

                    0x100104 -> run {
                        val name = stringMap[bytes.getInt()]
                        bytes.skip(8)
                        return Text(name)
                    }

                    else -> throw IllegalArgumentException("not a valid flag ${flag.toString(16)}")
                }
            }
        }
    }

    private data class XmlElement(val type: Int, val size: Int, val lineNum: Int, val content: XmlContent) {
        companion object {
            fun from(bytes: ByteBuffer, stringMap: List<String>): XmlElement {
                val typeNum = bytes.getInt()
                val size = bytes.getInt()
                val lineNum = bytes.getInt()
                bytes.skip(4)
                val content = XmlContent.from(typeNum, bytes, stringMap)
                return XmlElement(typeNum, size, lineNum, content)
            }
        }
    }

    private fun extractManifest(path: String): List<XmlElement> {
        val manifestBytes = ZipFile(path).run { getInputStream(getEntry("AndroidManifest.xml")).readAllBytes() }

        val bytes = ByteBuffer.wrap(manifestBytes).order(ByteOrder.LITTLE_ENDIAN)

        val magicNum = bytes.getInt()
        if (magicNum != 0x80003) {
            throw IllegalArgumentException("not a valid AndroidManifest.xml")
        }
        val fileSize = bytes.getInt()

        val stringChunkIndex = bytes.position()
        assert(bytes.getInt() == 0x1c0001)
        val stringChunkSize = bytes.getInt()

        val stringCount = bytes.getInt()
        val stringPoolOffsetIndex = bytes.position() + 16
        val stringPoolIndex = stringChunkIndex + bytes.skip(8).getInt()

        bytes.position(stringPoolOffsetIndex)
        val stringIndexs = (0 until stringCount).map { bytes.getInt() + stringPoolIndex }

        val stringMap = stringIndexs.map {
            bytes.position(it)
            val size = bytes.getShort().toInt()
            String(bytes.readNChar(size))
        }

        bytes.position(stringChunkIndex + stringChunkSize)
        val resourceChunkIndex = bytes.position()
        assert(bytes.getInt() == 0x80180)
        val resourceChunkSize = bytes.getInt()

        bytes.position(resourceChunkIndex + resourceChunkSize)
        val xmlContentChunkIndex = bytes.position()

        val r = mutableListOf<XmlElement>()
        while (bytes.position() < bytes.limit()) {
            val element = XmlElement.from(bytes, stringMap)
            r += element
        }
        return r.toList()
    }

    fun getApkVersion(path: String): String {
        val elements = extractManifest(path)
        return elements
            .filter { it.content is XmlContent.StartTag && it.content.name == "manifest" }
            .map {
                val tag = it.content as XmlContent.StartTag
                tag.attributes
                    .filter { it.name == "versionName" }
                    .map { it.value }
                    .firstOrNull()
            }
            .firstOrNull() ?: "null"
    }
}