package a;

import kotlin.*;
import kotlin.jvm.functions.*;

public class A {
    public class <caret>X {
        public X(Function0<String> f) {
            System.out.println(f.invoke());
        }
    }
}
