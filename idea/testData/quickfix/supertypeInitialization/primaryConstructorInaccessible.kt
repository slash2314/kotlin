// "Add constructor parameters and use them" "true"
open class Base private(p1: Int, val p2: Int) {
    private constructor() : this(0, 1)
    protected constructor(s: String) : this(s.length(), 1)
}

class C : Base<caret>
