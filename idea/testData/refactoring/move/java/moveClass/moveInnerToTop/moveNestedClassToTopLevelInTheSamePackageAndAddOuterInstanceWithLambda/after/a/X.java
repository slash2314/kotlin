package a;

import kotlin.*;
import kotlin.jvm.functions.*;

public class X {
    private A outer;

    public X(A outer, Function0<String> f) {
        this.outer = outer;
        System.out.println(f.invoke());
    }
}
