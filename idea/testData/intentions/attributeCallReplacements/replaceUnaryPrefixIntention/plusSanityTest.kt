// INTENTION_TEXT: Replace with '+' operator
fun test() {
    class Test {
        fun plus(): Test = Test()
    }
    val test = Test()
    test.pl<caret>us()
}
