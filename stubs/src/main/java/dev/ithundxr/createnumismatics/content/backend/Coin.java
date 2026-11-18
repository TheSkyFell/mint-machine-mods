package dev.ithundxr.createnumismatics.content.backend;

/** Stub of Create: Numismatics 1.1.0 (only values() and the value field are used). */
public enum Coin {
    ;

    // Set in the constructor so javac never treats it as a compile-time constant
    public final int value;

    Coin(int value) {
        this.value = value;
    }
}
