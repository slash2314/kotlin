package foo

class Bar : Function0<String> {
    override fun invoke() = "Bar.invoke()"
}

class Baz : Function2<Int, Boolean, String> {
    override fun invoke(i: Int, b: Boolean) = "Baz.invoke($i, $b)"
}

class ExtBar : [extension] Function1<String, String> {
    override fun invoke(s: String) = "ExtBar.invoke($s)"
}

class ExtBaz : [extension] Function3<String, Int, Boolean, String> {
    override fun invoke(s: String, i: Int, b: Boolean) = "ExtBaz.invoke($s, $i, $b)"
}

class Mixed :
        Function1<Int, String>,
        [extension] Function2<Int, Boolean, String>,
        [extension] Function3<Int, Int, Boolean, String>
{
    override fun invoke(i: Int) = "Mixed.invoke($i)"
    override fun invoke(i: Int, b: Boolean) = "Mixed.invoke($i, $b)"
    override fun invoke(s: Int, i: Int, b: Boolean) = "Mixed.invoke($s, $i, $b)"
}

fun box(): String {
    val bar = Bar()
    val baz = Baz()
    val extBar = ExtBar()
    val extBaz = ExtBaz()
    val mixed = Mixed()

    assertEquals("Bar.invoke()", bar())
    assertEquals("Baz.invoke(2, false)", baz(2, false))
    assertEquals("ExtBar.invoke(2e2)", "2e2".extBar())
    assertEquals("ExtBaz.invoke(29, 34, true)", "29".extBaz(34, true))

    assertEquals("Mixed.invoke(45)", mixed(45))
    assertEquals("Mixed.invoke(552, true)", mixed(552, true))
    assertEquals("Mixed.invoke(21, true)", 21.mixed(true))
    assertEquals("Mixed.invoke(29, 304, false)", 29.mixed(304, false))

    return "OK"
}
