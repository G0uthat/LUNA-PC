package com.example.notebook.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Forward- and backward-compatible JSON codec for Study Notebook pages.
 * - Writes `schemaVersion = 2` with unified `objects` array (keeping binary assets strictly as relative paths).
 * - Cleanly decodes BOTH `schemaVersion = 2` AND legacy `v = 1` (`strokes` + `items`) pages without data loss.
 */
object NotebookJsonCodec {

    fun encode(content: NotebookPageContent): String {
        val root = JSONObject()
        root.put("schemaVersion", NotebookPageContent.CURRENT_SCHEMA_VERSION)

        val layersArr = JSONArray()
        for (layer in content.resolvedLayers()) {
            val lj = JSONObject()
            lj.put("id", layer.id)
            lj.put("name", layer.name)
            lj.put("vis", layer.visible)
            if (layer.locked) lj.put("lock", true)
            lj.put("op", round3(layer.opacity))
            layersArr.put(lj)
        }
        root.put("layers", layersArr)

        val objectsArr = JSONArray()
        for (obj in content.objects) {
            val j = JSONObject()
            j.put("id", obj.id)
            j.put("z", obj.zIndex)
            j.put("layer", obj.layerId)
            if (obj.locked) j.put("locked", true)
            if (!obj.visible) j.put("visible", false)
            obj.groupId?.let { j.put("group", it) }
            j.put("x", round4(obj.x))
            j.put("y", round4(obj.y))
            j.put("w", round4(obj.width))
            j.put("h", round4(obj.height))
            if (obj.rotationDeg != 0f) j.put("rot", round2(obj.rotationDeg))

            when (obj) {
                is StrokeObject -> {
                    if (obj.points.isEmpty()) continue
                    j.put("type", "stroke")
                    j.put("tool", obj.tool.name)
                    j.put("c", obj.colorArgb)
                    j.put("sw", round4(obj.widthNorm))
                    j.put("op", round3(obj.opacity))
                    j.put("blend", obj.blendMode.name)
                    // Compact flat triplet array [x, y, pressure, ...]
                    val pts = JSONArray()
                    for (pt in obj.points) {
                        pts.put(round4(pt.x))
                        pts.put(round4(pt.y))
                        pts.put(round3(pt.pressure))
                    }
                    j.put("pts3", pts)
                }

                is ShapeObject -> {
                    j.put("type", "shape")
                    j.put("kind", obj.kind.name)
                    j.put("sx", round4(obj.startX))
                    j.put("sy", round4(obj.startY))
                    j.put("ex", round4(obj.endX))
                    j.put("ey", round4(obj.endY))
                    obj.midX?.let { j.put("mx", round4(it)) }
                    obj.midY?.let { j.put("my", round4(it)) }
                    j.put("c", obj.strokeColorArgb)
                    obj.fillColorArgb?.let { j.put("fill", it) }
                    j.put("sw", round4(obj.strokeWidthNorm))
                    if (obj.isDashed) j.put("dash", true)
                    j.put("cr", round2(obj.cornerRadiusDp))
                    j.put("op", round3(obj.opacity))
                    if (obj.hasShadow) j.put("sh", true)
                    if (obj.labelText.isNotEmpty()) {
                        j.put("lbl", obj.labelText)
                        j.put("lbg", obj.labelBgColorArgb)
                    }
                }

                is ConnectorObject -> {
                    j.put("type", "connector")
                    j.put("from", obj.fromObjectId)
                    j.put("to", obj.toObjectId)
                    j.put("c", obj.colorArgb)
                    j.put("sw", round4(obj.strokeWidthNorm))
                    if (obj.isDashed) j.put("dash", true)
                    if (!obj.isCurved) j.put("curved", false)
                    if (obj.doubleHeaded) j.put("dbl", true)
                    j.put("op", round3(obj.opacity))
                    if (obj.labelText.isNotEmpty()) j.put("lbl", obj.labelText)
                }

                is ImageObject -> {
                    j.put("type", "image")
                    j.put("asset", obj.relativeAssetPath)
                    j.put("op", round3(obj.opacity))
                    if (obj.flipHorizontal) j.put("fh", true)
                    if (obj.flipVertical) j.put("fv", true)
                    j.put("cl", round4(obj.cropLeft))
                    j.put("ct", round4(obj.cropTop))
                    j.put("crg", round4(obj.cropRight))
                    j.put("cb", round4(obj.cropBottom))
                    obj.borderColorArgb?.let { j.put("bdr", it) }
                    j.put("bw", round2(obj.borderWidthDp))
                    j.put("cr", round2(obj.cornerRadiusDp))
                    if (obj.hasShadow) j.put("sh", true)
                    if (obj.isOvalCrop) j.put("oval", true)
                }

                is TextObject -> {
                    j.put("type", "text")
                    j.put("font", obj.fontId)
                    j.put("fs", round4(obj.fontSizeNorm))
                    j.put("ls", round2(obj.lineSpacingMult))
                    j.put("align", obj.alignment.name)
                    j.put("list", obj.listMode.name)
                    if (obj.isBanner) j.put("ban", true)
                    obj.fillColorArgb?.let { j.put("fill", it) }
                    obj.borderColorArgb?.let { j.put("bdr", it) }
                    j.put("bw", round2(obj.borderWidthDp))
                    j.put("cr", round2(obj.cornerRadiusDp))
                    if (obj.hasShadow) j.put("sh", true)
                    j.put("op", round3(obj.opacity))
                    val spansArr = JSONArray()
                    for (sp in obj.spans) {
                        val s = JSONObject()
                        s.put("t", sp.text)
                        s.put("c", sp.colorArgb)
                        if (sp.isBold) s.put("b", true)
                        if (sp.isItalic) s.put("i", true)
                        if (sp.isUnderline) s.put("u", true)
                        if (sp.isStrikethrough) s.put("st", true)
                        if (sp.isSuperscript) s.put("sup", true)
                        if (sp.isSubscript) s.put("sub", true)
                        sp.highlightColorArgb?.let { s.put("hl", it) }
                        spansArr.put(s)
                    }
                    j.put("spans", spansArr)
                }

                is StickerObject -> {
                    j.put("type", "sticker")
                    j.put("kind", obj.builtInKind.name)
                    obj.customAssetPath?.let { j.put("asset", it) }
                    j.put("c", obj.colorArgb)
                    obj.bgColorArgb?.let { j.put("bg", it) }
                    j.put("op", round3(obj.opacity))
                }

                is TableObject -> {
                    j.put("type", "table")
                    j.put("rows", obj.rows)
                    j.put("cols", obj.cols)
                    j.put("hdr", obj.hasHeaderRow)
                    j.put("bdr", obj.borderColorArgb)
                    j.put("hbg", obj.headerBgColorArgb)
                    j.put("cbg", obj.cellBgColorArgb)
                    j.put("tc", obj.textColorArgb)
                    j.put("fs", round4(obj.fontSizeNorm))
                    j.put("op", round3(obj.opacity))
                    val cellsArr = JSONArray()
                    obj.cells.forEach { cellsArr.put(it) }
                    j.put("cells", cellsArr)
                }
            }
            objectsArr.put(j)
        }
        root.put("objects", objectsArr)
        return root.toString()
    }

