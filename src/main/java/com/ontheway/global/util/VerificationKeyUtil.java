package com.ontheway.global.util;

import com.ontheway.enums.EmailPurpose;

public class VerificationKeyUtil {
    public static String of(EmailPurpose purpose, String email) {
        return purpose.name() + ":" + email;
    }
}
