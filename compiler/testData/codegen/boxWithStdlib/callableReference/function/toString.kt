fun foo(s: String) {}

class A {
    fun bar(): String = ""
}

fun A.baz(x: Int) {}


// TODO: toString() for reflected functions is temporarily useless, will be fixed when reflection on functions is available
fun box(): String {
    val f = "${::foo}"
    if (f != "kotlin.reflect.KFunction<kotlin.Unit>") return "Fail foo: $f"

    val nameOfA = (A() as java.lang.Object).getClass().getName()

    val g = "${A::bar}"
    if (g != "kotlin.reflect.KMemberFunction<$nameOfA, java.lang.String>") return "Fail bar: $g"

    val h = "${A::baz}"
    if (h != "kotlin.reflect.KExtensionFunction<$nameOfA, kotlin.Unit>") return "Fail baz: $h"

    return "OK"
}
