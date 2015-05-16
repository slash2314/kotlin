fun foo() {
    class A
    fun bar() {}
    (fun <!FUNCTION_EXPRESSION_WITH_NAME!>bar<!>() {})
    fun A.foo() {}
    (fun A.<!FUNCTION_EXPRESSION_WITH_NAME!>foo<!>() {})
}
