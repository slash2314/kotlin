enum class MixedEnum {
    ENTRY1;
    companion object {
        val first = 1
    }
    // Syntax error because of the semicolon, another because of wrong order
    <!ENUM_ENTRY_USES_DEPRECATED_OR_NO_DELIMITER, ENUM_ENTRY_AFTER_ENUM_MEMBER!>ENTRY2<!><!SYNTAX!>;<!>
    fun foo(): String = "xyz"
    <!ENUM_ENTRY_AFTER_ENUM_MEMBER!>ENTRY3<!>
}