    fun decode(json: String): NotebookPageContent {
        if (json.isBlank()) return NotebookPageContent()
        return runCatching {
            val root = JSONObject(json)
            val schemaVersion = root.optInt("schemaVersion", root.optInt("v", 1))
            if (schemaVersion >= 2 && root.has("objects")) {
                decodeV2(root, schemaVersion)
            } else {
                decodeLegacyV1(root)
            }
        }.getOrDefault(NotebookPageContent())
    }

    private fun decodeV2(root: JSONObject, schemaVersion: Int): NotebookPageContent {
        val parsedLayers = mutableListOf<NotebookLayerInfo>()
        val layersArr = root.optJSONArray("layers")
        if (layersArr != null) {
            for (i in 0 until layersArr.length()) {
                val lj = layersArr.optJSONObject(i) ?: continue
                val lid = lj.optString("id").ifBlank { continue }
                val lname = lj.optString("name").ifBlank { lid }
                parsedLayers.add(
                    NotebookLayerInfo(
                        id = lid,
                        name = lname,
                        visible = lj.optBoolean("vis", true),
                        locked = lj.optBoolean("lock", false),
                        opacity = lj.optDouble("op", 1.0).toFloat().coerceIn(0.05f, 1f)
                    )
                )
            }
        }
        val effectiveLayers = if (parsedLayers.isEmpty()) NotebookLayerInfo.DEFAULT_LAYERS else parsedLayers

        val arr = root.optJSONArray("objects") ?: return NotebookPageContent(
            schemaVersion = schemaVersion,
            layers = effectiveLayers
        )
        val result = ArrayList<NotebookObject>(arr.length())

        for (i in 0 until arr.length()) {
            val j = arr.optJSONObject(i) ?: continue
            val id = j.optString("id").ifBlank { UUID.randomUUID().toString() }
            val z = j.optInt("z", 10)
            val layer = j.optString("layer").ifBlank { NotebookLayerIds.CONTENT }
            val locked = j.optBoolean("locked", false)
            val visible = j.optBoolean("visible", true)
            val group = if (j.has("group")) j.optString("group").takeIf { it.isNotBlank() } else null
            val x = j.optDouble("x", 0.1).toFloat()
            val y = j.optDouble("y", 0.1).toFloat()
            val w = j.optDouble("w", 0.3).toFloat()
            val h = j.optDouble("h", 0.15).toFloat()
            val rot = j.optDouble("rot", 0.0).toFloat()

            when (j.optString("type")) {
                "stroke" -> {
                    val pts = mutableListOf<NbPoint>()
                    val pts3 = j.optJSONArray("pts3")
                    if (pts3 != null) {
                        var k = 0
                        while (k + 2 < pts3.length()) {
                            pts.add(
                                NbPoint(
                                    x = pts3.optDouble(k, 0.0).toFloat(),
                                    y = pts3.optDouble(k + 1, 0.0).toFloat(),
                                    pressure = pts3.optDouble(k + 2, 1.0).toFloat().coerceIn(0.15f, 2f)
                                )
                            )
                            k += 3
                        }
                    }
                    if (pts.isEmpty()) continue
                    val tool = StrokeToolKind.fromName(j.optString("tool"))
                    val blend = StrokeBlendMode.fromName(j.optString("blend"))
                    result.add(
                        StrokeObject(
                            id = id,
                            zIndex = z,
                            layerId = layer,
                            locked = locked,
                            visible = visible,
                            groupId = group,
                            x = x,
                            y = y,
                            width = w,
                            height = h,
                            rotationDeg = rot,
                            tool = tool,
                            colorArgb = j.optLong("c", 0xFF151C2BL),
                            widthNorm = j.optDouble("sw", 0.0042).toFloat(),
                            opacity = j.optDouble("op", 1.0).toFloat().coerceIn(0.05f, 1f),
                            points = pts,
                            blendMode = blend
                        )
                    )
                }

                "shape" -> {
                    result.add(
                        ShapeObject(
                            id = id,
                            zIndex = z,
                            layerId = layer,
                            locked = locked,
                            visible = visible,
                            groupId = group,
                            x = x,
                            y = y,
                            width = w,
                            height = h,
                            rotationDeg = rot,
                            kind = NotebookShapeKind.fromName(j.optString("kind")),
                            startX = j.optDouble("sx", x.toDouble()).toFloat(),
                            startY = j.optDouble("sy", y.toDouble()).toFloat(),
                            endX = j.optDouble("ex", (x + w).toDouble()).toFloat(),
                            endY = j.optDouble("ey", (y + h).toDouble()).toFloat(),
                            midX = if (j.has("mx")) j.optDouble("mx").toFloat() else null,
                            midY = if (j.has("my")) j.optDouble("my").toFloat() else null,
                            strokeColorArgb = j.optLong("c", 0xFF1D5BFFL),
                            fillColorArgb = if (j.has("fill")) j.optLong("fill") else null,
                            strokeWidthNorm = j.optDouble("sw", 0.0045).toFloat(),
                            isDashed = j.optBoolean("dash", false),
                            cornerRadiusDp = j.optDouble("cr", 14.0).toFloat(),
                            opacity = j.optDouble("op", 1.0).toFloat().coerceIn(0.05f, 1f),
                            hasShadow = j.optBoolean("sh", false),
                            labelText = j.optString("lbl", ""),
                            labelBgColorArgb = j.optLong("lbg", 0xFFFFF8D6L)
                        )
                    )
                }

                "connector" -> {
                    val fromId = j.optString("from", "")
                    val toId = j.optString("to", "")
                    if (fromId.isNotBlank() && toId.isNotBlank()) {
                        result.add(
                            ConnectorObject(
                                id = id,
                                zIndex = z,
                                layerId = layer,
                                locked = locked,
                                visible = visible,
                                groupId = group,
                                fromObjectId = fromId,
                                toObjectId = toId,
                                colorArgb = j.optLong("c", 0xFF1D5BFFL),
                                strokeWidthNorm = j.optDouble("sw", 0.0042).toFloat(),
                                isDashed = j.optBoolean("dash", false),
                                isCurved = j.optBoolean("curved", true),
                                doubleHeaded = j.optBoolean("dbl", false),
                                opacity = j.optDouble("op", 1.0).toFloat().coerceIn(0.05f, 1f),
                                labelText = j.optString("lbl", "")
                            )
                        )
                    }
                }

                "image" -> {
                    val asset = j.optString("asset", "")
                    if (asset.isNotBlank()) {
                        result.add(
                            ImageObject(
                                id = id,
                                zIndex = z,
                                layerId = layer,
                                locked = locked,
                                visible = visible,
                                groupId = group,
                                x = x,
                                y = y,
                                width = w,
                                height = h,
                                rotationDeg = rot,
                                relativeAssetPath = asset,
                                opacity = j.optDouble("op", 1.0).toFloat().coerceIn(0.05f, 1f),
                                flipHorizontal = j.optBoolean("fh", false),
                                flipVertical = j.optBoolean("fv", false),
                                cropLeft = j.optDouble("cl", 0.0).toFloat().coerceIn(0f, 0.9f),
                                cropTop = j.optDouble("ct", 0.0).toFloat().coerceIn(0f, 0.9f),
                                cropRight = j.optDouble("crg", 1.0).toFloat().coerceIn(0.1f, 1f),
                                cropBottom = j.optDouble("cb", 1.0).toFloat().coerceIn(0.1f, 1f),
                                borderColorArgb = if (j.has("bdr")) j.optLong("bdr") else null,
                                borderWidthDp = j.optDouble("bw", 2.0).toFloat(),
                                cornerRadiusDp = j.optDouble("cr", 10.0).toFloat(),
                                hasShadow = j.optBoolean("sh", false),
                                isOvalCrop = j.optBoolean("oval", false)
                            )
                        )
                    }
                }

                "text" -> {
                    val spansArr = j.optJSONArray("spans")
                    val spans = mutableListOf<NotebookTextSpan>()
                    if (spansArr != null) {
                        for (sIdx in 0 until spansArr.length()) {
                            val sp = spansArr.optJSONObject(sIdx) ?: continue
                            spans.add(
                                NotebookTextSpan(
                                    text = sp.optString("t", ""),
                                    colorArgb = sp.optLong("c", 0xFF151C2BL),
                                    isBold = sp.optBoolean("b", false),
                                    isItalic = sp.optBoolean("i", false),
                                    isUnderline = sp.optBoolean("u", false),
                                    isStrikethrough = sp.optBoolean("st", false),
                                    isSuperscript = sp.optBoolean("sup", false),
                                    isSubscript = sp.optBoolean("sub", false),
                                    highlightColorArgb = if (sp.has("hl")) sp.optLong("hl") else null
                                )
                            )
                        }
                    }
                    result.add(
                        TextObject(
                            id = id,
                            zIndex = z,
                            layerId = layer,
                            locked = locked,
                            visible = visible,
                            groupId = group,
                            x = x,
                            y = y,
                            width = w,
                            height = h,
                            rotationDeg = rot,
                            spans = spans,
                            fontId = j.optString("font", NotebookFontId.SANS.id),
                            fontSizeNorm = j.optDouble("fs", 0.022).toFloat(),
                            lineSpacingMult = j.optDouble("ls", 1.18).toFloat().coerceIn(0.9f, 2.2f),
                            alignment = TextAlignMode.fromName(j.optString("align")),
                            listMode = TextListMode.fromName(j.optString("list")),
                            isBanner = j.optBoolean("ban", false),
                            fillColorArgb = if (j.has("fill")) j.optLong("fill") else null,
                            borderColorArgb = if (j.has("bdr")) j.optLong("bdr") else null,
                            borderWidthDp = j.optDouble("bw", 2.0).toFloat(),
                            cornerRadiusDp = j.optDouble("cr", 14.0).toFloat(),
                            hasShadow = j.optBoolean("sh", false),
                            opacity = j.optDouble("op", 1.0).toFloat().coerceIn(0.05f, 1f)
                        )
                    )
                }

                "sticker" -> {
                    result.add(
                        StickerObject(
                            id = id,
                            zIndex = z,
                            layerId = layer,
                            locked = locked,
                            visible = visible,
                            groupId = group,
                            x = x,
                            y = y,
                            width = w,
                            height = h,
                            rotationDeg = rot,
                            builtInKind = BuiltInStickerKind.fromName(j.optString("kind")),
                            customAssetPath = if (j.has("asset")) j.optString("asset").takeIf { it.isNotBlank() } else null,
                            colorArgb = j.optLong("c", 0xFF16A34AL),
                            bgColorArgb = if (j.has("bg")) j.optLong("bg") else null,
                            opacity = j.optDouble("op", 1.0).toFloat().coerceIn(0.05f, 1f)
                        )
                    )
                }

                "table" -> {
                    val rows = j.optInt("rows", 3).coerceIn(1, 12)
                    val cols = j.optInt("cols", 3).coerceIn(1, 8)
                    val cellsArr = j.optJSONArray("cells")
                    val cells = MutableList(rows * cols) { idx ->
                        cellsArr?.optString(idx, "") ?: ""
                    }
                    result.add(
                        TableObject(
                            id = id,
                            zIndex = z,
                            layerId = layer,
                            locked = locked,
                            visible = visible,
                            groupId = group,
                            x = x,
                            y = y,
                            width = w,
                            height = h,
                            rotationDeg = rot,
                            rows = rows,
                            cols = cols,
                            cells = cells,
                            hasHeaderRow = j.optBoolean("hdr", true),
                            borderColorArgb = j.optLong("bdr", 0xFF334155L),
                            headerBgColorArgb = j.optLong("hbg", 0xFFEFF6FFL),
                            cellBgColorArgb = j.optLong("cbg", 0xFFFFFFFFL),
                            textColorArgb = j.optLong("tc", 0xFF151C2BL),
                            fontSizeNorm = j.optDouble("fs", 0.018).toFloat(),
                            opacity = j.optDouble("op", 1.0).toFloat().coerceIn(0.05f, 1f)
                        )
                    )
                }
            }
        }

        return NotebookPageContent(
            schemaVersion = NotebookPageContent.CURRENT_SCHEMA_VERSION,
            objects = result,
            layers = effectiveLayers
        )
    }

