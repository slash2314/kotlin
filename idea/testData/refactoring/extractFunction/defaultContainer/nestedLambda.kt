// PARAM_TYPES: kotlin.Int
// PARAM_TYPES: kotlin.Int
// PARAM_DESCRIPTOR: value-parameter val a: kotlin.Int defined in A.foo
// PARAM_DESCRIPTOR: value-parameter val b: kotlin.Int defined in A.foo
class A {
    fun foo(a: Int, b: Int): Int {
        return {
            { <selection>a + b - 1</selection> }.invoke()
        }.invoke()
    }
}