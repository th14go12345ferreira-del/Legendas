package br.com.thiago.legendaoffline

data class Cue(val start: Long, val end: Long, val text: String)

object Srt {
    fun parse(raw: String): List<Cue> {
        if (raw.startsWith("ERROR|")) error(raw.substringAfter('|'))
        return raw.lineSequence().mapNotNull {
            val p = it.split('|', limit = 3)
            if (p.size == 3) Cue(p[0].toLong(), p[1].toLong(), p[2].trim()) else null
        }.toList()
    }

    fun format(cues: List<Cue>): String = cues.mapIndexed { i, c ->
        "${i+1}\n${time(c.start)} --> ${time(c.end)}\n${c.text}\n"
    }.joinToString("\n")

    private fun time(ms: Long): String {
        val h=ms/3600000; val m=(ms%3600000)/60000; val s=(ms%60000)/1000; val x=ms%1000
        return "%02d:%02d:%02d,%03d".format(h,m,s,x)
    }
}
