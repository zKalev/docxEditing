package com.example.docs.dto;

/** Document Server treats {"error":0} as success; anything else makes it report a save error to the user. */
public record CallbackResponse(int error) {

    public static CallbackResponse ok() {
        return new CallbackResponse(0);
    }

    public static CallbackResponse failed() {
        return new CallbackResponse(1);
    }
}
