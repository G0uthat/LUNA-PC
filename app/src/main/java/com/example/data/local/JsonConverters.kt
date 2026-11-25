package com.example.data.local

import com.example.data.model.DrawingStroke
import com.example.data.model.DrawingTool
import com.example.data.model.OcrBlockBox
import com.example.data.model.OcrPageResult
import com.example.data.model.OcrWordBox
import com.example.data.model.StrokePoint
import org.json.JSONArray
import org.json.JSONObject

object JsonConverters {

    fun strokesToJson(strokes: List<DrawingStroke>): String {
        val array = JSONArray()
        for (stroke in strokes) {
            val obj = JSONObject()
            obj.put("id", stroke.id)
            obj.put("tool", stroke.tool.name)
            obj.put("color", stroke.colorArgb)
            obj.put("width", stroke.widthNorm.toDouble())
            obj.put("alpha", stroke.alpha.toDouble())
            if (stroke.isFilled) {
                obj.put("filled", true)
            }
            val pts = JSONArray()
            for (pt in stroke.points) {
                pts.put(pt.x.toDouble())
                pts.put(pt.y.toDouble())
            }
            obj.put("pts", pts)
            array.put(obj)
        }
        return array.toString()
    }

    fun jsonToStrokes(json: String): List<DrawingStroke> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            val result = ArrayList<DrawingStroke>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val toolName = obj.optString("tool", DrawingTool.PEN.name)
                val tool = DrawingTool.entries.firstOrNull { it.name == toolName } ?: DrawingTool.PEN
                val ptsArray = obj.optJSONArray("pts") ?: JSONArray()
                val points = ArrayList<StrokePoint>(ptsArray.length() / 2)
                var j = 0
                while (j + 1 < ptsArray.length()) {
                    points.add(
                        StrokePoint(
                            x = ptsArray.optDouble(j, 0.0).toFloat(),
                            y = ptsArray.optDouble(j + 1, 0.0).toFloat()
                        )
                    )
                    j += 2
                }
                result.add(
                    DrawingStroke(
                        id = obj.optString("id", "s_$i"),
                        tool = tool,
                        colorArgb = obj.optLong("color", 0xFF2B64F5),
                        widthNorm = obj.optDouble("width", 0.008).toFloat(),
                        alpha = obj.optDouble("alpha", 1.0).toFloat(),
                        points = points,
                        isFilled = obj.optBoolean("filled", false)
                    )
                )
            }
            result
        }.getOrDefault(emptyList())
    }

    fun ocrPageToJson(result: OcrPageResult): String {
        val root = JSONObject()
        root.put("pageIndex", result.pageIndex)
        root.put("isTranslated", result.isTranslated)
        val blocksArr = JSONArray()
        for (block in result.blocks) {
            val bObj = JSONObject()
            bObj.put("id", block.id)
            bObj.put("originalText", block.originalText)
            if (block.translatedText != null) {
                bObj.put("translatedText", block.translatedText)
            }
            bObj.put("l", block.left.toDouble())
            bObj.put("t", block.top.toDouble())
            bObj.put("r", block.right.toDouble())
            bObj.put("b", block.bottom.toDouble())
            bObj.put("bg", block.bgColorArgb)
            bObj.put("ink", block.inkColorArgb)
            val wordsArr = JSONArray()
            for (word in block.words) {
                val wObj = JSONObject()
                wObj.put("text", word.text)
                wObj.put("l", word.left.toDouble())
                wObj.put("t", word.top.toDouble())
                wObj.put("r", word.right.toDouble())
                wObj.put("b", word.bottom.toDouble())
                wordsArr.put(wObj)
            }
            bObj.put("words", wordsArr)
            blocksArr.put(bObj)
        }
        root.put("blocks", blocksArr)
        return root.toString()
    }

    fun jsonToOcrPage(json: String): OcrPageResult? {
        if (json.isBlank()) return null
        return runCatching {
            val root = JSONObject(json)
            val pageIndex = root.optInt("pageIndex", 0)
            val isTranslated = root.optBoolean("isTranslated", false)
            val blocksArr = root.optJSONArray("blocks") ?: JSONArray()
            val blocks = ArrayList<OcrBlockBox>(blocksArr.length())
            for (i in 0 until blocksArr.length()) {
                val bObj = blocksArr.getJSONObject(i)
                val wordsArr = bObj.optJSONArray("words") ?: JSONArray()
                val words = ArrayList<OcrWordBox>(wordsArr.length())
                for (w in 0 until wordsArr.length()) {
                    val wObj = wordsArr.getJSONObject(w)
                    words.add(
                        OcrWordBox(
                            text = wObj.optString("text", ""),
                            left = wObj.optDouble("l", 0.0).toFloat(),
                            top = wObj.optDouble("t", 0.0).toFloat(),
                            right = wObj.optDouble("r", 0.0).toFloat(),
                            bottom = wObj.optDouble("b", 0.0).toFloat()
                        )
                    )
                }
                val translated = if (bObj.has("translatedText") && !bObj.isNull("translatedText")) {
                    bObj.optString("translatedText")
                } else {
                    null
                }
                blocks.add(
                    OcrBlockBox(
                        id = bObj.optInt("id", i),
                        originalText = bObj.optString("originalText", ""),
                        translatedText = translated,
                        left = bObj.optDouble("l", 0.0).toFloat(),
                        top = bObj.optDouble("t", 0.0).toFloat(),
                        right = bObj.optDouble("r", 0.0).toFloat(),
                        bottom = bObj.optDouble("b", 0.0).toFloat(),
                        words = words,
                        bgColorArgb = bObj.optLong("bg", 0xFFFFFFFFL),
                        inkColorArgb = bObj.optLong("ink", 0xFF101828L)
                    )
                )
            }
            OcrPageResult(pageIndex = pageIndex, blocks = blocks, isTranslated = isTranslated)
        }.getOrNull()
    }
}
