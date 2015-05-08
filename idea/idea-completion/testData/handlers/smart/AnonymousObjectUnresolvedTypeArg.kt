import java.util.*

class C<T>(c: Comparator<T>)

fun f() {
    C<T>(<caret>)
}

// ELEMENT: object
