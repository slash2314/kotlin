<caret>@deprecated("")
fun foo(p: Int) {
    bar("\"$p\"\n1\r2\t3")
}

fun bar(s: String){}