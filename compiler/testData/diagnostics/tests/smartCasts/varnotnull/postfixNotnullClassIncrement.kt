class MyClass

// In principle it is not correct, MyClass? is not a subtype of MyClass
fun MyClass.inc(): MyClass? { return null }

public fun box() : MyClass? {
    var i : MyClass? 
    i = MyClass()
    // type of j can be inferred as MyClass()
    var j = <!DEBUG_INFO_SMARTCAST!>i<!>++
    j.hashCode()
    return i
}
