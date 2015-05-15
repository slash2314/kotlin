package foo

class ExtBar : [extension] Function1<String, String> {
    override fun invoke(s: String) = "ExtBar.invoke($s)"
}

fun box(): String {
    val extBar: [extension] Function1<String, String> = ExtBar()

    assertEquals("ExtBar.invoke(2e2)", "2e2".extBar())

    return "OK"
}
