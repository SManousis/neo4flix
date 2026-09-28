package com.neo4flix.user.auth;

public final class PasswordPolicy {

    private static final int MINIMUM_LENGTH = 10;
    private final int maximumLength;

    public PasswordPolicy(int maximumLength) {
        if (maximumLength < MINIMUM_LENGTH || maximumLength > 1024) {
            throw new IllegalArgumentException("Password maximum must be between 10 and 1024");
        }
        this.maximumLength = maximumLength;
    }

    public void validate(String password) {
        if (password == null || password.length() < MINIMUM_LENGTH || password.length() > maximumLength) {
            throw invalid();
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (int offset = 0; offset < password.length();) {
            int codePoint = password.codePointAt(offset);
            if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) {
                throw invalid();
            }
            upper |= Character.isUpperCase(codePoint);
            lower |= Character.isLowerCase(codePoint);
            digit |= Character.isDigit(codePoint);
            special |= !Character.isLetterOrDigit(codePoint);
            offset += Character.charCount(codePoint);
        }
        if (!upper || !lower || !digit || !special) {
            throw invalid();
        }
    }

    private static PasswordPolicyViolationException invalid() {
        return new PasswordPolicyViolationException();
    }

    public static final class PasswordPolicyViolationException extends IllegalArgumentException {
        public PasswordPolicyViolationException() {
            super("Password does not satisfy the password policy");
        }
    }
}
