package com.neo4flix.user.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(128);

    @Test
    void acceptsPasswordAtMinimumLengthWithEveryRequiredCharacterClass() {
        assertThatCode(() -> policy.validate("Abcdefg1!x")).doesNotThrowAnyException();
    }

    @Test
    void rejectsEveryMissingCharacterClassAndPathologicalWhitespace() {
        assertThatThrownBy(() -> policy.validate("Abcdefg1!"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate("abcdefghi1!"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate("ABCDEFGHI1!"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate("Abcdefghij!"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate("Abcdefghi12"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate("Abcdef1!  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPasswordBeyondConfiguredMaximum() {
        assertThatThrownBy(() -> policy.validate("A1!" + "a".repeat(126)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
