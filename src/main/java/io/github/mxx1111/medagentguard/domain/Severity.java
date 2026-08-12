package io.github.mxx1111.medagentguard.domain;

public enum Severity {
    LOW(5),
    MEDIUM(10),
    HIGH(25),
    CRITICAL(40);

    private final int penalty;

    Severity(int penalty) {
        this.penalty = penalty;
    }

    public int penalty() {
        return penalty;
    }
}