    /**
     * Lossless migration reader for legacy v1 pages (`{"v":1,"strokes":[...],"items":[...]}`).
     */
    private fun decodeLegacyV1(root: JSONObject): NotebookPageContent {
        val objects = mutableListOf<NotebookObject>()

        val strokesArr = root.optJSONArray("strokes")
        if (strokesArr != null) {
            for (i in 0 until strokesArr.length()) {
                val sObj = strokesArr.optJSONObject(i) ?: continue
                val ptsArr = sObj.optJSONArray("pts") ?: continue
                val pts = ArrayList<NbPoint>(ptsArr.length() / 2)
                var idx = 0
                while (idx + 1 < ptsArr.length()) {
                    pts.add(NbPoint(ptsArr.optDouble(idx, 0.0).toFloat(), ptsArr.optDouble(idx + 1, 0.0).toFloat()))
                    idx += 2
                }
                if (pts.isEmpty()) continue
                val isHl = sObj.optBoolean("hl", false)
                val isFl = sObj.optBoolean("fl", false)
                val bounds = StrokeObject.computeBounds(pts)
                objects.add(
                    StrokeObject(
                        id = sObj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        zIndex = sObj.optInt("z", if (isHl) 2 else 20),
                        layerId = if (isHl) NotebookLayerIds.UNDERLAY else NotebookLayerIds.INK,
                        x = bounds[0],
                        y = bounds[1],
                        width = bounds[2],
                        height = bounds[3],
                        tool = when {
                            isHl -> StrokeToolKind.HIGHLIGHTER
                            isFl -> StrokeToolKind.FINELINER
                            else -> StrokeToolKind.PEN
                        },
                        colorArgb = sObj.optLong("c", 0xFF151C2BL),
                        widthNorm = sObj.optDouble("w", 0.004).toFloat(),
                        opacity = sObj.optDouble("a", 1.0).toFloat().coerceIn(0.05f, 1f),
                        points = pts,
                        blendMode = if (isHl) StrokeBlendMode.MULTIPLY else StrokeBlendMode.NORMAL
                    )
                )
            }
        }

        val itemsArr = root.optJSONArray("items")
        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.optJSONObject(i) ?: continue
                val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
                val x = obj.optDouble("x", 0.1).toFloat()
                val y = obj.optDouble("y", 0.1).toFloat()
                val w = obj.optDouble("w", 0.3).toFloat()
                val h = obj.optDouble("h", 0.15).toFloat()
                val rot = obj.optDouble("rot", 0.0).toFloat()
                val z = obj.optInt("z", 10)

                when (obj.optString("type")) {
                    "text" -> {
                        val spansArr = obj.optJSONArray("spans")
                        val spans = mutableListOf<NotebookTextSpan>()
                        if (spansArr != null) {
                            for (j in 0 until spansArr.length()) {
                                val spObj = spansArr.optJSONObject(j) ?: continue
                                spans.add(
                                    NotebookTextSpan(
                                        text = spObj.optString("t", ""),
                                        colorArgb = spObj.optLong("c", 0xFF151C2BL),
                                        isBold = spObj.optBoolean("b", false),
                                        isItalic = spObj.optBoolean("i", false),
                                        highlightColorArgb = if (spObj.has("hl")) spObj.optLong("hl") else null
                                    )
                                )
                            }
                        }
                        objects.add(
                            TextObject(
                                id = id,
                                zIndex = z,
                                x = x,
                                y = y,
                                width = w,
                                height = h,
                                rotationDeg = rot,
                                spans = spans,
                                fontSizeNorm = obj.optDouble("fs", 0.022).toFloat(),
                                listMode = if (obj.optBoolean("bul", false)) TextListMode.BULLETS else TextListMode.NONE,
                                alignment = if (obj.optBoolean("ctr", false)) TextAlignMode.CENTER else TextAlignMode.START,
                                isBanner = obj.optBoolean("ban", false),
                                fillColorArgb = if (obj.has("fill")) obj.optLong("fill") else null,
                                borderColorArgb = if (obj.has("bdr")) obj.optLong("bdr") else null,
                                borderWidthDp = obj.optDouble("bw", 2.0).toFloat(),
                                cornerRadiusDp = obj.optDouble("cr", 14.0).toFloat(),
                                hasShadow = obj.optBoolean("sh", false)
                            )
                        )
                    }

                    "shape" -> {
                        objects.add(
                            ShapeObject(
                                id = id,
                                zIndex = z,
                                x = x,
                                y = y,
                                width = w,
                                height = h,
                                rotationDeg = rot,
                                kind = NotebookShapeKind.fromName(obj.optString("kind")),
                                startX = obj.optDouble("sx", x.toDouble()).toFloat(),
                                startY = obj.optDouble("sy", y.toDouble()).toFloat(),
                                endX = obj.optDouble("ex", (x + w).toDouble()).toFloat(),
                                endY = obj.optDouble("ey", (y + h).toDouble()).toFloat(),
                                midX = if (obj.has("mx")) obj.optDouble("mx").toFloat() else null,
                                midY = if (obj.has("my")) obj.optDouble("my").toFloat() else null,
                                strokeColorArgb = obj.optLong("c", 0xFF1D5BFFL),
                                fillColorArgb = if (obj.has("fill")) obj.optLong("fill") else null,
                                strokeWidthNorm = obj.optDouble("sw", 0.0045).toFloat(),
                                labelText = obj.optString("lbl", ""),
                                labelBgColorArgb = obj.optLong("lbg", 0xFFFFF8D6L)
                            )
                        )
                    }

                    "image" -> {
                        val asset = obj.optString("asset", "")
                        if (asset.isNotBlank()) {
                            objects.add(
                                ImageObject(
                                    id = id,
                                    zIndex = z,
                                    x = x,
                                    y = y,
                                    width = w,
                                    height = h,
                                    rotationDeg = rot,
                                    relativeAssetPath = asset,
                                    cornerRadiusDp = obj.optDouble("cr", 10.0).toFloat(),
                                    cropLeft = obj.optDouble("cl", 0.0).toFloat().coerceIn(0f, 0.9f),
                                    cropTop = obj.optDouble("ct", 0.0).toFloat().coerceIn(0f, 0.9f),
                                    cropRight = obj.optDouble("crg", 1.0).toFloat().coerceIn(0.1f, 1f),
                                    cropBottom = obj.optDouble("cb", 1.0).toFloat().coerceIn(0.1f, 1f),
                                    borderColorArgb = if (obj.has("bdr")) obj.optLong("bdr") else null
                                )
                            )
                        }
                    }
                }
            }
        }
        return NotebookPageContent(schemaVersion = NotebookPageContent.CURRENT_SCHEMA_VERSION, objects = objects)
    }

    private fun round4(v: Float): Double = kotlin.math.round(v * 10000.0) / 10000.0
    private fun round3(v: Float): Double = kotlin.math.round(v * 1000.0) / 1000.0
    private fun round2(v: Float): Double = kotlin.math.round(v * 100.0) / 100.0
}
