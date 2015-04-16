package test

class NestedClasses {
    fun f() {
    }

    private class Nested {
        fun f() {
        }

        public class NN {
            fun f() {
            }
        }

        inner class NI {
            fun f() {
            }
        }
    }

    public inner class Inner {
        fun f() {
        }

        private inner class II {
            fun f() {
            }
        }
    }
}

// TODO Remove this restriction when nested classes will be supported in js backend.
// TARGET_BACKEND: JVM
