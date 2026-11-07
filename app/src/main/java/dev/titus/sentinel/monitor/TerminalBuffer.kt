package dev.titus.sentinel.monitor

/** Bounded plain-text display for TERM=dumb. Never executes OSC/clipboard/title actions. */
internal class TerminalBuffer(private val limit: Int=80_000) {
    private val text=StringBuilder()
    private val escape=StringBuilder()
    private var mode=0
    private var cursor=0
    private fun start()=text.lastIndexOf("\n",(cursor-1).coerceAtLeast(0))+1
    fun clear() { text.clear();cursor=0;escape.clear();mode=0 }
    fun append(value: String): String {
        for(c in value) {
            if(mode==3) { if(c=='\u0007') mode=0 else if(c=='\u001b') mode=4;continue }
            if(mode==4) { mode=if(c=='\\') 0 else 3;continue }
            if(mode==1) { mode=when(c) { '[' -> 2; ']', 'P', '_', '^' -> 3; else -> 0 };escape.clear();continue }
            if(mode==2) {
                if(c in '@'..'~') {
                    when(c) {
                        'K' -> { val end=text.indexOf("\n",cursor).let { if(it<0) text.length else it };text.delete(cursor,end) }
                        'J' -> if(escape.toString()=="2" || escape.toString()=="3") { text.clear();cursor=0 }
                        'D' -> cursor=(cursor-(escape.toString().toIntOrNull() ?: 1)).coerceAtLeast(start())
                        'C' -> cursor=(cursor+(escape.toString().toIntOrNull() ?: 1)).coerceAtMost(text.length)
                        'G' -> cursor=(start()+(escape.toString().toIntOrNull() ?: 1)-1).coerceIn(0,text.length)
                    }
                    mode=0;escape.clear()
                } else if(escape.length<64) escape.append(c) else { mode=0;escape.clear() }
                continue
            }
            when(c) {
                '\u001b' -> mode=1
                '\r' -> cursor=start()
                '\b','\u007f' -> cursor=(cursor-1).coerceAtLeast(start())
                '\n' -> { val end=text.indexOf("\n",cursor);if(end>=0) cursor=end+1 else { text.append('\n');cursor=text.length } }
                '\t' -> { repeat(4) { put(' ') } }
                else -> if(!c.isISOControl()) put(c)
            }
            if(text.length>limit) { val n=text.length-limit;text.delete(0,n);cursor=(cursor-n).coerceAtLeast(0) }
        }
        return text.toString()
    }
    private fun put(c: Char) {
        if(cursor<text.length && text[cursor]!='\n') text.setCharAt(cursor,c) else text.insert(cursor,c)
        cursor++
    }
}
